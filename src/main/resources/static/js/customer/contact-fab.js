/* Nút "Liên hệ" nổi ở góc màn hình: gọi hotline hoặc nhắn Zalo của tiệm.
   Lấy số từ /customer/shop-info (sửa trong trang quản trị > Cài đặt). Chưa nhập số nào thì không hiện gì. */
(function () {
    function digits(s) { return String(s || '').replace(/\D/g, ''); }

    fetch('/customer/shop-info').then(function (r) { return r.ok ? r.json() : null; }).then(function (info) {
        if (!info) return;
        var hotline = digits(info.hotline), zalo = digits(info.zalo);
        if (!hotline && !zalo) return;

        var wrap = document.createElement('div');
        wrap.setAttribute('style', 'position:fixed;right:16px;bottom:16px;z-index:50;display:flex;flex-direction:column;align-items:flex-end;gap:8px;font:500 14px "Be Vietnam Pro",system-ui,sans-serif');

        var menu = document.createElement('div');
        menu.hidden = true;
        menu.setAttribute('style', 'display:flex;flex-direction:column;gap:8px');
        menu.style.display = 'none';

        function link(text, href, external) {
            var a = document.createElement('a');
            a.textContent = text;
            a.href = href;
            if (external) { a.target = '_blank'; a.rel = 'noopener'; }
            a.setAttribute('style', 'display:block;padding:12px 18px;border-radius:99px;background:#fff;color:#2f4538;border:1px solid #d9d4c7;text-decoration:none;box-shadow:0 6px 18px rgba(0,0,0,.12)');
            return a;
        }
        if (hotline) menu.appendChild(link('Gọi hotline ' + (info.hotline || hotline), 'tel:' + hotline, false));
        if (zalo) menu.appendChild(link('Nhắn Zalo', 'https://zalo.me/' + zalo, true));

        var btn = document.createElement('button');
        btn.type = 'button';
        btn.textContent = 'Liên hệ tiệm';
        btn.setAttribute('aria-expanded', 'false');
        btn.setAttribute('style', 'padding:12px 20px;border-radius:99px;border:0;background:#2f4538;color:#fff;cursor:pointer;box-shadow:0 6px 18px rgba(0,0,0,.18);font:inherit');
        btn.addEventListener('click', function () {
            var open = menu.style.display === 'none';
            menu.style.display = open ? 'flex' : 'none';
            btn.setAttribute('aria-expanded', open ? 'true' : 'false');
        });

        wrap.appendChild(menu);
        wrap.appendChild(btn);
        document.body.appendChild(wrap);
    }).catch(function () { /* không có thông tin liên hệ thì thôi, không ảnh hưởng trang */ });
})();