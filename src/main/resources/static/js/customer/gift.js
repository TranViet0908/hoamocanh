const PLACEHOLDER = 'data:image/svg+xml;utf8,' + encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96"><rect width="100%" height="100%" fill="#f3ece4"/>' +
    '<text x="50%" y="58%" font-size="36" text-anchor="middle">🌸</text></svg>');
const imgSrc = u => !u ? PLACEHOLDER : (/^(https?:|\/|data:)/.test(u) ? u : '/' + u);
const safeUpload = u => typeof u === 'string' && u.startsWith('/uploads/gift/') && !u.includes('..') ? u : null;

document.addEventListener('DOMContentLoaded', async () => {
    // Dùng getEl thay cho $ để tránh lỗi công cụ IDE chèn gạch chéo
    const getEl = s => document.querySelector(s);
    const code = new URLSearchParams(location.search).get('code');

    loadShopInfo().then(shop => renderContact(getEl('#contact'), shop));

    const fail = msg => {
        getEl('#loading').hidden = true;
        getEl('#error').hidden = false;
        getEl('#error').textContent = msg;
    };
    if (!code) return fail('Đường link quà tặng không hợp lệ.');

    async function fetchAndRenderGift() {
        try {
            const res = await fetch('/customer/gift?code=' + encodeURIComponent(code));
            let data = null; try { data = await res.json(); } catch (_) {}
            if (!res.ok) throw new Error(data?.message || 'Không mở được món quà.');

            getEl('#loading').hidden = true;

            // Đã mở khóa -> Hiện hoa
            getEl('#gift').hidden = false;

            getEl('#giftFrom').textContent = data.senderName ? 'Từ ' + data.senderName : '';
            const msg = getEl('#giftMessage');
            if (data.giftMessage) {
                msg.hidden = false;
                msg.textContent = data.giftMessage;
            } else {
                msg.hidden = true;
            }

            const media = getEl('#giftMedia');
            media.innerHTML = '';
            const img = safeUpload(data.giftImageUrl), vid = safeUpload(data.giftVideoUrl);
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

            // Ưu tiên ảnh bó hoa; chưa có thì hiện lưới ảnh nguyên liệu (chỉ ảnh, không chữ)
            const bq = getEl('#giftBouquet'), collage = getEl('#giftCollage');
            const bouquet = safeUpload(data.bouquetImageUrl);
            if (bouquet) {
                bq.src = bouquet; bq.hidden = false; collage.hidden = true;
            } else {
                bq.hidden = true; collage.hidden = false; collage.innerHTML = '';
                (data.items || []).forEach(i => {
                    const tile = document.createElement('div');
                    tile.className = 'mt-tile';
                    const im = document.createElement('img');
                    im.src = imgSrc(i.imageUrl); im.alt = i.name; im.title = i.name;
                    im.onerror = () => { im.onerror = null; im.src = PLACEHOLDER; };
                    tile.appendChild(im);
                    collage.appendChild(tile);
                });
            }

            getEl('#giftMeta').textContent = [data.productTypeName, data.style, data.themeColor].filter(Boolean).join(' · ');

        } catch (e) { fail(e.message); }
    }

    fetchAndRenderGift();
});