// Thông tin tiệm (hotline, Zalo, tài khoản nhận chuyển khoản) lấy từ backend: GET /customer/shop-info
async function loadShopInfo() {
    try {
        const res = await fetch('/customer/shop-info');
        return res.ok ? await res.json() : {};
    } catch (_) { return {}; }
}

// Hiện nút Zalo / Gọi hotline vào phần tử `el`; số nào chưa cấu hình thì ẩn nút đó
function renderContact(el, shop) {
    const digits = s => String(s || '').replace(/[^\d+]/g, '');
    const parts = [];
    if (shop.zalo) parts.push(`<a class="button button-light" target="_blank" rel="noopener" href="https://zalo.me/${encodeURIComponent(digits(shop.zalo))}">Chat Zalo</a>`);
    if (shop.hotline) parts.push(`<a class="button button-light" href="tel:${encodeURIComponent(digits(shop.hotline))}">Gọi ${String(shop.hotline).replace(/[<>&"]/g, '')}</a>`);
    el.innerHTML = parts.join('');
    el.hidden = !parts.length;
}