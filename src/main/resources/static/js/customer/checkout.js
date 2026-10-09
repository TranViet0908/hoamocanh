const API_BASE = '/customer';

document.addEventListener('DOMContentLoaded', () => {
    const form = document.querySelector('#checkoutForm');

    form?.addEventListener('submit', async e => {
        e.preventDefault();

        // 1. Lấy dữ liệu giỏ hàng đã lưu từ trang design.js
        const orderDataStr = sessionStorage.getItem('hoaOrder');
        if (!orderDataStr) return alert("Không tìm thấy dữ liệu giỏ hàng!");
        const orderData = JSON.parse(orderDataStr);

        // 2. Lấy hình thức đầu ra từ UI (Gửi tặng online, Tự làm tại tiệm, Mộc Anh làm giúp)
        const activeTab = document.querySelector('.output-tabs button.selected').innerText.trim();
        let outputType = 'ONLINE';
        if(activeTab.includes('tại tiệm')) outputType = 'AT_STORE';
        if(activeTab.includes('Mộc Anh')) outputType = 'BY_MOCANH';

        const formData = new FormData(form);

        // 3. Khớp định dạng với class CheckoutReq.java ở Spring Boot
        const payload = {
            customerName: formData.get('name'),
            customerPhone: formData.get('phone'),
            productType: orderData.productType,
            budgetLevel: orderData.budgetLevel,
            outputType: outputType,
            deliveryAddress: formData.get('address'),
            giftMessage: formData.get('note'),
            items: orderData.items // Đã chuẩn hóa danh sách {materialId, quantity} từ bước trước
        };

        try {
            const res = await fetch(`${API_BASE}/orders/checkout`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (res.ok) {
                const result = await res.json();
                sessionStorage.removeItem('hoaOrder');
                location.href = 'thankyou.html?orderCode=' + result.orderCode;
            } else {
                const err = await res.json();
                alert("Lỗi đặt hàng: " + err.message);
            }
        } catch (error) {
            alert("Lỗi kết nối máy chủ Spring Boot!");
        }
    });

    // Lắng nghe đổi Tab hình thức giao hàng
    document.querySelectorAll('.output-tabs button').forEach(btn => {
        btn.onclick = () => {
            document.querySelectorAll('.output-tabs button').forEach(b => b.classList.remove('selected'));
            btn.classList.add('selected');
        };
    });
});