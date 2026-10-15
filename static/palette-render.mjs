export const forms={round:'Bó tròn',natural:'Bó tự nhiên',long:'Bó dài'};
export const sizes={small:'Nhỏ',medium:'Vừa',large:'Lớn'};
export const paperColors={cream:'#e5d5b9',pink:'#cfa2ac'};
export const ribbonColors={pink:'#bb7f93',cream:'#f1e5ce'};
export function paper(design,catalog){return catalog.packaging.find(p=>p.id===design.packaging)||{color:paperColors[design.packaging]};}
export function ribbon(design,catalog){return catalog.ribbon_options?.find(p=>p.id===design.ribbon)||{color:ribbonColors[design.ribbon]};}
export function colorParts(design,catalog){
 const parts=design.materials.map(p=>({...catalog.materials.find(m=>m.id===p.id),quantity:p.quantity})).filter(m=>m.id&&m.quantity>0);
 const total=parts.reduce((s,m)=>s+m.quantity*(m.visual_weight||1),0);
 return parts.map(m=>({...m,share:m.quantity*(m.visual_weight||1)/(total||1)}));
}
export function drawPalette(canvas,design,catalog){
 const c=canvas.getContext('2d'),parts=colorParts(design,catalog);
 c.clearRect(0,0,900,900);c.fillStyle='#f7f3eb';c.fillRect(0,0,900,900);
 c.textAlign='center';c.fillStyle='#304f42';c.font='24px system-ui';c.fillText('MỘC ANH · BẢN PHỐI MÀU',450,62);
 c.font='17px system-ui';c.fillText(forms[design.form]+' · '+sizes[design.size],450,98);
 // Geometric color patches illustrate adjacency; they do not depict flower species.
 const wrap=paper(design,catalog);
 c.fillStyle=wrap.secondary_color||wrap.color||'#e5d5b9';
 c.beginPath();c.moveTo(135,235);c.lineTo(300,170);c.lineTo(755,235);c.lineTo(535,725);c.lineTo(370,725);c.closePath();c.fill();
 c.fillStyle=wrap.color||'#e5d5b9';
 c.beginPath();c.moveTo(160,260);c.lineTo(310,190);c.lineTo(730,260);c.lineTo(535,725);c.lineTo(370,725);c.closePath();c.fill();
 const rx=design.form==='long'?190:260,ry=design.form==='long'?270:210;
 c.save();c.translate(450,390);c.scale(rx,ry);
 c.beginPath();c.arc(0,0,1,0,Math.PI*2);c.clip();
 if(parts.length){
  const cells=[];
  for(let row=-7;row<=7;row++)for(let col=-7;col<=7;col++){
   const x=col*.225+(row%2)*.1125,y=row*.195;
   if(x*x+y*y<1.16)cells.push({x,y});
  }
  const assigned=[];let used=0;
  parts.forEach((m,i)=>{const count=i===parts.length-1?cells.length-used:Math.round(m.share*cells.length);for(let n=0;n<count;n++)assigned.push(m.color);used+=count;});
  // Fixed shuffle keeps the same spatial layout when changing paper or ribbon.
  let seed=173;for(let i=assigned.length-1;i>0;i--){seed=(seed*1664525+1013904223)>>>0;const j=seed%(i+1);[assigned[i],assigned[j]]=[assigned[j],assigned[i]];}
  cells.forEach(({x,y},i)=>{c.beginPath();for(let k=0;k<6;k++){const a=Math.PI/6+k*Math.PI/3,px=x+.135*Math.cos(a),py=y+.135*Math.sin(a);k?c.lineTo(px,py):c.moveTo(px,py);}c.closePath();c.fillStyle=assigned[i];c.fill();c.lineWidth=.009;c.strokeStyle='#ffffff55';c.stroke();});
 }
 if(!parts.length){c.fillStyle='#deded4';c.beginPath();c.arc(0,0,1,0,Math.PI*2);c.fill();}
 c.restore();
 if(design.ribbon!=='none'){c.fillStyle=ribbon(design,catalog).color;c.fillRect(368,670,170,22);}
 c.fillStyle='#5d4d43';c.font='18px system-ui';c.fillText('TỶ LỆ MÀU ƯỚC LƯỢNG · KHÔNG PHẢI ẢNH THÀNH PHẨM',450,795);
 c.font='15px system-ui';c.fillText('Màu hoa ở giữa · giấy bao ngoài · ruy băng phía dưới',450,832);
 return parts;
}
