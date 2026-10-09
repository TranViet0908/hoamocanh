const API_BASE = '/customer';
let productType = 2; // Mặc định là Bó hoa (ID = 2 trong DB)
let budget = 700000, step = 1, filter = 'all';

const productTypes = [
    { id: 1, name: 'Mix thả bình' }, { id: 2, name: 'Bó hoa' },
    { id: 3, name: 'Giỏ hoa' }, { id: 4, name: 'Box hoa' },
    { id: 5, name: 'Túi hoa' }, { id: 6, name: 'Kệ hoa' }
];

const materials = [
    { id: 1, name: 'Hoa Hồng Ecuador Đỏ', type: 'flower', price: 35000 },
    { id: 2, name: 'Hoa Hướng Dương', type: 'flower', price: 20000 },
    { id: 3, name: 'Hoa Tulip Vàng', type: 'flower', price: 45000 },
    { id: 5, name: 'Hoa Baby Trắng', type: 'flower', price: 15000 },
    { id: 6, name: 'Lá Bạc', type: 'flower', price: 8000 },
    { id: 9, name: 'Ruy băng lụa đỏ', type: 'accessory', price: 5000 },
    { id: 7, name: 'Giấy gói Kraft', type: 'packaging', price: 15000 },
    { id: 8, name: 'Giấy gói Mika', type: 'packaging', price: 25000 }
];
let quantities = Array(materials.length).fill(0);

// Helper
const $ = s => document.querySelector(s);
const money = n => n.toLocaleString('vi-VN') + 'đ';

function renderMaterials() {
    let list = materials.map((m, i) => ({ ...m, i })).filter(m => filter === 'all' || m.type === filter);
    $('#materials').innerHTML = list.map(m => `<article class="material"><div class="material-info"><strong>${m.name}</strong><small>${m.type === 'flower' ? 'Hoa & lá' : m.type === 'accessory' ? 'Phụ kiện' : 'Bao bì'}</small><span class="material-price">${money(m.price)}</span></div><div class="quantity"><button data-a="minus" data-i="${m.i}">−</button><b>${quantities[m.i]}</b><button data-a="plus" data-i="${m.i}">+</button></div></article>`).join('');
    updateCart();
}

function updateCart() {
    const total = quantities.reduce((s, q, i) => s + q * materials[i].price, 0);
    $('#cartCount').textContent = quantities.reduce((s, q) => s + q, 0);
    $('#cartTotal').textContent = money(total);
    $('#budgetMessage').textContent = total > budget ? `Vượt ${money(total - budget)}` : `Còn ${money(budget - total)} trong ngân sách`;
    return total;
}

function goTo(n) {
    step = n;
    document.querySelectorAll('.step').forEach(x => x.classList.toggle('active', +x.dataset.step === n));
    $('#stepNumber').textContent = String(n).padStart(2, '0');
    $('#progressBar').style.width = n / 6 * 100 + '%';
    window.scrollTo({ top: document.querySelector('.flow').offsetTop, behavior: 'smooth' });
}

document.addEventListener('DOMContentLoaded', () => {
    const grid = $('#productGrid');
    if (grid) {
        grid.innerHTML = productTypes.map((p, i) => `
            <article class="product-card ${p.id === productType ? 'selected' : ''}" data-i="${i}">
                <div><strong>${p.name}</strong><small>Bắt đầu từ 400.000đ</small></div>
            </article>
        `).join('');
    }

    renderMaterials();

    // Nút chuyển bước (type="button" trong HTML nên không submit form)
    document.querySelectorAll('.next-step').forEach(b => b.onclick = () => goTo(Math.min(6, step + 1)));
    document.querySelectorAll('.prev-step').forEach(b => b.onclick = () => goTo(Math.max(1, step - 1)));

    // Bước 1: chọn kiểu dáng
    if (grid) {
        grid.onclick = e => {
            const c = e.target.closest('.product-card');
            if (c) {
                document.querySelectorAll('.product-card').forEach(x => x.classList.remove('selected'));
                c.classList.add('selected');
                productType = +c.dataset.i + 1;
            }
        };
    }

    // Bước 3: chọn phong cách (chọn 1)
    document.querySelectorAll('.choice').forEach(b => b.onclick = () => {
        document.querySelectorAll('.choice').forEach(x => x.classList.remove('selected'));
        b.classList.add('selected');
    });

    // Bước 4: lọc nguyên liệu
    document.querySelectorAll('.material-tabs button').forEach(b => b.onclick = () => {
        document.querySelectorAll('.material-tabs button').forEach(x => x.classList.remove('selected'));
        b.classList.add('selected');
        filter = b.dataset.filter;
        renderMaterials();
    });

    // Bước 4: tăng/giảm số lượng
    $('#materials').onclick = e => {
        const b = e.target.closest('button');
        if (b) {
            const i = +b.dataset.i;
            quantities[i] = Math.max(0, quantities[i] + (b.dataset.a === 'plus' ? 1 : -1));
            renderMaterials();
        }
    };

    // Bước 2: ngân sách
    $('#budget').oninput = e => {
        budget = +e.target.value;
        $('#budgetText').textContent = money(budget);
        updateCart();
    };

    // Bước 4: kiểm tra ngân sách với server
    $('#validate').onclick = async () => {
        const items = materials.map((m, i) => ({ materialId: m.id, quantity: quantities[i] })).filter(item => item.quantity > 0);
        try {
            const res = await fetch(`${API_BASE}/design/validate?productType=${productType}&budgetLevel=${budget}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(items)
            });
            const data = await res.json();
            alert(res.ok ? "Tuyệt vời! Thiết kế đang trong ngân sách." : "Lỗi: " + data.message);
        } catch (e) {
            alert("Lỗi kết nối máy chủ Spring Boot!");
        }
    };

    // Bước 6: chuyển sang trang chốt đơn
    const checkoutForm = $('#checkoutForm');
    if (checkoutForm) {
        checkoutForm.onsubmit = e => {
            e.preventDefault();
            const items = materials.map((m, i) => ({ materialId: m.id, quantity: quantities[i] })).filter(item => item.quantity > 0);

            if (items.length === 0) return alert("Bạn chưa chọn hoa nào!");

            sessionStorage.setItem('hoaOrder', JSON.stringify({
                budgetLevel: budget, productType: productType, items: items
            }));
            location.href = 'checkout.html';
        };
    }
});