/* Trang Mã giảm giá (chỉ ADMIN): tạo, sửa, bật/tắt voucher. Không xóa vì đơn cũ còn tham chiếu. */
(async function () {
    const { api, esc, money, toast } = Admin;
    const STATE = { ACTIVE: ['Đang áp dụng', 'ok'], INACTIVE: ['Đã tắt', ''], EXPIRED: ['Hết hạn', 'bad'], NOT_STARTED: ['Chưa bắt đầu', 'info'], EXHAUSTED: ['Hết lượt', 'warn'] };
    const fmt = s => { const d = s ? new Date(s) : null; return d && !isNaN(d) ? d.toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : ''; };
    const toLocal = s => { if (!s) return ''; const d = new Date(s); if (isNaN(d)) return ''; return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16); };
    const PER = 15;

    const { page, user } = await Admin.mount({ active: 'vouchers.html', title: 'Mã giảm giá',
        actions: '<button type="button" class="adm-btn primary" id="addBtn">+ Thêm mã</button>' });
    if (user.role !== 'ADMIN') {
        document.getElementById('addBtn')?.remove();
        page.innerHTML = '<div class="adm-card adm-muted">Chỉ quản trị viên được quản lý mã giảm giá.</div>';
        return;
    }

    let list = [], editing = null, pg = 1;
    const f = { q: '', st: '' };
    page.innerHTML = `
      <div class="vc-bar"><input class="adm-input" id="q" type="search" placeholder="Tìm theo mã...">
        <select class="adm-input" id="st"><option value="">Mọi trạng thái</option>${Object.keys(STATE).map(k => `<option value="${k}">${STATE[k][0]}</option>`).join('')}</select></div>
      <div class="adm-card"><table class="adm-table"><thead><tr><th>Mã</th><th>Mức giảm</th><th>Đơn tối thiểu</th><th>Lượt dùng</th><th>Hiệu lực</th><th>Trạng thái</th><th></th></tr></thead><tbody id="rows"></tbody></table></div>
      <p class="adm-muted" id="cnt" style="text-align:center"></p><div id="pg"></div>
      <div class="adm-overlay" id="ov"></div>
      <aside class="adm-drawer" id="dw" aria-hidden="true">
        <div class="adm-drawer-head"><h2 id="dwTitle"></h2><button type="button" class="adm-btn" id="dwClose">Đóng</button></div>
        <form id="vf" class="adm-drawer-body" novalidate>
          <label class="adm-field">Mã giảm giá (chữ không dấu, số, - hoặc _)<input class="adm-input" name="code" maxlength="30" style="text-transform:uppercase" required></label>
          <div class="adm-2col">
            <label class="adm-field">Loại giảm<select class="adm-input" name="type"><option value="PERCENT">Giảm theo phần trăm (%)</option><option value="FIXED">Giảm số tiền cố định (đ)</option></select></label>
            <label class="adm-field"><span id="valLabel">Mức giảm (%)</span><input class="adm-input" name="value" type="number" min="1" step="any" required></label>
          </div>
          <label class="adm-field" id="maxField">Giảm tối đa (đ, để trống nếu không giới hạn)<input class="adm-input" name="max" type="number" min="1000" step="1000"></label>
          <div class="adm-2col">
            <label class="adm-field">Đơn tối thiểu (đ)<input class="adm-input" name="min" type="number" min="0" step="1000" value="0"></label>
            <label class="adm-field">Giới hạn lượt dùng (để trống nếu không giới hạn)<input class="adm-input" name="limit" type="number" min="1" step="1"></label>
          </div>
          <div class="adm-2col">
            <label class="adm-field">Bắt đầu (không bắt buộc)<input class="adm-input" name="start" type="datetime-local"></label>
            <label class="adm-field">Kết thúc (không bắt buộc)<input class="adm-input" name="end" type="datetime-local"></label>
          </div>
          <label class="adm-field" style="display:flex;gap:10px;align-items:center"><input type="checkbox" name="active" checked style="width:auto"> Đang bật</label>
          <p class="adm-error" id="vfErr" role="alert"></p>
        </form>
        <div class="adm-drawer-foot"><button type="button" class="adm-btn" id="dwCancel">Hủy</button><button type="submit" form="vf" class="adm-btn primary" id="dwSave">Lưu</button></div>
      </aside>`;
    const $ = s => page.querySelector(s), vf = $('#vf'), dw = $('#dw'), ov = $('#ov');

    const amount = v => v.discountType === 'PERCENT'
        ? `${Number(v.discountValue)}%${v.maxDiscount ? ` <small class="adm-muted">(tối đa ${money(v.maxDiscount)})</small>` : ''}` : money(v.discountValue);
    function render() {
        const q = f.q.toLowerCase(), v = list.filter(x => (!f.st || x.state === f.st) && (!q || x.code.toLowerCase().includes(q)));
        const pages = Math.max(1, Math.ceil(v.length / PER)); if (pg > pages) pg = pages;
        const part = v.slice((pg - 1) * PER, pg * PER);
        $('#rows').innerHTML = part.length ? part.map(x => {
            const [label, cls] = STATE[x.state] || [x.state, ''];
            return `<tr data-id="${x.id}"><td data-label="Mã"><span class="vc-code">${esc(x.code)}</span></td><td data-label="Mức giảm">${amount(x)}</td>
              <td data-label="Đơn tối thiểu">${Number(x.minOrderValue) > 0 ? money(x.minOrderValue) : '—'}</td>
              <td data-label="Lượt dùng">${x.usedCount}${x.usageLimit ? ' / ' + x.usageLimit : ' / không giới hạn'}</td>
              <td data-label="Hiệu lực">${x.startDate || x.endDate ? esc((fmt(x.startDate) || '…') + ' → ' + (fmt(x.endDate) || '…')) : 'Không giới hạn'}</td>
              <td data-label="Trạng thái"><span class="adm-badge ${cls}">${label}</span></td>
              <td><div class="vc-act"><button type="button" data-act="edit">Sửa</button><button type="button" data-act="toggle">${x.active ? 'Tắt' : 'Bật'}</button></div></td></tr>`;
        }).join('') : '<tr><td colspan="7" class="adm-muted" style="text-align:center;padding:30px">Chưa có mã giảm giá nào.</td></tr>';
        $('#cnt').textContent = v.length ? `Hiển thị ${(pg - 1) * PER + 1}–${(pg - 1) * PER + part.length} / ${v.length} mã` : '';
        Admin.pageNav($('#pg'), v.length, pg, PER, p => { pg = p; render(); });
    }
    async function load() { list = await api('/vouchers'); render(); }

    function syncType() {
        const pct = vf.type.value === 'PERCENT';
        $('#valLabel').textContent = pct ? 'Mức giảm (%)' : 'Số tiền giảm (đ)';
        vf.value.max = pct ? 100 : 5000000; $('#maxField').hidden = !pct;
    }
    vf.type.onchange = syncType;
    function open(v) {
        editing = v || null; vf.reset(); $('#vfErr').textContent = '';
        $('#dwTitle').textContent = v ? 'Sửa mã ' + v.code : 'Thêm mã giảm giá';
        vf.code.disabled = !!v;   // mã không đổi sau khi tạo
        if (v) {
            vf.code.value = v.code; vf.type.value = v.discountType; vf.value.value = Number(v.discountValue);
            vf.max.value = v.maxDiscount ? Number(v.maxDiscount) : ''; vf.min.value = Number(v.minOrderValue || 0);
            vf.limit.value = v.usageLimit || ''; vf.start.value = toLocal(v.startDate); vf.end.value = toLocal(v.endDate); vf.active.checked = v.active;
        }
        syncType();
        dw.classList.add('show'); ov.classList.add('show'); dw.setAttribute('aria-hidden', 'false'); (v ? vf.value : vf.code).focus();
    }
    const close = () => { dw.classList.remove('show'); ov.classList.remove('show'); dw.setAttribute('aria-hidden', 'true'); };
    document.getElementById('addBtn').onclick = () => open(null);
    $('#dwClose').onclick = $('#dwCancel').onclick = ov.onclick = close;
    document.addEventListener('keydown', e => { if (e.key === 'Escape') close(); });

    $('#q').oninput = e => { f.q = e.target.value.trim(); pg = 1; render(); };
    $('#st').onchange = e => { f.st = e.target.value; pg = 1; render(); };
    $('#rows').addEventListener('click', async e => {
        const b = e.target.closest('[data-act]'); if (!b) return;
        const v = list.find(x => x.id === +b.closest('tr').dataset.id);
        if (b.dataset.act === 'edit') return open(v);
        b.disabled = true;
        try { await api(`/vouchers/${v.id}/active`, { method: 'PATCH', body: { active: !v.active } }); toast(v.active ? 'Đã tắt mã.' : 'Đã bật mã.', 'ok'); await load(); }
        catch (ex) { toast(ex.message, 'error'); b.disabled = false; }
    });

    vf.addEventListener('submit', async e => {
        e.preventDefault();
        const err = $('#vfErr'), btn = $('#dwSave'), num = el => el.value === '' ? null : Number(el.value), iso = el => el.value ? el.value + ':00' : null;
        const body = { code: vf.code.value.trim(), discountType: vf.type.value, discountValue: num(vf.value), maxDiscount: vf.type.value === 'PERCENT' ? num(vf.max) : null,
            minOrderValue: num(vf.min) || 0, usageLimit: num(vf.limit), startDate: iso(vf.start), endDate: iso(vf.end), active: vf.active.checked };
        btn.disabled = true; err.textContent = '';
        try {
            await api(editing ? '/vouchers/' + editing.id : '/vouchers', { method: editing ? 'PUT' : 'POST', body });
            close(); await load(); toast(editing ? 'Đã lưu mã giảm giá.' : 'Đã tạo mã giảm giá.', 'ok');
        } catch (ex) { err.textContent = ex.message; } finally { btn.disabled = false; }
    });

    try { await load(); } catch (ex) { $('#rows').innerHTML = `<tr><td colspan="7" class="adm-muted">${esc(ex.message)}</td></tr>`; }
})();