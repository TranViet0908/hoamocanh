/* Trang Lịch trải nghiệm: khách đặt lịch đến tiệm (tự tay làm tại tiệm) theo từng khung giờ. */
(async function () {
    const { api, esc, toast } = Admin;
    const ST = { PENDING: ['Chờ xác nhận', 'warn'], CONFIRMED: ['Đã xác nhận', 'info'], PREPARING: ['Đang chuẩn bị', 'info'],
        IN_PROGRESS: ['Đang thực hiện', 'info'], COMPLETED: ['Đã hoàn thành', 'ok'] };
    const PAY = { UNPAID: ['Chưa thanh toán', 'warn'], DEPOSITED: ['Đã đặt cọc', 'info'], PAID: ['Đã thanh toán', 'ok'] };
    const WD = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];
    const pad = n => String(n).padStart(2, '0');
    const ymd = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
    const parse = s => { const [y, m, d] = s.split('-').map(Number); return new Date(y, m - 1, d); };
    const addDays = (s, n) => { const d = parse(s); d.setDate(d.getDate() + n); return ymd(d); };
    const badge = ([t, c]) => `<span class="adm-badge ${c}">${t}</span>`;
    const hhmm = s => { const d = new Date(String(s).replace(' ', 'T')); return isNaN(d) ? '' : `${pad(d.getHours())}:${pad(d.getMinutes())}`; };

    const { page, user } = await Admin.mount({ active: 'calendar.html', title: 'Lịch trải nghiệm' });
    const isAdmin = user.role === 'ADMIN';
    const todayStr = ymd(new Date());
    let sel = todayStr, stripFrom = todayStr, summary = [], day = null;

    page.innerHTML = `
      <div class="cl-bar"><button type="button" class="adm-btn" id="prev">←</button><input class="adm-input" type="date" id="jump">
        <button type="button" class="adm-btn" id="next">→</button><button type="button" class="adm-btn" id="today">Hôm nay</button></div>
      <div class="cl-strip" id="strip"></div><div class="cl-day" id="day"></div>
      ${isAdmin ? `<section class="adm-card cl-cfg"><h2>Khung giờ nhận khách</h2>
        <p class="adm-muted" style="margin:0">Khách chỉ đặt được các khung giờ này, tối đa số chỗ bên dưới. Đổi cấu hình không ảnh hưởng đơn đã đặt.</p>
        <form id="cfg" class="cl-row" novalidate>
          <label>Giờ mở cửa<input class="adm-input" type="time" name="open"></label>
          <label>Giờ đóng cửa<input class="adm-input" type="time" name="close"></label>
          <label>Mỗi khung<select class="adm-input" name="slot"><option value="60">1 tiếng</option><option value="90">1 tiếng rưỡi</option><option value="120">2 tiếng</option><option value="180">3 tiếng</option></select></label>
          <label>Số chỗ mỗi khung<input class="adm-input" style="width:110px" type="number" name="cap" min="1" max="50"></label>
          <button class="adm-btn primary" type="submit">Lưu cấu hình</button></form>
        <p class="adm-error" id="cfgErr" role="alert" style="margin:8px 0 0"></p></section>` : ''}`;
    const $ = s => page.querySelector(s);

    function renderStrip() {
        $('#strip').innerHTML = summary.map(s => { const d = parse(s.date);
            return `<button type="button" data-d="${s.date}" class="${s.date === sel ? 'on' : ''} ${s.booked ? 'busy' : ''}">
              <small>${s.date === todayStr ? 'Hôm nay' : WD[d.getDay()]}</small><b>${pad(d.getDate())}/${pad(d.getMonth() + 1)}</b><i>${s.booked}/${s.capacity}</i></button>`; }).join('');
        const on = $('#strip .on'); if (on) on.scrollIntoView({ inline: 'center', block: 'nearest' });
    }

    const booking = b => `<a class="cl-bk" href="orders.html?id=${b.orderId}"><b>${esc(b.customerName)}</b> <small>· ${esc(b.code)} · ${esc(b.phone)}</small>
        <div>${badge(ST[b.status] || [b.status, ''])}${badge(PAY[b.paymentStatus] || PAY.UNPAID)}</div></a>`;

    function renderDay() {
        const d = parse(day.date);
        const total = day.slots.reduce((n, s) => n + s.bookings.length, 0) + day.outside.length;
        $('#day').innerHTML = `<h2>${d.getDay() === 0 ? 'Chủ nhật' : 'Thứ ' + (d.getDay() + 1)}, ${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()}
            <span class="adm-muted" style="font:400 14px 'Be Vietnam Pro',sans-serif"> · ${total} lượt đặt</span></h2>
          <div class="cl-grid">${day.slots.map(s => { const n = s.bookings.length, full = n >= s.capacity;
            return `<div class="cl-slot"><header><b>${s.start} – ${s.end}</b><span class="adm-badge ${full ? 'bad' : n ? 'warn' : 'ok'}">${full ? 'Đầy' : n + '/' + s.capacity + ' chỗ'}</span></header>
              <div class="cl-bar2"><span style="width:${Math.min(100, n / s.capacity * 100)}%"></span></div>
              ${n ? s.bookings.map(booking).join('') : '<p class="cl-empty">Chưa có khách đặt.</p>'}</div>`; }).join('')}
            ${day.outside.length ? `<div class="cl-slot"><header><b>Ngoài khung giờ</b><span class="adm-badge">${day.outside.length}</span></header>
              <p class="cl-empty">Đơn đặt trước khi có lịch, giờ hẹn không thuộc khung nào.</p>
              ${day.outside.map(b => booking(b).replace('</b>', ` · ${hhmm(b.time)}</b>`)).join('')}</div>` : ''}</div>`;
    }

    async function select(date) {
        sel = date; $('#jump').value = sel;
        const inStrip = summary.length && sel >= stripFrom && sel <= addDays(stripFrom, 13);
        if (!inStrip) stripFrom = sel < todayStr ? sel : (sel <= addDays(todayStr, 13) ? todayStr : sel);
        try {
            [summary, day] = await Promise.all([api(`/calendar/summary?from=${stripFrom}&days=14`), api('/calendar/day?date=' + sel)]);
            renderStrip(); renderDay();
        } catch (e) { $('#day').innerHTML = `<div class="adm-card adm-muted">${esc(e.message)}</div>`; }
    }

    $('#strip').onclick = e => { const b = e.target.closest('[data-d]'); if (b) select(b.dataset.d); };
    $('#jump').onchange = e => e.target.value && select(e.target.value);
    $('#prev').onclick = () => select(addDays(sel, -1));
    $('#next').onclick = () => select(addDays(sel, 1));
    $('#today').onclick = () => { stripFrom = todayStr; select(todayStr); };
    setInterval(() => select(sel), 60000);   // tự làm mới mỗi phút

    if (isAdmin) {
        const cfg = $('#cfg'), err = $('#cfgErr');
        try { const c = await api('/calendar/config'); cfg.open.value = c.open; cfg.close.value = c.close; cfg.slot.value = c.slotMinutes; cfg.cap.value = c.capacity; }
        catch (e) { err.textContent = e.message; }
        cfg.addEventListener('submit', async e => {
            e.preventDefault(); const btn = cfg.querySelector('button'); err.textContent = ''; btn.disabled = true;
            try { await api('/calendar/config', { method: 'PUT', body: { open: cfg.open.value, close: cfg.close.value, slotMinutes: +cfg.slot.value, capacity: +cfg.cap.value } });
                toast('Đã lưu cấu hình khung giờ.', 'ok'); await select(sel); }
            catch (ex) { err.textContent = ex.message; } finally { btn.disabled = false; }
        });
    }
    select(sel);
})();
