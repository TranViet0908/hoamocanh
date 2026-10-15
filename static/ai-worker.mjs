import * as ort from './vendor/ort.webgpu.min.mjs';
import {ClipTokenizer,normalNoise,trainingSigmas,schedule,eulerStep,floatToHalf,halfToFloat} from './ai-core.mjs';
import {vaePosteriorMean} from './vae-mean.mjs';
ort.env.wasm.wasmPaths=new URL('./vendor/',import.meta.url).href;
ort.env.wasm.numThreads=1;
const sessions={};let tokenizer,revision='',networkBytes=0;
const emit=(type,data={})=>postMessage({type,...data});
const CACHE='moc-anh-models-v2';
function floatTensor(data,dims,session,name){const m=session.inputMetadata?.find?.(x=>x.name===name);const type=m?.type==='float16'?'float16':'float32';return type==='float16'?new ort.Tensor(type,Uint16Array.from(data,floatToHalf),dims):new ort.Tensor('float32',data,dims);}
async function cpuData(t){const data=await t.getData();return t.type==='float16'?Float32Array.from(data,halfToFloat):data;}
async function download(file,previous,total){
 let cache=null,cached=null;try{cache=await caches.open(CACHE);cached=await cache.match(file.url);}catch{}
 if(cached){emit('progress',{phase:'cache',name:file.name,loaded:previous+file.bytes,total});return cached.arrayBuffer();}
 const response=await fetch(file.url,{mode:'cors'});if(!response.ok)throw new Error('MODEL_DOWNLOAD_HTTP_'+response.status);
 let loaded=0;const reader=response.body.getReader();
 const stream=new ReadableStream({async pull(controller){try{const {done,value}=await reader.read();if(done){if(loaded!==file.bytes){controller.error(new Error('MODEL_SIZE_MISMATCH'));return;}controller.close();return;}loaded+=value.byteLength;networkBytes+=value.byteLength;emit('progress',{phase:'download',name:file.name,loaded:previous+loaded,total,downloaded_bytes:networkBytes});controller.enqueue(value);}catch(e){controller.error(e);}},cancel(reason){return reader.cancel(reason);}});
 if(cache){try{await cache.put(file.url,new Response(stream,{headers:{'Content-Type':'application/octet-stream'}}));cached=await cache.match(file.url);if(cached)return cached.arrayBuffer();}catch(e){emit('notice',{message:'Không lưu được cache; đang tải model vào bộ nhớ. Có thể phải tải lại lần sau.'});const r=await fetch(file.url);if(!r.ok)throw new Error('MODEL_DOWNLOAD_HTTP_'+r.status);return r.arrayBuffer();}}
 return new Response(stream).arrayBuffer();
}
async function load(manifest){
 const adapter=await navigator.gpu?.requestAdapter({powerPreference:'high-performance'});if(!adapter)throw new Error('WEBGPU_ADAPTER_UNAVAILABLE');if(!adapter.features.has('shader-f16'))throw new Error('WEBGPU_FP16_UNAVAILABLE');
 revision=manifest.revision;networkBytes=0;let loaded=0;
 for(const file of manifest.files){emit('progress',{phase:'load',name:file.name,loaded,total:manifest.total_bytes});let bytes=await download(file,loaded,manifest.total_bytes);if(bytes.byteLength!==file.bytes)throw new Error('MODEL_SIZE_MISMATCH');
  const dims=file.name==='unet'?{batch_size:1,num_channels:4,height:64,width:64,sequence_length:77}:file.name==='text_encoder'?{batch_size:1}:file.name==='vae_decoder'?{batch_size:1,num_channels_latent:4,height_latent:64,width_latent:64}:{batch_size:1,num_channels:3,height:512,width:512};
  if(file.name==='vae_encoder')bytes=vaePosteriorMean(bytes);
  sessions[file.name]=await ort.InferenceSession.create(bytes,{executionProviders:['webgpu'],freeDimensionOverrides:dims,graphOptimizationLevel:'all',enableMemPattern:false});bytes=null;loaded+=file.bytes;
 }
 const [vocab,merges]=await Promise.all([fetch('./vendor/tokenizer-vocab.json').then(r=>r.json()),fetch('./vendor/tokenizer-merges.txt').then(r=>r.text())]);tokenizer=new ClipTokenizer(vocab,merges);emit('ready',{revision});
}
async function generate({prompt,pixels,seed,strength}){
 if(!tokenizer)throw new Error('MODEL_NOT_READY');const start=performance.now();const tokens=tokenizer.encode(prompt);emit('generation-progress',{phase:'text',truncated:tokens.truncated,tokenCount:tokens.tokenCount});
 const tensor=new ort.Tensor('int32',tokens.ids,[1,77]);const text=sessions.text_encoder;const enc=await text.run({[text.inputNames[0]]:tensor});const hidden=enc[text.outputNames[0]];for(const name of text.outputNames.slice(1))enc[name]?.dispose();tensor.dispose();
 const input=new Float32Array(3*512*512);for(let i=0;i<512*512;i++)for(let c=0;c<3;c++)input[c*512*512+i]=pixels[i*4+c]/127.5-1;
 const encoder=sessions.vae_encoder,vaeIn=floatTensor(input,[1,3,512,512],encoder,encoder.inputNames[0]);emit('generation-progress',{phase:'encode'});
 const encoded=await encoder.run({[encoder.inputNames[0]]:vaeIn});vaeIn.dispose();const latentTensor=encoded[encoder.outputNames[0]];const raw=await cpuData(latentTensor);latentTensor.dispose();
 if(raw.length!==4*64*64)throw new Error('VAE_ENCODER_SHAPE_MISMATCH');const noise=normalNoise(raw.length,seed),sigmas=trainingSigmas(),timesteps=schedule(strength,4);let latents=Float32Array.from(raw,(x,i)=>x*.18215+noise[i]*sigmas[timesteps[0]]);
 try{
  for(let k=0;k<timesteps.length;k++){
   const timestep=timesteps[k],sigma=sigmas[timestep],next=k+1<timesteps.length?sigmas[timesteps[k+1]]:0;
   const normalized=Float32Array.from(latents,x=>x/Math.sqrt(sigma*sigma+1));const unet=sessions.unet;
   const sample=floatTensor(normalized,[1,4,64,64],unet,'sample'),time=new ort.Tensor('int64',BigInt64Array.from([BigInt(timestep)]),[1]);
   emit('generation-progress',{phase:'denoise',step:k+1,steps:timesteps.length});const output=await unet.run({sample,timestep:time,encoder_hidden_states:hidden});sample.dispose();time.dispose();const epsTensor=output[unet.outputNames[0]],epsilon=await cpuData(epsTensor);latents=eulerStep(latents,epsilon,sigma,next);epsTensor.dispose();
  }
 }finally{hidden.dispose();}
 const decoder=sessions.vae_decoder,decoderIn=floatTensor(Float32Array.from(latents,x=>x/.18215),[1,4,64,64],decoder,decoder.inputNames[0]);emit('generation-progress',{phase:'decode'});
 const decoded=await decoder.run({[decoder.inputNames[0]]:decoderIn});decoderIn.dispose();const outputTensor=decoded[decoder.outputNames[0]],rgb=await cpuData(outputTensor);outputTensor.dispose();
 if(rgb.length!==3*512*512)throw new Error('VAE_DECODER_SHAPE_MISMATCH');const rgba=new Uint8ClampedArray(512*512*4);for(let i=0;i<512*512;i++){for(let c=0;c<3;c++)rgba[4*i+c]=Math.round(Math.max(0,Math.min(1,rgb[c*512*512+i]/2+.5))*255);rgba[4*i+3]=255;}
 postMessage({type:'image',pixels:rgba.buffer,width:512,height:512,seed,strength,elapsed_ms:performance.now()-start,truncated:tokens.truncated,revision,pipeline_version:'vae-mean-v1'},[rgba.buffer]);
}
self.onmessage=async({data})=>{try{if(data.type==='load')await load(data.manifest);else if(data.type==='generate')await generate(data);else if(data.type==='clear-cache'){await caches.delete(CACHE);emit('cache-cleared');}}catch(e){emit('error',{code:e.message?.match(/^[A-Z0-9_]+$/)?e.message:'INFERENCE_FAILED',message:e.message?.slice(0,400)||'Không chạy được AI.'});}};
