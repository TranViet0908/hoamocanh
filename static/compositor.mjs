import {seededRandom} from './ai-core.mjs';
const imageCache=new Map();
export function clearPhotoCache(){imageCache.clear();}
async function photo(url){if(!imageCache.has(url)){const p=new Promise((resolve,reject)=>{const im=new Image();im.onload=()=>resolve(im);im.onerror=()=>reject(new Error('Thiếu ảnh nguyên liệu: '+url));im.src=url;});imageCache.set(url,p);p.catch(()=>imageCache.delete(url));}return imageCache.get(url);}
function cover(ctx,image,x,y,w,h){const r=Math.max(w/image.width,h/image.height),sw=w/r,sh=h/r;ctx.drawImage(image,(image.width-sw)/2,(image.height-sh)/2,sw,sh,x,y,w,h);}
function botanicalCutout(im,id){
 const out=document.createElement('canvas');out.width=out.height=512;const c=out.getContext('2d');
 // Pixel masks remove obvious backgrounds from SAMPLE photos. Shop PNG cutouts are preferable.
 const regions={rose:[.23,.045,.66,.89],tulip:[.17,0,.66,.42],daisy:[0,0,1,.8],baby:[0,0,1,1],eucalyptus:[0,0,1,1]};const r=regions[id]||[0,0,1,1];
 c.drawImage(im,im.width*r[0],im.height*r[1],im.width*r[2],im.height*r[3],0,0,512,512);
 const image=c.getImageData(0,0,512,512),d=image.data;for(let i=0;i<d.length;i+=4){const [red,green,blue]=[d[i],d[i+1],d[i+2]];let keep=true;
 if(id==='rose')keep=(red>green*1.1&&red>70&&blue>green*.95)||(red>185&&green>140&&blue>130&&red>=green*.95&&blue>=green*.9);
 else if(id==='tulip')keep=red>100&&green>75&&blue<red*.7;
 else if(id==='daisy'||id==='baby')keep=red>160&&green>160&&blue>145&&Math.max(red,green,blue)-Math.min(red,green,blue)<65;
 else if(id==='eucalyptus')keep=green>red*1.045&&green>blue*.98&&green<210;
 if(!keep)d[i+3]=0;
 }c.putImageData(image,0,0);return out;
}
function sampleContainer(im,id){
 const out=document.createElement('canvas');out.width=out.height=600;const c=out.getContext('2d');
 c.drawImage(im,0,0,600,600);
 if(id==='bag'){
  // Outline of the photographed white paper bag, not the old leather handbag.
  c.globalCompositeOperation='destination-in';c.beginPath();
  const points=[[.334,.095],[.757,.085],[.719,.129],[.77,.329],[.755,.865],[.513,.932],[.207,.77],[.201,.567]];
  points.forEach(([x,y],i)=>i?c.lineTo(x*600,y*600):c.moveTo(x*600,y*600));c.closePath();c.fill();
 }else if(id==='basket'||id==='stand'){
  const data=c.getImageData(0,0,600,600),d=data.data;
  for(let i=0;i<d.length;i+=4){const r=d[i],g=d[i+1],b=d[i+2],x=(i/4)%600/600,y=Math.floor(i/4/600)/600;
   let keep=r>65&&r>g*1.08&&g>b*1.09;
   if(id==='basket'&&y>.28&&y<.63&&x>.36&&x<.81)keep=false; // omit existing flowers
   if(id==='stand'&&y>.84)keep=false; // exclude the brown tabletop
   if(!keep)d[i+3]=0;
  }c.putImageData(data,0,0);
 }return out;
}
function paperTexture(im,id){
 const crop=id==='cream'?[.45,.685,.12,.035]:[.08,.4,.27,.24];
 const texture=document.createElement('canvas');texture.width=texture.height=400;
 texture.getContext('2d').drawImage(im,im.width*crop[0],im.height*crop[1],im.width*crop[2],im.height*crop[3],0,0,400,400);
 return texture;
}
function drawPackage(ctx,im,pack,l){
 if(pack.photo_is_sample&&['cream','pink'].includes(pack.id)){
  // Shape a photographed paper texture into a wrap. Do not use a gift-box photo
  // as the product reference: low-strength img2img faithfully preserves that box.
  const texture=paperTexture(im,pack.id);
  ctx.save();ctx.translate(450,l.py);ctx.beginPath();ctx.moveTo(-l.pw/2,10);ctx.lineTo(-l.pw*.17,-25);ctx.lineTo(l.pw/2,25);ctx.lineTo(l.pw*.13,l.ph);ctx.lineTo(-l.pw*.13,l.ph);ctx.closePath();ctx.clip();
  cover(ctx,texture,-l.pw/2,-25,l.pw,l.ph+25);ctx.restore();
 }else{
  const image=pack.photo_is_sample?sampleContainer(im,pack.id):im;
  // Transparent shop PNGs keep their aspect ratio; do not crop out handles/legs.
  const ratio=Math.min(l.pw/image.width,l.ph/image.height),w=image.width*ratio,h=image.height*ratio;
  ctx.drawImage(image,450-w/2,l.py,w,h);
 }
}
function label(ctx,text,x,y,size=21){ctx.font=`${size}px system-ui, sans-serif`;ctx.fillStyle='#5d4d43';ctx.fillText(text,x,y);}
export async function renderDesign(design,catalog,seed=17){
 const canvas=document.createElement('canvas');canvas.width=canvas.height=900;const ctx=canvas.getContext('2d');ctx.fillStyle='#f7f3eb';ctx.fillRect(0,0,900,900);
 const chosen=design.materials.map(p=>({...catalog.materials.find(m=>m.id===p.id),quantity:p.quantity}));
 const originalImages=await Promise.all(chosen.map(m=>photo(m.photo_url)));
 const images=originalImages.map((im,i)=>chosen[i].photo_is_sample?botanicalCutout(im,chosen[i].id):im);
 const pack=catalog.packaging.find(p=>p.id===design.packaging);const packageImage=await photo(pack.photo_url);
 const rng=seededRandom(seed);const layouts={mix:{cx:450,cy:345,rx:240,ry:170,py:390,pw:430,ph:350},bouquet:{cx:450,cy:340,rx:185,ry:135,py:355,pw:470,ph:420},basket:{cx:450,cy:340,rx:195,ry:95,py:400,pw:500,ph:350},box:{cx:450,cy:340,rx:175,ry:95,py:425,pw:450,ph:310},bag:{cx:450,cy:355,rx:130,ry:105,py:420,pw:360,ph:360},stand:{cx:450,cy:270,rx:170,ry:145,py:280,pw:480,ph:520}};const l=layouts[design.product];
 // Real photographs only. Layout is an illustrative collage, not a photograph of a finished product.
 drawPackage(ctx,packageImage,pack,l);
 const pieces=[];chosen.forEach((m,j)=>{for(let n=0;n<Math.min(m.quantity,12);n++)pieces.push({m,j});});
 pieces.sort((a,b)=>(a.m.kind==='leaf'?-1:1)-(b.m.kind==='leaf'?-1:1));
 for(let i=0;i<pieces.length;i++){const {m,j}=pieces[i],angle=i*2.39996+(design.style==='natural'?rng()*.25:0),radius=Math.sqrt((i+.5)/pieces.length),x=l.cx+Math.cos(angle)*l.rx*radius,y=l.cy+Math.sin(angle)*l.ry*radius;const size=m.id==='baby'?112:m.kind==='leaf'?180:190;
  ctx.save();ctx.translate(x,y);ctx.rotate((rng()-.5)*.45);ctx.beginPath();ctx.ellipse(0,0,size/2,size/2,0,0,Math.PI*2);ctx.clip();cover(ctx,images[j],-size/2,-size/2,size,size);ctx.restore();
 }
 const reference=document.createElement('canvas');reference.width=reference.height=900;reference.getContext('2d').drawImage(canvas,0,0);
 label(ctx,'Ruy băng: '+({pink:'hồng',cream:'kem',none:'không dùng'}[design.ribbon]),35,810,17);
 label(ctx,catalog.products.find(p=>p.id===design.product).name,35,55,25);label(ctx,'ẢNH GHÉP TỪ ẢNH CHỤP · BỐ CỤC THAM KHẢO',35,850,16);
 const mood=document.createElement('canvas');mood.width=mood.height=900;const c=mood.getContext('2d');c.fillStyle='#f7f3eb';c.fillRect(0,0,900,900);label(c,'Bảng phối nguyên liệu',35,55,30);
 const packagePreview=pack.photo_is_sample&&['cream','pink'].includes(pack.id)?paperTexture(packageImage,pack.id):packageImage;
 const cards=[...chosen.map((m,j)=>({name:m.name,quantity:m.quantity,image:originalImages[j]})),{name:pack.name,quantity:null,image:packagePreview}];
 const columns=cards.length>4?3:2,w=(830-(columns-1)*18)/columns,h=cards.length>4?220:290;
 cards.forEach((item,i)=>{const x=35+(i%columns)*(w+18),y=95+Math.floor(i/columns)*(h+54);cover(c,item.image,x,y,w,h);label(c,item.name+(item.quantity?' × '+item.quantity:''),x,y+h+30,19);});
 label(c,'Tone: '+({'pink-white':'Hồng & trắng','yellow-white':'Vàng & trắng',pastel:'Pastel',mixed:'Màu nguyên liệu'}[design.tone]),35,855,20);
 return {collage:canvas,moodboard:mood,reference};
}
export function asBlob(canvas){return new Promise((resolve,reject)=>canvas.toBlob(b=>b?resolve(b):reject(new Error('Không xuất được ảnh.')),'image/png'));}
