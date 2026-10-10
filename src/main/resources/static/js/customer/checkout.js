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

    // 2. Tóm tắt đơn
    document.querySelector('#orderSummary').innerHTML = `
        <p><b>${esc(orderData.productTypeName || '')}</b> · ${esc([orderData.style, orderData.themeColor, orderData.occasion].filter(Boolean).join(' · '))}</p>
        <hr>
        ${(orderData.display || []).map(i =>
            `<p>${esc(i.name)} × ${i.quantity} <span style="float:right">${money(i.price * i.quantity)}</span></p>`).join('')}
        <hr>
        <p><b>Tạm tính</b> <b style="float:right">${money(orderData.total)}</b></p>`;

    // 3. Hình thức đầu ra -> hiện/ẩn ô nhập tương ứng
    let outputType = 'ONLINE';
    let paymentMethod = 'CASH';
    const media = { image: null, video: null };      // URL sau khi upload
    let uploading = 0;

    const fields = {
        time: { box: form.querySelector('#fieldTime'), input: form.elements.time },
        address: { box: form.querySelector('#fieldAddress'), input: form.elements.address },
        gift: { box: form.querySelector('#fieldGift'), input: form.elements.gift }
    };

    function applyMode(mode) {
        outputType = mode;
        const cfg = MODES[mode];
        document.querySelector('#outputHint').textContent = cfg.hint;
        ['time', 'address', 'gift'].forEach(k => {
            fields[k].box.hidden = !cfg[k];
            if (!cfg[k]) fields[k].input.value = '';
        });
        if (!cfg.gift) resetMedia();
        fields.time.input.required = cfg.time;
        fields.address.input.required = cfg.address;
        if (cfg.time) document.querySelector('#timeLabel').textContent = cfg.timeLabel;
    }

    // Không cho chọn thời điểm trong quá khứ
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

    // 5. Upload ảnh/video quà tặng (tải lên ngay khi chọn file)
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
        const name = fd.get('name').trim();
        const phone = fd.get('phone').trim();

        if (!name) return showModal('Vui lòng nhập họ và tên.');
        if (!/^(0|\+84)\d{9,10}$/.test(phone.replace(/[\s.-]/g, ''))) return showModal('Số điện thoại chưa đúng định dạng.');
        if (MODES[outputType].time && !fd.get('time')) return showModal('Vui lòng chọn thời gian.');
        if (MODES[outputType].address && !fd.get('address').trim()) return showModal('Vui lòng nhập địa chỉ nhận hàng.');

        const gift = MODES[outputType].gift;
        const payload = {
            productTypeId: orderData.productTypeId,
            budgetLevel: orderData.budgetLevel,
            style: orderData.style,
            themeColor: orderData.themeColor || null,
            // CheckoutReq chưa có trường "mục đích tặng" riêng nên ghép vào styleNote
            styleNote: [orderData.occasion ? 'Mục đích tặng: ' + orderData.occasion : '', fd.get('note').trim()]
                .filter(Boolean).join('\n') || null,
            deliveryType: outputType,
            paymentMethod,
            receiverName: name,
            receiverPhone: phone,
            shippingAddress: MODES[outputType].address ? fd.get('address').trim() : null,
            deliveryTime: MODES[outputType].time ? fd.get('time') : null,      // "2026-10-12T15:30"
            giftMessage: gift ? (fd.get('gift').trim() || null) : null,
            giftImageUrl: gift ? media.image : null,
            giftVideoUrl: gift ? media.video : null,
            voucherCode: fd.get('voucher').trim() || null,
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

            // OrderCheckoutRes: orderCode, paymentMethod, payableAmount, qrCodeUrl
            sessionStorage.setItem('lastOrder', JSON.stringify({
                orderCode: data.orderCode,
                deliveryType: outputType,
                paymentMethod: data.paymentMethod,
                payableAmount: data.payableAmount,
                giftUrl: data.qrCodeUrl || null
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