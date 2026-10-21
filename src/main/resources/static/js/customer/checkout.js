const API_BASE = '/customer';
const money = n => Number(n || 0).toLocaleString('vi-VN') + 'đ';
const esc = s => String(s ?? '').replace(/[&<>"']/g,
    c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

const PLACEHOLDER = 'data:image/svg+xml;utf8,' + encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96"><rect width="100%" height="100%" fill="#f3ece4"/>' +
    '<text x="50%" y="58%" font-size="36" text-anchor="middle">🌸</text></svg>');
const imgSrc = u => !u ? PLACEHOLDER : (/^(https?:|\/|data:)/.test(u) ? u : '/' + u);

// Khớp CheckoutReq.deliveryType: ONLINE | PICKUP | DELIVERY | STUDIO_PICKUP
// (tab "Mộc Anh làm giúp" gửi DELIVERY, hoặc STUDIO_PICKUP khi khách chọn đến tiệm nhận)
const MODES = {
    ONLINE:   { hint: 'Dùng thiết kế để gửi quà online: viết lời chúc, thêm ảnh hoặc video. Sau khi đặt, hệ thống tạo đường link và mã QR có logo Mộc Anh để gửi người nhận.',
                time: false, address: false, gift: true },
    PICKUP:   { hint: 'Bạn đặt lịch đến tiệm, nhận nguyên liệu và được florist hướng dẫn hoàn thiện sản phẩm.',
                time: true, timeLabel: 'Thời gian bạn đến tiệm', address: false, gift: false },
    DELIVERY: { hint: 'Mộc Anh kiểm tra nguyên liệu, thực hiện sản phẩm theo thiết kế rồi giao đến địa chỉ của bạn.',
                time: true, timeLabel: 'Thời gian muốn nhận hoa', address: true, gift: false, card: true }
};

// Khớp CheckoutReq.paymentMethod: CASH | ONLINE
const PAY_HINTS = {
    CASH: 'Bạn trả tiền trực tiếp khi nhận hoa hoặc khi đến tiệm.',
    ONLINE: 'Sau khi đặt đơn, bạn sẽ nhận hướng dẫn chuyển khoản. Đơn được xác nhận khi tiệm nhận được tiền.'
};

const FORM_LABELS = { round: 'Bó tròn', natural: 'Bó tự nhiên', long: 'Bó dài' };
const MIX_LABELS = { 'rose-baby': 'Hồng + baby', 'rose-daisy': 'Hồng + cúc', 'tulip-baby': 'Tulip + baby' };

// Tóm tắt kết quả mô phỏng ở Bước 5 (chế độ, mẫu khách chọn, mô tả) để tiệm biết khách chọn gì.
// Dữ liệu lấy từ extractColleagueSimulationSummary() trong design.js: { mode, kind, summary, samples[], imageUrl }.
function formatSimSummary(sim) {
    if (!sim) return '';
    const clean = v => String(v || '').replace(/\s+/g, ' ').trim();
    const parts = [clean(sim.mode), clean(sim.kind)];
    const samples = Array.isArray(sim.samples) ? sim.samples.map(clean).filter(Boolean) : [];
    if (samples.length) parts.push('Mẫu đã chọn: ' + samples.join('; '));
    const summary = clean(sim.summary);
    if (summary) parts.push(summary.length > 300 ? summary.slice(0, 297) + '...' : summary);
    return parts.filter(Boolean).join(' · ').slice(0, 500);   // giữ ghi chú tổng dưới giới hạn 1000 ký tự của server
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
    const simImg = orderData.simulation && orderData.simulation.imageUrl;
    document.querySelector('#orderSummary').innerHTML = `
        <p><b>${esc(orderData.productTypeName || '')}</b> · ${esc([orderData.style, orderData.themeColor, orderData.occasion].filter(Boolean).join(' · '))}</p>
        ${simImg ? `<img src="${esc(simImg)}" alt="Hình mô phỏng bó hoa" style="display:block;width:100%;max-height:320px;object-fit:cover;border-radius:16px;margin:12px 0 4px">
        <p style="font-size:11.5px;color:var(--muted);margin:0 0 6px">Hình ảnh chỉ mang tính mô phỏng.</p>` : ''}
        ${simSummaryText ? `<p style="font-size:12.5px;color:var(--muted);margin-top:4px"><b>Bản phối mô phỏng:</b> ${esc(simSummaryText)}</p>` : ''}
        <hr>
        ${(orderData.display || []).map(i =>
            `<p style="display:flex;align-items:center;gap:10px;margin:8px 0">
                <img src="${esc(imgSrc(i.imageUrl))}" alt="" loading="lazy" onerror="this.onerror=null;this.src=PLACEHOLDER" style="width:44px;height:44px;border-radius:10px;object-fit:cover;flex:none">
                <span style="flex:1">${esc(i.name)} ×${i.quantity}</span>
                <span>${money(i.price * i.quantity)}</span></p>`).join('')}
        <hr>
        <p><b>Ngân sách gói</b> <b style="float:right">${money(orderData.budgetLevel || orderData.total)}</b></p>
        <p style="font-size:12.5px;color:var(--muted)"><span>Tạm tính nguyên liệu</span> <span style="float:right">${money(orderData.total)}</span></p>`;

    // 3. Hình thức đầu ra -> hiện/ẩn ô nhập tương ứng
    let outputType = 'ONLINE';
    let receive = 'DELIVERY';          // chỉ dùng khi outputType === 'DELIVERY': DELIVERY | STUDIO_PICKUP
    let paymentMethod = 'CASH';
    const STUDIO_PICKUP_HINT = 'Mộc Anh thực hiện sản phẩm theo thiết kế của bạn, gửi ảnh để bạn duyệt rồi chuẩn bị sẵn để bạn đến tiệm nhận.';
    const effectiveType = () => outputType === 'DELIVERY' ? receive : outputType;
    const needAddress = () => MODES[outputType].address && !(outputType === 'DELIVERY' && receive === 'STUDIO_PICKUP');
    const media = { image: null, video: null };
    let uploading = 0;
    let fees = null;   // { depositPercent, shipFee, holdMinutes } từ /orders/fees; lỗi thì chỉ không hiện thông báo phí
    fetch(`${API_BASE}/orders/fees`).then(r => r.ok ? r.json() : null).then(f => { fees = f; applyFeeHint(); }).catch(() => {});

    function applyFeeHint() {
        const el = document.querySelector('#feeHint');
        const t = effectiveType();
        let text = '';
        if (fees && t === 'DELIVERY' && Number(fees.shipFee) > 0) text = `Phí giao hàng ${money(fees.shipFee)} sẽ được cộng vào tổng thanh toán.`;
        if (fees && t === 'PICKUP') text = `Bạn đặt cọc ${fees.depositPercent}% giá trị đơn để giữ chỗ. Chỗ được giữ ${fees.holdMinutes} phút sau khi đặt, quá hạn mà chưa nhận được cọc thì khung giờ có thể nhường cho khách khác. Phần còn lại thanh toán tại tiệm.`;
        el.textContent = text; el.hidden = !text;
    }

    const fields = {
        time: { box: form.querySelector('#fieldTime'), input: form.elements.time },
        address: { box: form.querySelector('#fieldAddress'), input: form.elements.address },
        gift: { box: form.querySelector('#fieldGift'), input: form.elements.gift },
        card: { box: form.querySelector('#fieldCard'), input: form.elements.card }
    };

    // ----- Hình mô phỏng: nhận mọi định dạng ảnh, định dạng lạ thì vẽ lại thành JPEG trước khi tải lên -----
    const SIM_EXT = { 'image/jpeg': 'jpg', 'image/png': 'png', 'image/webp': 'webp' };
    function toJpeg(blob) {
        return new Promise((resolve, reject) => {
            const url = URL.createObjectURL(blob), img = new Image();
            img.onload = () => {
                const c = document.createElement('canvas');
                c.width = img.naturalWidth || 800; c.height = img.naturalHeight || 800;
                const ctx = c.getContext('2d'); ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, c.width, c.height); ctx.drawImage(img, 0, 0);
                URL.revokeObjectURL(url);
                c.toBlob(x => x ? resolve(x) : reject(new Error('Không chuyển được định dạng ảnh.')), 'image/jpeg', 0.85);
            };
            img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('Không đọc được ảnh.')); };
            img.src = url;
        });
    }
    async function uploadSimulation(src) {
        let blob = await (await fetch(src)).blob();
        let ext = SIM_EXT[blob.type];
        if (!ext) { blob = await toJpeg(blob); ext = 'jpg'; }
        const up = new FormData();
        up.append('file', blob, 'bouquet.' + ext);
        const ur = await fetch(`${API_BASE}/uploads/gift`, { method: 'POST', body: up });
        let ud = null; try { ud = await ur.json(); } catch (_) {}
        if (!ur.ok) throw new Error(ud?.message || `Lỗi máy chủ (${ur.status})`);
        if (ud.type !== 'image') throw new Error('Sai loại tệp.');
        return ud.url;
    }

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

    function applyBuyer() {
        const on = document.querySelector('#buyerOther').checked;
        document.querySelector('#buyerFields').hidden = !on;
        if (!on) { form.elements.buyerName.value = ''; form.elements.buyerPhone.value = ''; }
    }
    document.querySelector('#buyerOther').onchange = applyBuyer;

    // Mộc Anh làm giúp: giao tận nơi (cần địa chỉ) hoặc đến tiệm nhận (không cần địa chỉ)
    function applyReceive() {
        const box = document.querySelector('#receiveTabs');
        box.hidden = outputType !== 'DELIVERY';
        // Mộc Anh làm giúp: cho phép đặt hộ người khác (tách người đặt và người nhận)
        const studio = outputType === 'DELIVERY';
        document.querySelector('#buyerBox').hidden = !studio;
        document.querySelector('#nameLabel').textContent = studio ? 'Họ và tên người nhận' : 'Họ và tên';
        document.querySelector('#phoneLabel').textContent = studio ? 'Số điện thoại người nhận' : 'Số điện thoại';
        if (!studio) { document.querySelector('#buyerOther').checked = false; applyBuyer(); }
        applyFeeHint();
        const addr = needAddress();
        fields.address.box.hidden = !addr;
        fields.address.input.required = addr;
        if (!addr) fields.address.input.value = '';
        if (outputType === 'DELIVERY') {
            const pickup = receive === 'STUDIO_PICKUP';
            document.querySelector('#outputHint').textContent = pickup ? STUDIO_PICKUP_HINT : MODES.DELIVERY.hint;
            document.querySelector('#timeLabel').textContent = pickup ? 'Thời gian bạn đến nhận hoa' : MODES.DELIVERY.timeLabel;
        }
    }

    function applyMode(mode) {
        outputType = mode;
        const cfg = MODES[mode];
        document.querySelector('#outputHint').textContent = cfg.hint;
        ['time', 'address', 'gift', 'card'].forEach(k => {
            fields[k].box.hidden = !cfg[k];
            if (!cfg[k]) fields[k].input.value = '';
        });
        if (!cfg.gift) resetMedia();
        fields.time.input.required = cfg.time && mode !== 'PICKUP';
        fields.address.input.required = cfg.address;
        if (cfg.time) document.querySelector('#timeLabel').textContent = cfg.timeLabel;
        togglePicker(mode === 'PICKUP');
        applyReceive();
    }

    document.querySelectorAll('#receiveTabs button').forEach(btn => btn.onclick = () => {
        document.querySelectorAll('#receiveTabs button').forEach(b => b.classList.remove('selected'));
        btn.classList.add('selected');
        receive = btn.dataset.receive;
        applyReceive();
    });

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
        const cardMsg = MODES[outputType].card ? String(fd.get('card') || '').trim() : '';
        const voucher = String(fd.get('voucher') || '').trim();

        const buyerOn = outputType === 'DELIVERY' && document.querySelector('#buyerOther').checked;
        const buyerName = buyerOn ? String(fd.get('buyerName') || '').trim() : '';
        const buyerPhone = buyerOn ? String(fd.get('buyerPhone') || '').trim() : '';
        if (buyerOn && !buyerName) return showModal('Vui lòng nhập họ và tên người đặt.');
        if (buyerOn && !/^(0|\+84)\d{9,10}$/.test(buyerPhone.replace(/[\s.-]/g, ''))) return showModal('Số điện thoại người đặt chưa đúng định dạng.');
        if (!name) return showModal('Vui lòng nhập họ và tên.');
        if (!/^(0|\+84)\d{9,10}$/.test(phone.replace(/[\s.-]/g, ''))) return showModal('Số điện thoại chưa đúng định dạng.');
        if (MODES[outputType].time && !fd.get('time')) return showModal('Vui lòng chọn thời gian.');
        if (needAddress() && !address) return showModal('Vui lòng nhập địa chỉ nhận hàng.');

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
            deliveryType: effectiveType(),
            paymentMethod,
            receiverName: name,
            receiverPhone: phone,
            buyerName: buyerName || null,
            buyerPhone: buyerPhone || null,
            shippingAddress: needAddress() ? address : null,
            deliveryTime: MODES[outputType].time ? fd.get('time') : null,
            giftMessage: gift ? (giftMsg || null) : (cardMsg || null),   // ONLINE: lời chúc; Mộc Anh làm giúp: nội dung thiệp
            giftImageUrl: gift ? media.image : null,
            giftVideoUrl: gift ? media.video : null,
            voucherCode: voucher || null,
            items: orderData.items
        };

        submitBtn.disabled = true;
        const oldText = submitBtn.textContent;
        submitBtn.textContent = 'Đang gửi...';

        try {
            // Tải hình mô phỏng đã chốt ở Bước 5 lên cho MỌI loại đơn: trang quà (ONLINE) và nhân viên xem thiết kế đều dùng
            let bouquetImageUrl = null;
            if (simImg) {
                try { bouquetImageUrl = await uploadSimulation(simImg); }
                catch (err) {
                    await showModal('Chưa lưu được hình mô phỏng (' + err.message + '). Đơn của bạn vẫn được gửi, tiệm sẽ dựa vào danh sách nguyên liệu.',
                        { type: 'error', title: 'Hình mô phỏng' });
                }
            }
            payload.bouquetImageUrl = bouquetImageUrl;

            // Ảnh các mẫu khách đã chọn ở Bước 5 (tối đa 2): sao chép lên máy chủ để tiệm xem lại dù thư viện mẫu có thay đổi.
            // Mẫu nào tải lỗi thì bỏ qua, đơn vẫn gửi (tên mẫu vẫn nằm trong ghi chú đơn).
            const sampleImages = [];
            const picked = orderData.simulation && Array.isArray(orderData.simulation.sampleImages) ? orderData.simulation.sampleImages.slice(0, 2) : [];
            for (const s of picked) {
                try { sampleImages.push({ caption: s.caption || '', url: await uploadSimulation(s.url) }); } catch (_) { /* bỏ qua mẫu lỗi */ }
            }
            payload.sampleImages = sampleImages;

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
                bookingCode: data.bookingCode || null,
                deliveryType: effectiveType(),
                paymentMethod: data.paymentMethod,
                payableAmount: data.payableAmount,
                shippingFee: data.shippingFee,
                depositAmount: data.depositAmount,
                payNowAmount: data.payNowAmount,
                holdUntil: data.holdUntil,
                giftUrl: data.qrCodeUrl || null
            }));
            sessionStorage.removeItem('hoaOrder');
            try { localStorage.removeItem('hoaDesignState'); } catch (_) {}
            location.href = '/customer/thankyou.html?orderCode=' + encodeURIComponent(data.orderCode);
        } catch (err) {
            showModal(err.message, { type: 'error', title: 'Đặt hàng chưa thành công' });
            submitBtn.disabled = false;
            submitBtn.textContent = oldText;
        }
    });
});