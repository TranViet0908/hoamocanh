// Modal dùng chung, thay cho alert()
(function () {
    const css = `
    .ma-modal-overlay{position:fixed;inset:0;background:rgba(30,25,25,.5);display:flex;align-items:center;justify-content:center;padding:20px;z-index:9999;opacity:0;transition:opacity .2s}
    .ma-modal-overlay.show{opacity:1}
    .ma-modal{background:#fff;border-radius:0;max-width:380px;width:100%;padding:28px 24px 22px;text-align:center;box-shadow:0 20px 50px rgba(0,0,0,.25);transform:translateY(12px);transition:transform .2s}
    .ma-modal-overlay.show .ma-modal{transform:none}
    .ma-modal-icon{width:44px;height:44px;border-radius:50%;margin:0 auto 14px;display:flex;align-items:center;justify-content:center;font-size:24px;color:#fff}
    .ma-modal-icon.success{background:#4f9d69}.ma-modal-icon.error{background:#c0453a}.ma-modal-icon.info{background:#8a6f5a}
    .ma-modal h4{margin:0 0 8px;font:500 24px 'Playfair Display',Georgia,serif}
    .ma-modal p{margin:0 0 20px;line-height:1.6;color:#555;white-space:pre-line}
    .ma-modal button{width:100%;padding:12px;border:0;border-radius:0;background:var(--ink,#29332f);color:#fff;font-size:13px;letter-spacing:.4px;cursor:pointer}`;
    const style = document.createElement('style');
    style.textContent = css;
    document.head.appendChild(style);

    const ICONS = { success: '✓', error: '!', info: 'i' };
    const TITLES = { success: 'Thành công', error: 'Có lỗi xảy ra', info: 'Thông báo' };

    // showModal(message, {type: 'info'|'success'|'error', title}) -> Promise (resolve khi đóng)
    window.showModal = function (message, opts = {}) {
        const type = opts.type || 'info';
        return new Promise(resolve => {
            const overlay = document.createElement('div');
            overlay.className = 'ma-modal-overlay';
            overlay.setAttribute('role', 'dialog');
            overlay.setAttribute('aria-modal', 'true');

            const box = document.createElement('div');
            box.className = 'ma-modal';
            box.innerHTML = '<div class="ma-modal-icon ' + type + '"></div><h4></h4><p></p><button type="button">Đồng ý</button>';
            box.querySelector('.ma-modal-icon').textContent = ICONS[type];
            box.querySelector('h4').textContent = opts.title || TITLES[type];
            box.querySelector('p').textContent = message;   // textContent: an toàn với dữ liệu từ server
            overlay.appendChild(box);
            document.body.appendChild(overlay);
            requestAnimationFrame(() => overlay.classList.add('show'));

            const btn = box.querySelector('button');
            btn.focus();
            const close = () => {
                document.removeEventListener('keydown', onKey);
                overlay.classList.remove('show');
                setTimeout(() => overlay.remove(), 200);
                resolve();
            };
            const onKey = e => { if (e.key === 'Escape' || e.key === 'Enter') close(); };
            document.addEventListener('keydown', onKey);
            btn.onclick = close;
            overlay.onclick = e => { if (e.target === overlay) close(); };
        });
    };
})();