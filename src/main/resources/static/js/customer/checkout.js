const API_BASE = '/customer';
const money = n => Number(n || 0).toLocaleString('vi-VN') + 'đ';
const esc = s => String(s ?? '').replace(/[&<>"']/g,
    c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

// Khớp CheckoutReq.deliveryType: ONLINE | PICKUP | DELIVERY
const MODES = {
    ONLINE:   { hint: 'Dùng thiết kế để gửi quà online: viết lời chúc, thêm ảnh hoặc video. Sau khi đặt, hệ thống tạo đường link và mã QR có logo Mộc Anh để gửi người nhận.',
                time: false, address: false, gift: true },
    PICKUP:   { hint: 'Bạn đặt lịch đến tiệm, nhận nguyên liệu và được florist hướng dẫn hoàn thiện sản phẩm.',
                time: true, timeLabel: 'Thời gian bạn đến tiệm', address: false, gift: false },
    DELIVERY: { hint: 'Mộc Anh kiểm tra nguyên liệu, thực hiện sản phẩm theo thiết kế rồi giao đến địa chỉ của bạn.',
                time: true, timeLabel: 'Thời gian muốn nhận hoa', address: true, gift: false }
};

// Khớp CheckoutReq.paymentMethod: CASH | ONLINE
const PAY_HINTS = {
    CASH: 'Bạn trả tiền trực tiếp khi nhận hoa hoặc khi đến tiệm.',
    ONLINE: 'Sau khi đặt đơn, bạn sẽ nhận hướng dẫn chuyển khoản. Đơn được xác nhận khi tiệm nhận được tiền.'
};

const FORM_LABELS = { round: 'Bó tròn', natural: 'Bó tự nhiên', long: 'Bó dài' };
const MIX_LABELS = { 'rose-baby': 'Hồng + baby', 'rose-daisy': 'Hồng + cúc', 'tulip-baby': 'Tulip + baby' };

function formatSimSummary(sim) {
    if (!sim) return '';
    const mix = MIX_LABELS[sim.mix] || sim.mix || '';
    const form = FORM_LABELS[sim.form] || sim.form || '';
    return [mix, form, sim.flowerColor ? `Hoa ${sim.flowerColor}` : '', sim.paperColor ? `Giấy ${sim.paperColor}` : '', sim.ribbonColor ? `Nơ ${sim.ribbonColor}` : '']
        .filter(Boolean).join(' · ');
}

document.addEventListener('DOMContentLoaded', () => {
    const form = document.querySelector('#checkoutForm');
    const submitBtn = form.querySelector('button[type="submit"]');

    // 1. Đọc thiết kế từ trang design; không có thì quay lại
    const raw = sessionStorage.getItem('hoaOrder');
    if (!raw) {
        showModal('Không tìm thấy thiết kế. Vui lòng thiết kế lại.', { type: 'error' })
            .then(() => { location.href = '/customer/design.html'; });
        return;
    }
    const orderData = JSON.parse(raw);
    const simSummaryText = formatSimSummary(orderData.simulation);

    // 2. Tóm tắt đơn (kèm cấu hình bản phối mô phỏng từ Bước 5)
    document.querySelector('#orderSummary').innerHTML = `
        <p><b>${esc(orderData.productTypeName || '')}</b> · ${esc([orderData.style, orderData.themeColor, orderData.occasion].filter(Boolean).join(' · '))}</p>
        ${simSummaryText ? `<p style="font-size:12.5px;color:var(--muted);margin-top:4px"><b>Bản phối mô phỏng:</b> ${esc(simSummaryText)}</p>` : ''}
        <hr>
        ${(orderData.display || []).map(i =>
            `<p>${esc(i.name)} ×${i.quantity} <span style="float:right">${money(i.price * i.quantity)}</span></p>`).join('')}
        <hr>
        <p><b>Ngân sách gói</b> <b style="float:right">${money(orderData.budgetLevel || orderData.total)}</b></p>
        <p style="font-size:12.5px;color:var(--muted)"><span>Tạm tính nguyên liệu</span> <span style="float:right">${money(orderData.total)}</span></p>`;

    // 3. Hình thức đầu ra -> hiện/ẩn ô nhập tương ứng
    let outputType = 'ONLINE';
    let paymentMethod = 'CASH';
    const media = { image: null, video: null };
    let uploading = 0;

    const fields = {
        time: { box: form.querySelector('#fieldTime'), input: form.elements.time },
        address: { box: form.querySelector('#fieldAddress'), input: form.elements.address },
        gift: { box: form.querySelector('#fieldGift'), input: form.elements.gift }
    };

    // ----- Đặt lịch đến tiệm (PICKUP): chọn ngày rồi chọn khung giờ còn chỗ -----
    let picker = null;
    const pad = n => String(n).padStart(2, '0');
    const ymd = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;

    function togglePicker(on) {
        const input = fields.time.input;
        if (!on) { if (picker) picker.hidden = true; input.style.display = ''; return; }
        input.style.display = 'none'; input.value = '';
        if (!picker) {
            picker = document.createElement('div');
            picker.innerHTML = '<input type="date" id="bkDate" style="width:100%;margin-bottom:10px">' +
                '<div id="bkSlots" class="choice-grid" style="margin-bottom:6px"></div>' +
                '<p id="bkHint" style="font-size:12px;color:var(--muted);margin:0"></p>';
            fields.time.box.appendChild(picker);
            const d = picker.querySelector('#bkDate'), t = new Date(), max = new Date();
            max.setDate(t.getDate() + 30);
            d.min = ymd(t); d.max = ymd(max);
            d.onchange = () => loadSlots(d.value);
        }
        picker.hidden = false;
        picker.querySelector('#bkDate').value = '';
        picker.querySelector('#bkSlots').innerHTML = '';
        picker.querySelector('#bkHint').textContent = 'Chọn ngày bạn muốn đến tiệm để xem các khung giờ còn chỗ.';
    }

    async function loadSlots(date) {
        const box = picker.querySelector('#bkSlots'), hint = picker.querySelector('#bkHint');
        fields.time.input.value = ''; box.innerHTML = '';
        if (!date) return;
        hint.textContent = 'Đang tải khung giờ...';
        try {
            const res = await fetch(`${API_BASE}/booking/slots?date=${encodeURIComponent(date)}`);
            let data = null; try { data = await res.json(); } catch (_) {}
            if (!res.ok) throw new Error(data?.message || `Lỗi máy chủ (${res.status})`);
            hint.textContent = data.some(s => s.available)
                ? 'Chọn một khung giờ, số chỗ còn lại ghi trong ngoặc.'
                : 'Ngày này đã hết chỗ, vui lòng chọn ngày khác.';
            data.forEach(s => {
                const b = document.createElement('button');
                b.type = 'button'; b.className = 'choice'; b.disabled = !s.available;
                if (!s.available) b.style.opacity = '.45';
                b.textContent = s.available ? `${s.label} (còn ${s.remaining})` : `${s.label} (hết chỗ)`;
                b.onclick = () => {
                    box.querySelectorAll('.choice').forEach(x => x.classList.remove('selected'));
                    b.classList.add('selected');
                    fields.time.input.value = `${date}T${s.start}`;
                };
                box.appendChild(b);
            });
        } catch (err) { hint.textContent = err.message; }
    }

    function applyMode(mode) {
        outputType = mode;
        const cfg = MODES[mode];
        document.querySelector('#outputHint').textContent = cfg.hint;
        ['time', 'address', 'gift'].forEach(k => {
            fields[k].box.hidden = !cfg[k];
            if (!cfg[k]) fields[k].input.value = '';
        });
        if (!cfg.gift) resetMedia();
        fields.time.input.required = cfg.time && mode !== 'PICKUP';
        fields.address.input.required = cfg.address;
        if (cfg.time) document.querySelector('#timeLabel').textContent = cfg.timeLabel;
        togglePicker(mode === 'PICKUP');
    }

    const now = new Date(Date.now() - new Date().getTimezoneOffset() * 60000);
    fields.time.input.min = now.toISOString().slice(0, 16);

    const tabs = document.querySelectorAll('#outputTabs button');
    tabs.forEach(btn => btn.onclick = () => {
        tabs.forEach(b => b.classList.remove('selected'));
        btn.classList.add('selected');
        applyMode(btn.dataset.output);
    });

    // 4. Thanh toán
    const payTabs = document.querySelectorAll('#payTabs button');
    const applyPay = m => {
        paymentMethod = m;
        document.querySelector('#payHint').textContent = PAY_HINTS[m];
    };
    payTabs.forEach(btn => btn.onclick = () => {
        payTabs.forEach(b => b.classList.remove('selected'));
        btn.classList.add('selected');
        applyPay(btn.dataset.pay);
    });

    // 5. Upload ảnh/video quà tặng
    function setStatus(kind, text, isError) {
        const el = document.querySelector(kind === 'image' ? '#giftImageStatus' : '#giftVideoStatus');
        el.textContent = text;
        el.style.color = isError ? '#9a504b' : 'var(--sage)';
    }
    function resetMedia() {
        media.image = media.video = null;
        document.querySelector('#giftImage').value = '';
        document.querySelector('#giftVideo').value = '';
        setStatus('image', ''); setStatus('video', '');
    }
    async function upload(kind, file) {
        if (!file) { media[kind] = null; setStatus(kind, ''); return; }
        const fd = new FormData();
        fd.append('file', file);
        uploading++; submitBtn.disabled = true;
        setStatus(kind, 'Đang tải lên...');
        try {
            const res = await fetch(`${API_BASE}/uploads/gift`, { method: 'POST', body: fd });
            let data = null; try { data = await res.json(); } catch (_) {}
            if (!res.ok) throw new Error(data?.message || `Lỗi máy chủ (${res.status})`);
            if (data.type !== kind) throw new Error('Sai loại tệp.');
            media[kind] = data.url;
            setStatus(kind, '✓ Đã tải lên: ' + file.name);
        } catch (err) {
            media[kind] = null;
            document.querySelector(kind === 'image' ? '#giftImage' : '#giftVideo').value = '';
            setStatus(kind, err.message, true);
        } finally {
            if (--uploading === 0) submitBtn.disabled = false;
        }
    }
    document.querySelector('#giftImage').onchange = e => upload('image', e.target.files[0]);
    document.querySelector('#giftVideo').onchange = e => upload('video', e.target.files[0]);

    const isGiftPrivateCb = document.querySelector('#isGiftPrivate');
    const giftPwdWrap = document.querySelector('#giftPasswordWrap');
    if (isGiftPrivateCb && giftPwdWrap) {
        isGiftPrivateCb.addEventListener('change', e => {
            giftPwdWrap.style.display = e.target.checked ? 'block' : 'none';
        });
    }

    applyMode('ONLINE');
    applyPay('CASH');

    // 6. Gửi đơn — field khớp CheckoutReq.java
    form.addEventListener('submit', async e => {
        e.preventDefault();
        if (uploading) return showModal('Tệp đính kèm đang được tải lên, vui lòng đợi một chút.');
        const fd = new FormData(form);
        const name = String(fd.get('name') || '').trim();
        const phone = String(fd.get('phone') || '').trim();
        const address = String(fd.get('address') || '').trim();
        const note = String(fd.get('note') || '').trim();
        const giftMsg = String(fd.get('gift') || '').trim();
        const giftPwd = String(fd.get('giftPassword') || '').trim();
        const giftHint = String(fd.get('giftPasswordHint') || '').trim();
        const voucher = String(fd.get('voucher') || '').trim();

        if (!name) return showModal('Vui lòng nhập họ và tên.');
        if (!/^(0|\+84)\d{9,10}$/.test(phone.replace(/[\s.-]/g, ''))) return showModal('Số điện thoại chưa đúng định dạng.');
        if (MODES[outputType].time && !fd.get('time')) return showModal('Vui lòng chọn thời gian.');
        if (MODES[outputType].address && !address) return showModal('Vui lòng nhập địa chỉ nhận hàng.');

        const gift = MODES[outputType].gift;
        const combinedNotes = [
            orderData.occasion ? 'Mục đích tặng: ' + orderData.occasion : '',
            simSummaryText ? 'Bản phối mô phỏng: ' + simSummaryText : '',
            note
        ].filter(Boolean).join('\n') || null;

        const payload = {
            productTypeId: orderData.productTypeId,
            budgetLevel: orderData.budgetLevel,
            style: orderData.style,
            themeColor: orderData.themeColor || null,
            styleNote: combinedNotes,
            deliveryType: outputType,
            paymentMethod,
            receiverName: name,
            receiverPhone: phone,
            shippingAddress: MODES[outputType].address ? address : null,
            deliveryTime: MODES[outputType].time ? fd.get('time') : null,
            giftMessage: gift ? (giftMsg || null) : null,
            giftImageUrl: gift ? media.image : null,
            giftVideoUrl: gift ? media.video : null,
            isGiftPrivate: gift ? Boolean(isGiftPrivateCb && isGiftPrivateCb.checked) : false,
            giftPassword: gift ? (giftPwd || null) : null,
            giftPasswordHint: gift ? (giftHint || null) : null,
            voucherCode: voucher || null,
            items: orderData.items
        };

        submitBtn.disabled = true;
        const oldText = submitBtn.textContent;
        submitBtn.textContent = 'Đang gửi...';

        try {
            const res = await fetch(`${API_BASE}/orders/checkout`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            let data = null;
            try { data = await res.json(); } catch (_) {}
            if (!res.ok) throw new Error(data?.message || `Lỗi máy chủ (${res.status})`);

            sessionStorage.setItem('lastOrder', JSON.stringify({
                orderCode: data.orderCode,
                deliveryType: outputType,
                paymentMethod: data.paymentMethod,
                payableAmount: data.payableAmount,
                giftUrl: data.qrCodeUrl || null,
                giftPassword: data.giftPassword || null
            }));
            sessionStorage.removeItem('hoaOrder');
            location.href = '/customer/thankyou.html?orderCode=' + encodeURIComponent(data.orderCode);
        } catch (err) {
            showModal(err.message, { type: 'error', title: 'Đặt hàng chưa thành công' });
            submitBtn.disabled = false;
            submitBtn.textContent = oldText;
        }
    });
});