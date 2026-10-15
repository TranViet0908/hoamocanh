const token=location.pathname.split('/').pop();
fetch('/api/v1/gifts/'+encodeURIComponent(token)).then(async r=>{if(!r.ok)throw new Error('Quà tặng đã hết hạn hoặc được thu hồi.');return r.json()}).then(g=>{
  document.getElementById('recipient').textContent='Gửi '+g.recipient;
  document.getElementById('message').textContent=g.message;
  document.getElementById('sender').textContent=g.sender?'Từ '+g.sender+' ♡':'';
  const image=document.getElementById('gift-image');image.src=g.image_url;image.hidden=false;
  for(const m of g.media){const el=document.createElement(m.mime.startsWith('video/')?'video':'img');el.src=m.url;if(el.tagName==='VIDEO'){el.controls=true;el.preload='metadata'}else el.alt='Ảnh từ người tặng';document.getElementById('media').append(el)}
}).catch(e=>document.getElementById('recipient').textContent=e.message);
