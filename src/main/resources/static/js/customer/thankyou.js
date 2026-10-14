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

        let shareText = last.giftUrl;
        if (last.giftPassword) {
            getEl('#giftPasswordDisplay').hidden = false;
            getEl('#giftPasswordTxt').textContent = last.giftPassword;
            shareText = `Có một món quà từ Mộc Anh dành cho bạn!\nLink mở quà: ${last.giftUrl}\nMã bí mật: ${last.giftPassword}`;
        }

        // Sự kiện Copy cho nút "Sao chép link"
        getEl('#copyLink').onclick = async () => {
            try {
                await navigator.clipboard.writeText(shareText);
                getEl('#copyLink').textContent = 'Đã sao chép ✓';
            } catch (_) {
                window.prompt('Sao chép nội dung này:', shareText);
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

    if (last.paymentMethod === 'ONLINE') {
        getEl('#payTitle').textContent = 'Thanh toán online';
        const amount = Number(last.payableAmount || 0);
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
                    <p>Số tiền: <b>${money(amount)}</b></p>
                    <p>Nội dung: <b>${escHtml(code || '')}</b></p>
                </div>
                <p style="font-size:11px;color:var(--muted)">Vui lòng ghi đúng nội dung chuyển khoản. Tiệm sẽ xác nhận khi nhận được tiền.</p>`;
        } else {
            body.innerHTML = `<p>Số tiền cần thanh toán: <b>${money(amount)}</b></p>
                <p style="font-size:12px;color:var(--muted)">Nhân viên Mộc Anh sẽ liên hệ để hướng dẫn bạn thanh toán.</p>`;
        }
    } else {
        getEl('#payTitle').textContent = 'Thanh toán trực tiếp';
        body.innerHTML = `<p>Số tiền cần thanh toán: <b>${money(last.payableAmount)}</b></p>
            <p style="font-size:12px;color:var(--muted)">Bạn thanh toán trực tiếp khi nhận hoa hoặc khi đến tiệm.</p>`;
    }
});