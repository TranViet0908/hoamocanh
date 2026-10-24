/* Mộc Anh — lõi dùng chung cho mọi trang quản trị: gọi API, tiện ích, khung giao diện, kiểm tra đăng nhập. */
(function () {
    const NAV = [
        { href: 'dashboard.html', label: 'Tổng quan', icon: '◧' },
        { href: 'inventory.html', label: 'Kho hoa', icon: '❀' },
        { href: 'orders.html', label: 'Đơn hàng', icon: '☰' },
        { href: 'calendar.html', label: 'Lịch', icon: '▦' },
        { href: 'pricing.html', label: 'Mức giá', icon: '₫', role: 'ADMIN' },
        { href: 'staff.html', label: 'Nhân viên', icon: '☺', role: 'ADMIN' },
        { href: 'products.html', label: 'Sản phẩm mẫu', icon: '❁' },
        { href: 'curation.html', label: 'Mẫu & Flash sale', icon: '★' },
        { href: 'settings.html', label: 'Cài đặt', icon: '⚙' }
        // Thêm trang mới: { href: 'pricing.html', label: 'Mức giá', icon: '₫', role: 'ADMIN' }
    ];

    const esc = s => String(s ?? '').replace(/[&<>"']/g,
        c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
    const money = n => Number(n || 0).toLocaleString('vi-VN') + 'đ';

    // Gọi API /admin/... — trả JSON; lỗi thì ném Error(message tiếng Việt từ server)
    async function api(path, { method = 'GET', body } = {}) {
        const isForm = body instanceof FormData;
        const res = await fetch('/admin' + path, {
            method,
            credentials: 'same-origin',
            headers: body === undefined || isForm ? {} : { 'Content-Type': 'application/json' },
            body: body === undefined ? undefined : (isForm ? body : JSON.stringify(body))
        });
        if (res.status === 401 && !location.pathname.endsWith('login.html')) {
            location.href = '/admin/login.html';
            throw new Error('Phiên đăng nhập đã hết hạn.');
        }
        let data = null;
        try { data = await res.json(); } catch (_) { /* phản hồi rỗng */ }
        if (!res.ok) throw new Error(data?.message || `Lỗi máy chủ (${res.status})`);
        return data;
    }

    let toastTimer;
    function toast(msg, type = '') {
        let el = document.querySelector('.adm-toast');
        if (!el) { el = document.createElement('div'); el.className = 'adm-toast'; el.setAttribute('role', 'status'); document.body.appendChild(el); }
        el.textContent = msg;
        el.className = 'adm-toast ' + type;
        requestAnimationFrame(() => el.classList.add('show'));
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => el.classList.remove('show'), 3200);
    }

    async function logout() {
        try { await api('/auth/logout', { method: 'POST' }); } catch (_) { }
        location.href = '/admin/login.html';
    }

    // Dựng khung trang. Trả { page, user }: page là vùng nội dung để trang tự đổ dữ liệu vào.
    async function mount({ active, title, actions = '' }) {
        let user;
        try { user = await api('/auth/me'); }
        catch (e) {   // 401 đã tự chuyển về trang đăng nhập; các lỗi khác (máy chủ tắt, 500...) thì báo rõ thay vì để trang trắng
            document.body.innerHTML = '<p style="padding:40px;font:15px sans-serif">' + esc(e.message) + ' <a href="/admin/login.html">Đăng nhập lại</a></p>';
            throw e;
        }
        const items = NAV.filter(n => !n.role || n.role === user.role);
        const given = (user.fullName || '').trim().split(/\s+/).pop() || '';   // tên gọi = chữ cuối

        document.body.classList.add('adm');
        document.body.innerHTML = `
        <div class="adm-shell">
          <aside class="adm-side">
            <a class="adm-brand" href="dashboard.html"><span class="adm-mark">MA</span><span><b>MỘC ANH</b><small>flower studio</small></span></a>
            <nav class="adm-nav">${items.map(n =>
                `<a href="${n.href}" class="${n.href === active ? 'on' : ''}"><i>${n.icon}</i>${esc(n.label)}</a>`).join('')}</nav>
            <div class="adm-user"><b>${esc(user.fullName)}</b>${user.role === 'ADMIN' ? 'Quản trị viên' : 'Nhân viên'}
              <br><button type="button" id="admLogout">Đăng xuất</button></div>
          </aside>
          <main class="adm-main">
            <div class="adm-top">
              <div><p class="adm-eyebrow">Xin chào, ${esc(given)}</p><h1>${esc(title)}</h1></div>
              <div class="adm-top-actions">${actions}<button type="button" class="adm-btn adm-logout-m" id="admLogoutM">Thoát</button></div>
            </div>
            <div id="page"></div>
          </main>
          <nav class="adm-tabbar">${items.map(n =>
                `<a href="${n.href}" class="${n.href === active ? 'on' : ''}"><i>${n.icon}</i>${esc(n.label)}</a>`).join('')}</nav>
        </div>`;
        document.getElementById('admLogout').onclick = logout;
        document.getElementById('admLogoutM').onclick = logout;
        document.title = title + ' — Mộc Anh';
        return { page: document.getElementById('page'), user };
    }

    // Thanh phân trang dùng chung cho các danh sách trong trang quản trị
    function pageNav(el, total, page, per, cb) {
        if (!document.getElementById('admPgCss')) {
            const st = document.createElement('style'); st.id = 'admPgCss';
            st.textContent = '.adm-pg{display:flex;gap:6px;justify-content:center;flex-wrap:wrap;margin:16px 0}.adm-pg button{min-width:38px;height:38px;border:1px solid var(--line,#e5e0d3);background:#fff;border-radius:10px;cursor:pointer;font:inherit}.adm-pg button.on{background:var(--ink,#2f4538);color:#fff;border-color:var(--ink,#2f4538)}.adm-pg button[disabled]{opacity:.4;cursor:default}';
            document.head.appendChild(st);
        }
        const n = Math.max(1, Math.ceil(total / per)); page = Math.min(Math.max(1, page), n);
        if (n < 2) { el.innerHTML = ''; el.className = ''; return; }
        const set = new Set([1, n, page - 1, page, page + 1].filter(x => x >= 1 && x <= n));
        let h = '', last = 0;
        [...set].sort((a, b) => a - b).forEach(x => {
            if (x - last > 1) h += '<span aria-hidden="true" style="align-self:center">…</span>';
            h += `<button type="button" data-p="${x}"${x === page ? ' class="on" aria-current="page"' : ''} aria-label="Trang ${x}">${x}</button>`;
            last = x;
        });
        el.className = 'adm-pg'; el.setAttribute('role', 'navigation'); el.setAttribute('aria-label', 'Phân trang');
        el.innerHTML = `<button type="button" data-p="${page - 1}"${page === 1 ? ' disabled' : ''} aria-label="Trang trước">‹</button>${h}<button type="button" data-p="${page + 1}"${page === n ? ' disabled' : ''} aria-label="Trang sau">›</button>`;
        el.onclick = e => { const b = e.target.closest('button[data-p]'); if (b && !b.disabled) cb(+b.dataset.p); };
    }

    window.Admin = { api, esc, money, toast, mount, logout, pageNav };
})();