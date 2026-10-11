/* Trang Đơn hàng: lọc theo hình thức/trạng thái, xem thiết kế, ghi nhận thanh toán, chuyển trạng thái. */
(async function () {
    const { api, esc, money, toast } = Admin;
    const TYPE = { ONLINE: 'Gửi tặng online', PICKUP: 'Tự làm tại tiệm', DELIVERY: 'Mộc Anh làm giúp' };
    const ST = {
        PENDING: ['Chờ xác nhận', 'warn'], CONFIRMED: ['Đã xác nhận', 'info'], PREPARING: ['Đang chuẩn bị nguyên liệu', 'info'],
        IN_PROGRESS: ['Đang thực hiện', 'info'], AWAITING_APPROVAL: ['Chờ khách duyệt', 'warn'],
        READY: ['Sẵn sàng giao/nhận', 'ok'], COMPLETED: ['Đã hoàn thành', 'ok'], CANCELLED: ['Đã hủy', 'bad']
    };
    const PAY = { UNPAID: ['Chưa thanh toán', 'warn'], DEPOSITED: ['Đã đặt cọc', 'info'], PAID: ['Đã thanh toán', 'ok'] };
    const ACTION = { CONFIRMED: 'Xác nhận đơn', PREPARING: 'Bắt đầu chuẩn bị nguyên liệu', IN_PROGRESS: 'Bắt đầu thực hiện',
        AWAITING_APPROVAL: 'Gửi khách duyệt', READY: 'Sẵn sàng giao/nhận', COMPLETED: 'Hoàn thành đơn', CANCELLED: 'Hủy đơn' };
    const METHOD = { CASH: 'Tiền mặt', BANK_TRANSFER: 'Chuyển khoản', MOMO: 'MoMo' };
    const FINAL = ['COMPLETED', 'CANCELLED'];
    const VIEWS = { active: o => !FINAL.includes(o.status), PENDING: o => o.status === 'PENDING',
        COMPLETED: o => o.status === 'COMPLETED', CANCELLED: o => o.status === 'CANCELLED', all: () => true };

    const PLACEHOLDER = 'data:image/svg+xml;utf8,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="88" height="88"><rect width="100%" height="100%" fill="#f1ede4"/><text x="50%" y="62%" font-size="36" text-anchor="middle">🌸</text></svg>');
    const imgSrc = u => !u ? PLACEHOLDER : (/^(https?:|\/|data:)/.test(u) ? u : '/' + u);
    const toDate = s => s ? new Date(String(s).replace(' ', 'T')) : null;
    const fmt = s => { const d = toDate(s); return d && !isNaN(d) ? d.toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }) : '—'; };
    const badge = ([t, c]) => `<span class="adm-badge ${c}">${t}</span>`;
    const digits = s => String(s || '').replace(/[^\d+]/g, '');

    let orders = [], cur = null, armed = false;
    const f = { type: '', view: 'active', q: '' };

    const { page } = await Admin.mount({ active: 'orders.html', title: 'Đơn hàng',
        actions: '<button type="button" class="adm-btn" id="refresh">Làm mới</button>' });

    page.innerHTML = `
      <div class="od-tabs" id="tabs"></div>
      <div class="od-bar">
        <input class="adm-input" id="q" type="search" placeholder="Tìm mã đơn (MA-12), tên hoặc số điện thoại...">
        <select class="adm-input" id="view"><option value="active">Đang xử lý</option><option value="PENDING">Chờ xác nhận</option>
          <option value="COMPLETED">Đã hoàn thành</option><option value="CANCELLED">Đã hủy</option><option value="all">Tất cả</option></select>
      </div>
      <div class="adm-card"><table class="adm-table od-table"><thead><tr><th>Mã đơn</th><th>Khách hàng</th><th>Hình thức</th>
        <th>Hẹn</th><th>Cần thu</th><th>Thanh toán</th><th>Trạng thái</th></tr></thead><tbody id="rows"></tbody></table></div>
      <div class="adm-overlay" id="ov"></div>
      <aside class="adm-drawer" id="dw" style="width:min(560px,100%)" aria-hidden="true">
        <div class="adm-drawer-head"><div><h2 id="dwTitle"></h2><span id="dwBadge"></span></div><button type="button" class="adm-btn" id="dwClose">Đóng</button></div>
        <div class="adm-drawer-body" id="dwBody"></div>
        <div class="adm-drawer-foot" id="dwFoot" style="flex-direction:column;align-items:stretch"></div>
      </aside>`;
    const $ = s => page.querySelector(s);
    const qView = new URLSearchParams(location.search).get('view');   // từ link ở trang Tổng quan
    if (qView && Object.hasOwn(VIEWS, qView)) { f.view = qView; $('#view').value = qView; }

    // ---------- Danh sách ----------
    function render() {
        const live = orders.filter(VIEWS.active);
        $('#tabs').innerHTML = [['', 'Tất cả', live.length], ...Object.entries(TYPE).map(([k, v]) => [k, v, live.filter(o => o.deliveryType === k).length])]
            .map(([k, t, n]) => `<button type="button" data-type="${k}" class="${k === f.type ? 'on' : ''}">${t}${n ? `<b>${n}</b>` : ''}</button>`).join('');

        const q = f.q.toLowerCase().replace(/^ma-/, '');
        const list = orders.filter(o => VIEWS[f.view](o) && (!f.type || o.deliveryType === f.type) &&
            (!q || String(o.id) === q || (o.customerName || '').toLowerCase().includes(q) || (o.phone || '').includes(q)));

        $('#rows').innerHTML = list.length ? list.map(o => `<tr data-id="${o.id}">
            <td data-label="Mã đơn"><b>${esc(o.code)}</b></td>
            <td data-label="Khách">${esc(o.customerName)}<br><small class="adm-muted">${esc(o.phone)}</small></td>
            <td data-label="Hình thức">${TYPE[o.deliveryType] || esc(o.deliveryType)}</td>
            <td data-label="Hẹn">${o.deliveryType === 'ONLINE' ? '—' : fmt(o.deliveryTime)}</td>
            <td data-label="Cần thu">${money(o.payable)}</td>
            <td data-label="Thanh toán">${badge(PAY[o.paymentStatus] || PAY.UNPAID)}</td>
            <td data-label="Trạng thái">${badge(ST[o.status] || [o.status, ''])}</td></tr>`).join('')
            : '<tr><td colspan="7" class="od-empty">Không có đơn nào phù hợp.</td></tr>';
    }

    async function load() { orders = await api('/orders'); render(); }

    $('#tabs').onclick = e => { const b = e.target.closest('[data-type]'); if (b) { f.type = b.dataset.type; render(); } };
    $('#q').oninput = e => { f.q = e.target.value.trim(); render(); };
    $('#view').onchange = e => { f.view = e.target.value; render(); };
    $('#refresh').onclick = () => load().then(() => toast('Đã cập nhật.', 'ok')).catch(e => toast(e.message, 'error'));
    $('#rows').onclick = e => { const tr = e.target.closest('tr[data-id]'); if (tr) openOrder(+tr.dataset.id); };
    setInterval(() => { if (!$('#dw').classList.contains('show')) load().catch(() => { }); }, 60000);   // tự làm mới mỗi phút

    // ---------- Chi tiết ----------
    const dw = $('#dw'), ov = $('#ov');
    const closeDrawer = () => { dw.classList.remove('show'); ov.classList.remove('show'); dw.setAttribute('aria-hidden', 'true'); };
    $('#dwClose').onclick = ov.onclick = closeDrawer;
    document.addEventListener('keydown', e => { if (e.key === 'Escape') closeDrawer(); });

    async function openOrder(id) {
        try { cur = await api('/orders/' + id); } catch (e) { return toast(e.message, 'error'); }
        armed = false; renderDetail();
        dw.classList.add('show'); ov.classList.add('show'); dw.setAttribute('aria-hidden', 'false');
    }

    function renderDetail() {
        const o = cur, remaining = Math.max(0, o.payable - o.paid);
        $('#dwTitle').textContent = o.code;
        $('#dwBadge').innerHTML = badge(ST[o.status] || [o.status, '']) + ' ' + badge(PAY[o.paymentStatus] || PAY.UNPAID);
        const safeGift = u => typeof u === 'string' && u.startsWith('/uploads/gift/') && !u.includes('..') ? u : null;

        $('#dwBody').innerHTML = `
          <div class="od-sec"><h3>Khách hàng</h3><div class="od-kv">
            <span>Họ tên</span><b>${esc(o.customerName)}</b>
            <span>Liên hệ</span><div><a href="tel:${esc(digits(o.phone))}">${esc(o.phone)}</a> · <a href="https://zalo.me/${esc(digits(o.phone))}" target="_blank" rel="noopener">Zalo</a></div>
            <span>Hình thức</span><b>${TYPE[o.deliveryType] || esc(o.deliveryType)}</b>
            ${o.deliveryType === 'ONLINE' ? '' : `<span>${o.deliveryType === 'PICKUP' ? 'Khách đến lúc' : 'Giao lúc'}</span><b>${fmt(o.deliveryTime)}</b>`}
            ${o.shippingAddress ? `<span>Địa chỉ</span><div>${esc(o.shippingAddress)}</div>` : ''}
            <span>Đặt lúc</span><div>${fmt(o.createdAt)}</div></div></div>

          <div class="od-sec"><h3>Thiết kế của khách</h3>
            <p style="margin:0 0 8px">${esc([o.productTypeName, o.style, o.themeColor].filter(Boolean).join(' · '))}</p>
            ${o.styleNote ? `<p class="adm-muted" style="white-space:pre-line;margin:0 0 8px">${esc(o.styleNote)}</p>` : ''}
            ${o.items.map(i => `<div class="od-item"><img src="${esc(imgSrc(i.imageUrl))}" alt="" onerror="this.onerror=null;this.src='${PLACEHOLDER}'">
              <div><b>${esc(i.name)}</b><br><small>× ${i.quantity}</small></div><span>${money(i.unitPrice * i.quantity)}</span></div>`).join('')}
            <div class="od-kv" style="margin-top:10px"><span>Mức giá</span><b>${money(o.totalPrice)}</b>
              ${o.discountAmount > 0 ? `<span>Giảm giá</span><div>− ${money(o.discountAmount)}</div>` : ''}</div></div>

          ${o.deliveryType === 'ONLINE' ? `<div class="od-sec"><h3>Quà tặng online</h3><div class="od-gift">
            ${o.giftMessage ? esc(o.giftMessage) : '<span class="adm-muted">Không có lời chúc.</span>'}
            ${safeGift(o.giftImageUrl) ? `<img src="${esc(safeGift(o.giftImageUrl))}" alt="Ảnh kèm theo">` : ''}
            ${safeGift(o.giftVideoUrl) ? `<video src="${esc(safeGift(o.giftVideoUrl))}" controls playsinline preload="metadata"></video>` : ''}</div>
            ${o.giftUrl ? `<p style="margin:10px 0 0"><a href="${esc(o.giftUrl)}" target="_blank" rel="noopener">Mở trang quà tặng</a> · <a href="#" id="copyGift">Sao chép link</a></p>` : ''}</div>` : ''}

          <div class="od-sec"><h3>Thanh toán</h3><div class="od-kv">
            <span>Khách chọn</span><div>${o.paymentMethod === 'ONLINE' ? 'Chuyển khoản online' : 'Trả trực tiếp'}</div>
            <span>Cần thu</span><b>${money(o.payable)}</b><span>Đã nhận</span><div>${money(o.paid)}</div>
            <span>Còn lại</span><b style="color:${remaining ? 'var(--bad)' : 'var(--ok)'}">${money(remaining)}</b></div>
            ${o.payments.map(p => `<div class="od-item"><div><b>${money(p.amount)}</b> · ${METHOD[p.method] || esc(p.method)}<br>
              <small>${p.type === 'DEPOSIT' ? 'Đặt cọc' : 'Thanh toán đủ'} · ${fmt(p.createdAt)}${p.transactionCode ? ' · ' + esc(p.transactionCode) : ''}</small></div></div>`).join('')}
            ${remaining > 0 && o.status !== 'CANCELLED' ? `<form id="payForm" class="od-pay" novalidate>
              <input class="adm-input" name="amount" type="number" min="1" max="${remaining}" value="${remaining}" aria-label="Số tiền">
              <select class="adm-input" name="method"><option value="BANK_TRANSFER">Chuyển khoản</option><option value="CASH">Tiền mặt</option><option value="MOMO">MoMo</option></select>
              <select class="adm-input" name="type"><option value="DEPOSIT">Đặt cọc</option><option value="FULL_PAYMENT" ${o.paid === 0 ? 'selected' : ''}>Thanh toán đủ</option></select>
              <input class="adm-input" name="code" placeholder="Mã giao dịch (nếu có)" maxlength="100">
              <button class="adm-btn dark full" type="submit">Ghi nhận đã nhận tiền</button></form>` : ''}</div>

          <div class="od-sec"><h3>Lịch sử</h3><ul class="od-hist"><li>Khách đặt đơn<small>${fmt(o.createdAt)}</small></li>
            ${o.history.map(h => `<li>${(ST[h.status] || [h.status])[0]}${h.note ? ' — ' + esc(h.note) : ''}<small>${fmt(h.at)}${h.by ? ' · ' + esc(h.by) : ''}</small></li>`).join('')}</ul></div>`;

        const next = o.nextStatuses.filter(s => s !== 'CANCELLED'), canCancel = o.nextStatuses.includes('CANCELLED');
        $('#dwFoot').innerHTML = o.nextStatuses.length ? `
          <textarea class="adm-input" id="note" rows="2" maxlength="255" placeholder="Ghi chú tiến độ (bắt buộc nếu hủy đơn)"></textarea>
          <div class="od-actions">${next.map((s, i) => `<button type="button" class="adm-btn ${i === 0 ? 'primary' : ''}" data-to="${s}">${
              o.status === 'AWAITING_APPROVAL' && s === 'IN_PROGRESS' ? 'Khách muốn chỉnh lại' : ACTION[s]}</button>`).join('')}
            ${canCancel ? '<button type="button" class="adm-btn bad" data-to="CANCELLED" id="cancelBtn">Hủy đơn</button>' : ''}</div>`
            : '<p class="adm-muted" style="margin:0;text-align:center">Đơn đã kết thúc.</p>';
    }

    // Chuyển trạng thái. Hủy đơn cần bấm 2 lần để tránh bấm nhầm.
    $('#dwFoot').addEventListener('click', async e => {
        const btn = e.target.closest('[data-to]'); if (!btn) return;
        const to = btn.dataset.to, note = $('#note').value.trim();
        if (to === 'CANCELLED') {
            if (!note) { $('#note').focus(); return toast('Vui lòng nhập lý do hủy đơn.', 'error'); }
            if (!armed) { armed = true; btn.classList.add('armed'); btn.textContent = 'Bấm lần nữa để hủy'; return; }
        }
        $('#dwFoot').querySelectorAll('button').forEach(b => b.disabled = true);
        try {
            cur = await api(`/orders/${cur.id}/status`, { method: 'PATCH', body: { newStatus: to, note: note || null } });
            toast('Đã cập nhật: ' + (ST[cur.status] || [cur.status])[0], 'ok');
            armed = false; renderDetail(); load();
        } catch (ex) { toast(ex.message, 'error'); armed = false; renderDetail(); }
    });

    $('#dwBody').addEventListener('submit', async e => {
        if (e.target.id !== 'payForm') return;
        e.preventDefault();
        const fd = new FormData(e.target), btn = e.target.querySelector('button');
        btn.disabled = true;
        try {
            cur = await api(`/orders/${cur.id}/payments`, { method: 'POST',
                body: { amount: +fd.get('amount'), method: fd.get('method'), type: fd.get('type'), transactionCode: fd.get('code') || null } });
            toast('Đã ghi nhận thanh toán.', 'ok'); renderDetail(); load();
        } catch (ex) { toast(ex.message, 'error'); btn.disabled = false; }
    });
    $('#dwBody').addEventListener('click', async e => {
        if (e.target.id !== 'copyGift') return;
        e.preventDefault();
        try { await navigator.clipboard.writeText(cur.giftUrl); toast('Đã sao chép link.', 'ok'); }
        catch (_) { window.prompt('Sao chép link này:', cur.giftUrl); }
    });

    try { await load(); const qid = new URLSearchParams(location.search).get('id'); if (qid) openOrder(+qid); } catch (e) { $('#rows').innerHTML = `<tr><td colspan="7" class="od-empty">${esc(e.message)}</td></tr>`; }
})();
