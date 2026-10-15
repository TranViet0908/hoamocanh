const money = n => Number(n || 0).toLocaleString('vi-VN') + 'đ';
const STATUS = {
    PENDING: 'Chờ xác nhận', CONFIRMED: 'Đã xác nhận', PREPARING: 'Đang chuẩn bị nguyên liệu',
    IN_PROGRESS: 'Đang thực hiện', PROCESSING: 'Đang thực hiện', AWAITING_APPROVAL: 'Chờ bạn duyệt ảnh',
    READY: 'Sẵn sàng giao/nhận', COMPLETED: 'Hoàn thành', DELIVERED: 'Hoàn thành', CANCELLED: 'Đã hủy'
};
const PAY = { UNPAID: 'Chưa thanh toán', DEPOSITED: 'Đã đặt cọc', PAID: 'Đã thanh toán' };

document.addEventListener('DOMContentLoaded', () => {
    const $ = s => document.querySelector(s);
    const form = $('#lookupForm'), err = $('#lookupError'), box = $('#result');
    let ctx = null;   // mã đơn + số điện thoại vừa tra, dùng khi khách duyệt ảnh
    const params = new URLSearchParams(location.search);
    if (params.get('orderCode')) form.elements.code.value = params.get('orderCode');

    form.addEventListener('submit', async e => {
        e.preventDefault();
        err.hidden = true; box.hidden = true;
        const btn = form.querySelector('button[type="submit"]');
        btn.disabled = true;
        try {
            const q = new URLSearchParams({ orderCode: form.elements.code.value.trim(), phone: form.elements.phone.value.trim() });
            const res = await fetch('/customer/orders/lookup?' + q);
            let d = null; try { d = await res.json(); } catch (_) {}
            if (!res.ok) throw new Error(d?.message || `Lỗi máy chủ (${res.status})`);
            ctx = { orderCode: form.elements.code.value.trim(), phone: form.elements.phone.value.trim() };
            render(d);
        } catch (ex) {
            err.textContent = ex.message; err.hidden = false;
        } finally { btn.disabled = false; }
    });

    async function review(approve) {
        if (!ctx) return;
        const note = $('#reviewNote').value.trim();
        if (!approve && !note) { err.textContent = 'Vui lòng cho tiệm biết bạn muốn chỉnh sửa điều gì.'; err.hidden = false; return; }
        err.hidden = true;
        const btns = [$('#approveBtn'), $('#reviseBtn')]; btns.forEach(b => b.disabled = true);
        try {
            const res = await fetch('/customer/orders/review', { method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ ...ctx, approve, note: note || null }) });
            let d = null; try { d = await res.json(); } catch (_) {}
            if (!res.ok) throw new Error(d?.message || `Lỗi máy chủ (${res.status})`);
            render(d);
        } catch (ex) { err.textContent = ex.message; err.hidden = false; }
        finally { btns.forEach(b => b.disabled = false); }
    }
    $('#approveBtn').onclick = () => review(true);
    $('#reviseBtn').onclick = () => review(false);

    function render(d) {
        box.hidden = false;
        $('#resCode').textContent = 'Mã đơn ' + d.orderCode;
        $('#resStatus').textContent = 'Trạng thái: ' + (STATUS[d.status] || d.status);
        const dep = Number(d.depositAmount || 0);
        $('#resPay').textContent = `${PAY[d.paymentStatus] || d.paymentStatus} · Tổng ${money(d.payableAmount)}`
            + (dep > 0 ? ` · Tiền cọc ${money(dep)}` : '')
            + (Number(d.shippingFee || 0) > 0 ? ` · Gồm phí giao ${money(d.shippingFee)}` : '');
        if (d.deliveryTime) {
            const t = new Date(d.deliveryTime);
            $('#resTime').textContent = 'Thời gian hẹn: ' + (isNaN(t) ? d.deliveryTime : t.toLocaleString('vi-VN'));
            $('#resTime').hidden = false;
        } else $('#resTime').hidden = true;

        // Ảnh thành phẩm: khách duyệt khi đơn đang chờ duyệt
        const okPhoto = typeof d.finishedImageUrl === 'string' && d.finishedImageUrl.startsWith('/uploads/product/') && !d.finishedImageUrl.includes('..');
        $('#photoBlock').hidden = !okPhoto;
        if (okPhoto) $('#photoImg').src = d.finishedImageUrl;
        $('#reviewBox').hidden = !(okPhoto && d.status === 'AWAITING_APPROVAL');
        $('#reviewNote').value = '';

        const g = $('#giftBlock');
        g.hidden = !d.giftUrl;
        $('#qrCanvas').innerHTML = '';
        if (d.giftUrl) {
            $('#giftLink').textContent = d.giftUrl;
            $('#openGift').href = d.giftUrl;
            if (window.QRCode) {
                $('#qrWrap').hidden = false;
                new QRCode($('#qrCanvas'), { text: d.giftUrl, width: 200, height: 200, correctLevel: QRCode.CorrectLevel.H });
            } else $('#qrWrap').hidden = true;
            $('#copyLink').onclick = async () => {
                try { await navigator.clipboard.writeText(d.giftUrl); $('#copyLink').textContent = 'Đã sao chép ✓'; }
                catch (_) { window.prompt('Sao chép link này:', d.giftUrl); }
            };
        }
    }
});