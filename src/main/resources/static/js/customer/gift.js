const PLACEHOLDER = 'data:image/svg+xml;utf8,' + encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96"><rect width="100%" height="100%" fill="#f3ece4"/>' +
    '<text x="50%" y="58%" font-size="36" text-anchor="middle">🌸</text></svg>');
// DB có thể lưu "images/a.jpg", "/images/a.jpg" hoặc URL đầy đủ
const imgSrc = u => !u ? PLACEHOLDER : (/^(https?:|\/|data:)/.test(u) ? u : '/' + u);
// Chỉ nhận file do hệ thống upload (server cũng đã kiểm tra)
const safeUpload = u => typeof u === 'string' && u.startsWith('/uploads/gift/') && !u.includes('..') ? u : null;

document.addEventListener('DOMContentLoaded', async () => {
    const $ = s => document.querySelector(s);
    const code = new URLSearchParams(location.search).get('code');

    loadShopInfo().then(shop => renderContact($('#contact'), shop));

    const fail = msg => {
        $('#loading').hidden = true;
        $('#error').hidden = false;
        $('#error').textContent = msg;
    };
    if (!code) return fail('Đường link quà tặng không hợp lệ.');

    let g;
    try {
        const res = await fetch('/customer/gift?code=' + encodeURIComponent(code));
        let data = null; try { data = await res.json(); } catch (_) {}
        if (!res.ok) throw new Error(data?.message || 'Không mở được món quà.');
        g = data;
    } catch (e) { return fail(e.message); }

    $('#loading').hidden = true;
    $('#gift').hidden = false;

    // Nội dung do khách nhập -> chỉ dùng textContent
    $('#giftFrom').textContent = g.senderName ? 'Từ ' + g.senderName : '';
    const msg = $('#giftMessage');
    if (g.giftMessage) msg.textContent = g.giftMessage; else msg.hidden = true;

    const media = $('#giftMedia');
    const img = safeUpload(g.giftImageUrl), vid = safeUpload(g.giftVideoUrl);
    if (img) {
        const el = document.createElement('img');
        el.src = img; el.alt = 'Ảnh kèm theo lời chúc';
        media.appendChild(el);
    }
    if (vid) {
        const el = document.createElement('video');
        el.src = vid; el.controls = true; el.playsInline = true; el.preload = 'metadata';
        media.appendChild(el);
    }

    const collage = $('#giftCollage');
    (g.items || []).forEach(i => {
        const tile = document.createElement('div');
        tile.className = 'mt-tile';
        const im = document.createElement('img');
        im.src = imgSrc(i.imageUrl); im.alt = i.name;
        im.onerror = () => { im.onerror = null; im.src = PLACEHOLDER; };
        const qty = document.createElement('span');
        qty.textContent = `${i.name} × ${i.quantity}`;
        tile.append(im, qty);
        collage.appendChild(tile);
    });

    $('#giftMeta').textContent = [g.productTypeName, g.style, g.themeColor].filter(Boolean).join(' · ');
});