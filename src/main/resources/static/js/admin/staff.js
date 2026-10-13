/* Trang Nhân viên (chỉ ADMIN): thêm, sửa, khóa/mở khóa, đặt lại mật khẩu. */
(async function () {
    const { api, esc, toast } = Admin;
    const ROLE = { ADMIN: 'Quản trị viên', STAFF: 'Nhân viên' };
    const fmtDate = s => { const d = s ? new Date(String(s).replace(' ', 'T')) : null; return d && !isNaN(d) ? d.toLocaleDateString('vi-VN') : '—'; };

    const { page, user } = await Admin.mount({ active: 'staff.html', title: 'Nhân viên',
        actions: '<button type="button" class="adm-btn primary" id="addBtn">+ Thêm nhân viên</button>' });
    if (user.role !== 'ADMIN') {
        document.getElementById('addBtn')?.remove();
        page.innerHTML = '<div class="adm-card adm-muted">Chỉ quản trị viên được xem trang này.</div>';
        return;
    }

    let list = [], mode = 'create', cur = null;
    page.innerHTML = `
      <div class="st-hint">Khóa tài khoản hoặc đổi vai trò có hiệu lực ngay: người đó sẽ bị đăng xuất ở thao tác kế tiếp. Luôn phải còn ít nhất một quản trị viên đang hoạt động.</div>
      <div class="adm-card"><table class="adm-table"><thead><tr><th>Họ tên</th><th>Số điện thoại</th><th>Vai trò</th><th>Trạng thái</th><th>Tạo ngày</th><th></th></tr></thead><tbody id="rows"></tbody></table></div>
      <div class="adm-overlay" id="ov"></div>
      <aside class="adm-drawer" id="dw" aria-hidden="true">
        <div class="adm-drawer-head"><h2 id="dwTitle"></h2><button type="button" class="adm-btn" id="dwClose">Đóng</button></div>
        <form id="sf" class="adm-drawer-body" novalidate>
          <p id="who" class="adm-muted" hidden></p>
          <label class="adm-field" data-for="create edit">Họ tên<input class="adm-input" name="fullName" maxlength="100"></label>
          <label class="adm-field" data-for="create edit">Số điện thoại (dùng để đăng nhập)<input class="adm-input" name="phone" type="tel" maxlength="11"></label>
          <label class="adm-field" data-for="create edit">Email (không bắt buộc)<input class="adm-input" name="email" type="email" maxlength="100"></label>
          <label class="adm-field" data-for="create edit">Vai trò<select class="adm-input" name="role"><option value="STAFF">Nhân viên</option><option value="ADMIN">Quản trị viên</option></select></label>
          <label class="adm-field" data-for="create reset"><span id="pwLabel">Mật khẩu</span>
            <span style="display:flex;gap:8px"><input class="adm-input" style="flex:1" name="password" type="password" autocomplete="new-password" maxlength="72" placeholder="Từ 8 ký tự, có cả chữ và số">
              <button type="button" class="adm-btn" id="pwToggle" style="flex:none;min-width:64px" aria-label="Hiện hoặc ẩn mật khẩu">Hiện</button></span></label>
          <p class="adm-error" id="sfErr" role="alert"></p>
        </form>
        <div class="adm-drawer-foot"><button type="button" class="adm-btn" id="dwCancel">Hủy</button><button type="submit" form="sf" class="adm-btn primary" id="dwSave">Lưu</button></div>
      </aside>`;
    const $ = s => page.querySelector(s), sf = $('#sf'), dw = $('#dw'), ov = $('#ov');
    const setPwVisible = show => { sf.password.type = show ? 'text' : 'password'; $('#pwToggle').textContent = show ? 'Ẩn' : 'Hiện'; };
    $('#pwToggle').onclick = () => setPwVisible(sf.password.type === 'password');

    function render() {
        $('#rows').innerHTML = list.map(s => `<tr data-id="${s.id}" class="${s.active ? '' : 'st-off'}">
            <td data-label="Họ tên"><b>${esc(s.fullName)}</b>${s.self ? ' <span class="adm-muted">(bạn)</span>' : ''}${s.email ? `<br><small class="adm-muted">${esc(s.email)}</small>` : ''}</td>
            <td data-label="Số điện thoại">${esc(s.phone)}</td>
            <td data-label="Vai trò"><span class="adm-badge ${s.role === 'ADMIN' ? 'info' : ''}">${ROLE[s.role] || esc(s.role)}</span></td>
            <td data-label="Trạng thái"><span class="adm-badge ${s.active ? 'ok' : 'bad'}">${s.active ? 'Đang hoạt động' : 'Đã khóa'}</span></td>
            <td data-label="Tạo ngày">${fmtDate(s.createdAt)}</td>
            <td><div class="st-act"><button type="button" data-act="edit">Sửa</button><button type="button" data-act="reset">Đặt lại mật khẩu</button>
              <button type="button" data-act="toggle" ${s.self ? 'disabled title="Không thể tự khóa chính mình"' : ''}>${s.active ? 'Khóa' : 'Mở khóa'}</button></div></td></tr>`).join('');
    }
    async function load() { list = await api('/staff'); render(); }

    function open(m, s) {
        mode = m; cur = s || null; sf.reset(); setPwVisible(false); $('#sfErr').textContent = '';
        sf.querySelectorAll('[data-for]').forEach(el => el.hidden = !el.dataset.for.split(' ').includes(m));
        $('#dwTitle').textContent = { create: 'Thêm nhân viên', edit: 'Sửa nhân viên', reset: 'Đặt lại mật khẩu' }[m];
        $('#who').hidden = m !== 'reset'; if (m === 'reset') $('#who').textContent = `${s.fullName} · ${s.phone}. Hãy báo mật khẩu mới cho họ và nhắc đổi trong mục Cài đặt.`;
        $('#pwLabel').textContent = m === 'reset' ? 'Mật khẩu mới' : 'Mật khẩu ban đầu';
        sf.phone.disabled = m === 'edit'; sf.role.disabled = !!(s && s.self);
        if (m === 'edit') { sf.fullName.value = s.fullName || ''; sf.phone.value = s.phone; sf.email.value = s.email || ''; sf.role.value = s.role; }
        dw.classList.add('show'); ov.classList.add('show'); dw.setAttribute('aria-hidden', 'false');
    }
    const close = () => { dw.classList.remove('show'); ov.classList.remove('show'); dw.setAttribute('aria-hidden', 'true'); };
    document.getElementById('addBtn').onclick = () => open('create');
    $('#dwClose').onclick = $('#dwCancel').onclick = ov.onclick = close;
    document.addEventListener('keydown', e => { if (e.key === 'Escape') close(); });

    $('#rows').addEventListener('click', async e => {
        const b = e.target.closest('[data-act]'); if (!b || b.disabled) return;
        const s = list.find(x => x.id === +b.closest('tr').dataset.id);
        if (b.dataset.act === 'edit') open('edit', s);
        else if (b.dataset.act === 'reset') open('reset', s);
        else {
            b.disabled = true;
            try { await api(`/staff/${s.id}/active`, { method: 'PATCH', body: { active: !s.active } }); toast(s.active ? 'Đã khóa tài khoản.' : 'Đã mở khóa tài khoản.', 'ok'); await load(); }
            catch (ex) { toast(ex.message, 'error'); b.disabled = false; }
        }
    });

    sf.addEventListener('submit', async e => {
        e.preventDefault();
        const f = sf.elements, btn = $('#dwSave'), err = $('#sfErr');
        err.textContent = ''; btn.disabled = true;
        try {
            if (mode === 'create') await api('/staff', { method: 'POST', body: { fullName: f.fullName.value.trim(), phone: f.phone.value.trim(), email: f.email.value.trim() || null, role: f.role.value, password: f.password.value } });
            else if (mode === 'edit') await api('/staff/' + cur.id, { method: 'PUT', body: { fullName: f.fullName.value.trim(), email: f.email.value.trim() || null, role: f.role.value } });
            else await api(`/staff/${cur.id}/password`, { method: 'PUT', body: { newPassword: f.password.value } });
            close(); await load(); toast({ create: 'Đã thêm nhân viên.', edit: 'Đã lưu thay đổi.', reset: 'Đã đặt lại mật khẩu.' }[mode], 'ok');
        } catch (ex) { err.textContent = ex.message; }
        finally { btn.disabled = false; }
    });

    try { await load(); } catch (ex) { $('#rows').innerHTML = `<tr><td colspan="6" class="adm-muted">${esc(ex.message)}</td></tr>`; }
})();