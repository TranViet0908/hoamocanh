const money = n => Number(n || 0).toLocaleString('vi-VN') + 'đ';
const escHtml = s => String(s ?? '').replace(/[&<>"']/g,
    c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

document.addEventListener('DOMContentLoaded', async () => {
    // Đã đổi $ thành getEl để tránh lỗi IDE tự động thêm gạch chéo
    const getEl = s => document.querySelector(s);
    const code = new URLSearchParams(location.search).get('orderCode');
    let last = null;
    try { last = JSON.parse(sessionStorage.getItem('lastOrder') || 'null'); } catch (_) {}

    // Chỉ dùng dữ liệu đã lưu nếu đúng đơn đang xem
    if (last && code && last.orderCode !== code) last = null;

    if (code) { getEl('#orderCode').textContent = code; getEl('#orderLine').hidden = false; }

    const shop = await loadShopInfo();
    renderContact(getEl('#contact'), shop);

    if (!last) return;

    // ----- Gửi tặng online: QR + link -----
    if (last.deliveryType === 'ONLINE' && last.giftUrl) {
        getEl('#giftBox').hidden = false;
        getEl('#giftLink').textContent = last.giftUrl;
        getEl('#openGift').href = last.giftUrl; // Nút "Xem trang quà" tự động trỏ link

        if (window.QRCode) {
            new QRCode(getEl('#qrCanvas'), { text: last.giftUrl, width: 200, height: 200, correctLevel: QRCode.CorrectLevel.H });
        } else {
            getEl('#qrWrap').hidden = true;
        }

        // Sự kiện Copy cho nút "Sao chép link"
        getEl('#copyLink').onclick = async () => {
            try {
                await navigator.clipboard.writeText(last.giftUrl);
                getEl('#copyLink').textContent = 'Đã sao chép ✓';
            } catch (_) {
                window.prompt('Sao chép link này:', last.giftUrl);
            }
        };

        if (navigator.share) {
            getEl('#shareLink').hidden = false;
            getEl('#shareLink').onclick = () => navigator.share({ title: 'Một món quà từ Mộc Anh', url: last.giftUrl }).catch(() => {});
        }
    }

    // ----- Thanh toán -----
    const payBox = getEl('#payBox'), body = getEl('#payBody');
    payBox.hidden = false;

    const deposit = Number(last.depositAmount || 0);      // > 0: đơn tự làm tại tiệm, phải cọc để giữ chỗ
    const ship = Number(last.shippingFee || 0);
    const total = Number(last.payableAmount || 0);
    const amount = Number(last.payNowAmount ?? last.payableAmount ?? 0);
    const fmtTime = v => { const d = new Date(v); return isNaN(d) ? '' : d.toLocaleString('vi-VN', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit' }); };

    // Chuyển khoản: đơn chọn thanh toán online, hoặc đơn PICKUP (cọc luôn chuyển khoản để giữ chỗ)
    const viaBank = deposit > 0 || last.paymentMethod === 'ONLINE';
    getEl('#payTitle').textContent = deposit > 0 ? 'Đặt cọc giữ chỗ' : (viaBank ? 'Thanh toán online' : 'Thanh toán trực tiếp');

    const extra = [
        ship > 0 ? `<p style="font-size:12px;color:var(--muted)">Đã gồm phí giao hàng ${money(ship)}.</p>` : '',
        deposit > 0 ? `<p style="font-size:12px;color:var(--muted)">Tổng giá trị đơn ${money(total)}. Phần còn lại ${money(total - deposit)} thanh toán tại tiệm.</p>` : '',
        deposit > 0 && last.holdUntil ? `<p style="font-size:12px;color:var(--muted)">Chỗ được giữ đến ${escHtml(fmtTime(last.holdUntil))}. Quá hạn mà tiệm chưa nhận được cọc, khung giờ có thể nhường cho khách khác.</p>` : ''
    ].join('');
    const amountLabel = deposit > 0 ? 'Tiền cọc' : 'Số tiền';

    if (viaBank) {
        const hasBank = shop.bankCode && shop.bankAccount;
        if (hasBank) {
            const qr = `https://img.vietqr.io/image/${encodeURIComponent(shop.bankCode)}-${encodeURIComponent(shop.bankAccount)}-compact2.png`
                + `?amount=${amount}&addInfo=${encodeURIComponent(code || '')}&accountName=${encodeURIComponent(shop.bankHolder || '')}`;
            body.innerHTML = `
                <img src="${escHtml(qr)}" alt="Mã QR chuyển khoản" onerror="this.style.display='none'">
                <div class="mt-bank">
                    <p>Ngân hàng: <b>${escHtml(shop.bankCode)}</b></p>
                    <p>Số tài khoản: <b>${escHtml(shop.bankAccount)}</b></p>
                    ${shop.bankHolder ? `<p>Chủ tài khoản: <b>${escHtml(shop.bankHolder)}</b></p>` : ''}
                    <p>${amountLabel}: <b>${money(amount)}</b></p>
                    <p>Nội dung: <b>${escHtml(code || '')}</b></p>
                </div>
                ${extra}
                <p style="font-size:11px;color:var(--muted)">Vui lòng ghi đúng nội dung chuyển khoản. Tiệm sẽ xác nhận khi nhận được tiền.</p>`;
        } else {
            body.innerHTML = `<p>${amountLabel} cần thanh toán: <b>${money(amount)}</b></p>${extra}
                <p style="font-size:12px;color:var(--muted)">Nhân viên Mộc Anh sẽ liên hệ để hướng dẫn bạn thanh toán.</p>`;
        }
    } else {
        body.innerHTML = `<p>Số tiền cần thanh toán: <b>${money(amount)}</b></p>${extra}
            <p style="font-size:12px;color:var(--muted)">Bạn thanh toán trực tiếp khi nhận hoa hoặc khi đến tiệm.</p>`;
    }
});