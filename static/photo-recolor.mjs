export function hexRGB(hex){return [1,3,5].map(i=>parseInt(hex.slice(i,i+2),16));}
const clamp=n=>Math.min(1,Math.max(0,n));
const smooth=(a,b,n)=>{const t=clamp((n-a)/(b-a));return t*t*(3-2*t);};
function hsv(r,g,b){const hi=Math.max(r,g,b),lo=Math.min(r,g,b),delta=hi-lo;let h=0;if(delta){h=hi===r?((g-b)/delta+6)%6:hi===g?(b-r)/delta+2:(r-g)/delta+4;h/=6;}return [h,hi?delta/hi:0,hi];}
export function buildMasks(pixels,width,height,regions){
 const masks={flower:new Float32Array(width*height),secondary:new Float32Array(width*height),paper:new Float32Array(width*height),ribbon:new Float32Array(width*height)};
 for(let i=0;i<width*height;i++){
  const x=(i%width)/width,y=Math.floor(i/width)/height,[r,g,b]=[pixels[i*4],pixels[i*4+1],pixels[i*4+2]].map(v=>v/255);
  const [h,s,v]=hsv(r,g,b);if(v<.08)continue;
  const pink=(h>.89||h<.055)?smooth(.035,.13,s):0;
  const warm=(h>=.025&&h<.18)?smooth(.035,.15,s):0;
  const f=regions.flower,dist=Math.sqrt(((x-f.cx)/f.rx)**2+((y-f.cy)/f.ry)**2),flowerRegion=1-smooth(.93,1.04,dist);
  masks.flower[i]=pink*flowerRegion;
  const second=regions.secondary;
  if(second){const dist2=Math.sqrt(((x-second.cx)/second.rx)**2+((y-second.cy)/second.ry)**2);masks.secondary[i]=(h>.66&&h<.88?smooth(.06,.22,s):0)*(1-smooth(.93,1.04,dist2));}
  const bow=regions.ribbon;
  const ribbonRegion=smooth(bow.top,bow.top+.02,y)*(1-smooth(bow.bottom-.02,bow.bottom,y))*smooth(bow.left,bow.left+.02,x)*(1-smooth(bow.right-.02,bow.right,x));
  masks.ribbon[i]=pink*ribbonRegion;
  const p=regions.paper;
  const paperRegion=smooth(p.top,p.top+.02,y)*(1-smooth(p.bottom-.02,p.bottom,y))*smooth(p.left,p.left+.02,x)*(1-smooth(p.right-.02,p.right,x));
  const paperBehindFlowers=smooth(.055,.085,h)*(1-smooth(.35,.5,s));
  masks.paper[i]=warm*paperRegion*(1-flowerRegion+flowerRegion*paperBehindFlowers)*(1-masks.flower[i])*(1-ribbonRegion*pink);
 }
 return masks;
}
export function recolor(original,masks,colors,bases){
 const result=new Uint8ClampedArray(original),stats={flower:0,paper:0,ribbon:0};
 for(const key of Object.keys(masks)){
  if(!colors[key]||!bases[key])continue;
  const target=hexRGB(colors[key]),base=hexRGB(bases[key]);
  if(colors[key].toLowerCase()===bases[key].toLowerCase())continue;
  const baseL=.2126*base[0]+.7152*base[1]+.0722*base[2];
  for(let i=0;i<masks[key].length;i++){
   const alpha=masks[key][i];if(alpha<.015)continue;stats[key]++;
   const idx=i*4,lum=.2126*original[idx]+.7152*original[idx+1]+.0722*original[idx+2],gain=key==='secondary'?Math.pow(lum/baseL,.55):lum/baseL;
   for(let k=0;k<3;k++){const tinted=Math.min(255,target[k]*gain);result[idx+k]=original[idx+k]*(1-alpha)+tinted*alpha;}
  }
 }
 return {pixels:result,stats};
}
