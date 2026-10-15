// Use the posterior mean of this pinned VAE export rather than its hidden random
// sample. Change only GraphProto.output; weights and intermediate nodes stay intact.
// The runtime eliminates the now-unused random branch during graph optimization.
const decoder=new TextDecoder(),encoder=new TextEncoder();
function fields(bytes){
 const result=[];let i=0;
 const read=()=>{let value=0,factor=1;for(let n=0;n<8;n++){if(i>=bytes.length)throw new Error('VAE_PROTO_INVALID');const b=bytes[i++];value+=(b&127)*factor;if(!(b&128))return value;factor*=128;}throw new Error('VAE_PROTO_INVALID');};
 while(i<bytes.length){const start=i,key=read(),number=Math.floor(key/8),wire=key%8;let valueStart=i,valueEnd;
  if(!number)throw new Error('VAE_PROTO_INVALID');
  if(wire===2){const length=read();valueStart=i;i+=length;}else if(wire===0)read();else if(wire===1)i+=8;else if(wire===5)i+=4;else throw new Error('VAE_PROTO_INVALID');
  if(i>bytes.length)throw new Error('VAE_PROTO_INVALID');valueEnd=i;result.push({number,wire,value:bytes.subarray(valueStart,valueEnd),raw:bytes.subarray(start,i)});
 }return result;
}
function varint(n){const out=[];do{const b=n%128;n=Math.floor(n/128);out.push(b+(n?128:0));}while(n);return Uint8Array.from(out);}
function join(parts){const out=new Uint8Array(parts.reduce((n,p)=>n+p.length,0));let i=0;for(const part of parts){out.set(part,i);i+=part.length;}return out;}
function message(number,bytes){return join([varint(number*8+2),varint(bytes.length),bytes]);}
const strings=(fs,n)=>fs.filter(f=>f.number===n&&f.wire===2).map(f=>decoder.decode(f.value));
export function vaePosteriorMean(buffer){
 const bytes=buffer instanceof Uint8Array?buffer:new Uint8Array(buffer),model=fields(bytes);
 const graphs=model.filter(f=>f.number===7&&f.wire===2);if(graphs.length!==1)throw new Error('VAE_GRAPH_UNEXPECTED');
 const graph=graphs[0],gf=fields(graph.value),outputs=gf.filter(f=>f.number===12&&f.wire===2);
 const nodes=gf.filter(f=>f.number===1&&f.wire===2).map(f=>{const nf=fields(f.value);return{op:strings(nf,4)[0],inputs:strings(nf,1),outputs:strings(nf,2)};});
 if(outputs.length!==1||strings(fields(outputs[0].value),1)[0]!=='latent_sample')throw new Error('VAE_GRAPH_UNEXPECTED');
 const random=nodes.filter(n=>n.op==='RandomNormalLike');
 const final=nodes.find(n=>n.op==='Add'&&n.outputs.includes('latent_sample'));
 const mean=nodes.find(n=>n.op==='Slice'&&n.outputs.includes('/Slice_output_0'));
 const cast=nodes.find(n=>n.op==='Cast'&&n.inputs.includes(random[0]?.outputs[0]));
 const mul=nodes.find(n=>n.op==='Mul'&&n.inputs.includes(cast?.outputs[0]));
 if(random.length!==1||!mean||!final?.inputs.includes(mean.outputs[0])||!final.inputs.includes(mul?.outputs[0]))throw new Error('VAE_GRAPH_UNEXPECTED');
 const output=outputs[0],of=fields(output.value);
 const updatedOutput=message(12,join(of.map(f=>f.number===1?message(1,encoder.encode(mean.outputs[0])):f.raw)));
 const updatedGraph=message(7,join(gf.map(f=>f===output?updatedOutput:f.raw)));
 return join(model.map(f=>f===graph?updatedGraph:f.raw));
}
