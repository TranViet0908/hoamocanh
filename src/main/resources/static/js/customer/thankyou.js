const money = n => Number(n || 0).toLocaleString('vi-VN') + 'đ';
const escHtml = s => String(s ?? '').replace(/[&<>"']/g,
    c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

document.addEventListener('DOMContentLoaded', async () => {
    const $ = s => document.querySelector(s);
    const code = new URLSearchParams(location.search).get('orderCode');
    let last = null;
    try { last = JSON.parse(sessionStorage.getItem('lastOrder') || 'null'); } catch (_) {}
    // chỉ dùng dữ liệu đã lưu nếu đúng đơn đang xem
    if (last && code && last.orderCode !== code) last = null;

    if (code) { $('#orderCode').textContent = code; $('#orderLine').hidden = false; }

    const shop = await loadShopInfo();
    renderContact($('#contact'), shop);

    if (!last) return;

    // ----- Gửi tặng online: QR + link -----
    if (last.deliveryType === 'ONLINE' && last.giftUrl) {
        $('#giftBox').hidden = false;
        $('#giftLink').textContent = last.giftUrl;
        $('#openGift').href = last.giftUrl;

        if (window.QRCode) {
            // mức sửa lỗi H để vẫn quét được khi có logo ở giữa
            new QRCode($('#qrCanvas'), { text: last.giftUrl, width: 200, height: 200, correctLevel: QRCode.CorrectLevel.H });
        } else {
            $('#qrWrap').hidden = true;   // không tải được thư viện QR (mất mạng): vẫn còn link bên dưới
        }

        $('#copyLink').onclick = async () => {
            try { await navigator.clipboard.writeText(last.giftUrl); $('#copyLink').textContent = 'Đã sao chép ✓'; }
            catch (_) { window.prompt('Sao chép link này:', last.giftUrl); }
        };
        if (navigator.share) {
            $('#shareLink').hidden = false;
            $('#shareLink').onclick = () => navigator.share({ title: 'Một món quà từ Mộc Anh', url: last.giftUrl }).catch(() => {});
        }
    }

    // ----- Thanh toán -----
    const payBox = $('#payBox'), body = $('#payBody');
    payBox.hidden = false;

    if (last.paymentMethod === 'ONLINE') {
        $('#payTitle').textContent = 'Thanh toán online';
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
        $('#payTitle').textContent = 'Thanh toán trực tiếp';
        body.innerHTML = `<p>Số tiền cần thanh toán: <b>${money(last.payableAmount)}</b></p>
            <p style="font-size:12px;color:var(--muted)">Bạn thanh toán trực tiếp khi nhận hoa hoặc khi đến tiệm.</p>`;
    }
});