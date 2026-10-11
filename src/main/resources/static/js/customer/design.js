const API_BASE = '/customer';
const BUDGET_MIN = 400000, BUDGET_MAX = 1500000, BUDGET_STEP = 100000;

// ===== Helpers =====
const $ = s => document.querySelector(s);
const $$ = s => document.querySelectorAll(s);
const money = n => Number(n || 0).toLocaleString('vi-VN') + 'đ';
const esc = s => String(s ?? '').replace(/[&<>"']/g,
    c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

const PLACEHOLDER = 'data:image/svg+xml;utf8,' + encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96"><rect width="100%" height="100%" fill="#f3ece4"/>' +
    '<text x="50%" y="58%" font-size="36" text-anchor="middle">🌸</text></svg>');
// DB có thể lưu "images/a.jpg" hay "/images/a.jpg" hay URL đầy đủ
const imgSrc = u => !u ? PLACEHOLDER : (/^(https?:|\/|data:)/.test(u) ? u : '/' + u);
// Ảnh lỗi/404 -> ảnh dự phòng
document.addEventListener('error', e => {
    if (e.target.tagName === 'IMG' && e.target.classList.contains('mt-img')) {
        e.target.classList.remove('mt-img');
        e.target.src = PLACEHOLDER;
    }
}, true);

async function api(path, options = {}) {
    const res = await fetch(API_BASE + path, { headers: { 'Content-Type': 'application/json' }, ...options });
    let data = null;
    try { data = await res.json(); } catch (_) { /* body rỗng */ }
    if (!res.ok) throw new Error(data?.message || `Lỗi máy chủ (${res.status})`);
    return data;
}

// ===== Nhóm nguyên liệu =====
// Nhóm của từng nguyên liệu do BACKEND quyết định (cột categories.design_group -> MaterialDto.designGroup).
// Thêm danh mục mới: chỉ cần gán design_group trong DB, không phải sửa file này.
// Muốn đổi nhãn hiển thị hoặc nhóm nào nằm ở tab nào thì sửa tại đây.
const GROUPS = {
    main:      { label: 'Hoa chính',    tab: 'flower',    limit: 'maxMainFlowers' },
    sub:       { label: 'Hoa phụ',      tab: 'flower',    limit: 'maxSubFlowers' },
    leaf:      { label: 'Lá trang trí', tab: 'flower',    limit: 'maxLeaves' },
    packaging: { label: 'Bao bì',       tab: 'packaging', limit: 'maxPackaging' },
    accessory: { label: 'Phụ kiện',     tab: 'accessory', limit: 'maxAccessories' },
    other:     { label: 'Khác',         tab: 'accessory', limit: null }
};

// ===== State =====
const state = {
    step: 1,
    filter: 'all',
    group: null,            // lọc theo nhóm (bấm nhãn Hoa chính/Hoa phụ...), null = không lọc
    budget: 700000,
    style: 'Dịu dàng',
    themeColor: null,       // tone màu (tùy chọn)
    occasion: null,         // mục đích tặng (tùy chọn)
    productTypes: [],
    productType: null,      // {id, name, minPrice}
    materials: [],          // từ server (đã chuẩn hóa)
    limits: null,           // định mức theo mức giá, null nếu chưa có
    quantities: new Map()   // materialId -> số lượng
};
let loadToken = 0;

// ===== Chuẩn hóa dữ liệu =====
function normType(p) {
    return {
        id: p.id,
        name: p.name ?? `Loại ${p.id}`,
        minPrice: Number(p.minBudget ?? BUDGET_MIN)
    };
}

// MaterialDto: id, categoryId, categoryName, designGroup, name, price, imageUrl, stock
function normMaterial(m) {
    return {
        id: m.id,
        group: GROUPS[String(m.designGroup || '').toLowerCase()] ? String(m.designGroup).toLowerCase() : 'other',
        categoryName: m.categoryName || '',
        name: m.name,
        price: Number(m.price ?? 0),
        imageUrl: m.imageUrl || null,
        stock: m.stock == null ? null : Number(m.stock)
    };
}

// ===== Tính toán =====
const qtyOf = id => state.quantities.get(id) || 0;

function cartItems() {
    return state.materials
        .filter(m => qtyOf(m.id) > 0)
        .map(m => ({ materialId: m.id, quantity: qtyOf(m.id), name: m.name, price: m.price, imageUrl: m.imageUrl, group: m.group }));
}
const cartTotal = () => cartItems().reduce((s, i) => s + i.price * i.quantity, 0);

const groupCount = g => cartItems().filter(i => i.group === g).reduce((s, i) => s + i.quantity, 0);
const groupLimit = g => {
    const key = GROUPS[g].limit;
    return key && state.limits && state.limits[key] != null ? Number(state.limits[key]) : null;
};

// Số lượng tối đa còn có thể chọn thêm cho một nguyên liệu (theo kho + định mức nhóm)
function maxQty(m) {
    let max = m.stock ?? Infinity;
    const lim = groupLimit(m.group);
    if (lim !== null) max = Math.min(max, qtyOf(m.id) + Math.max(0, lim - groupCount(m.group)));
    return max;
}

// ===== Render =====
function renderProductTypes() {
    const grid = $('#productGrid');
    if (!state.productTypes.length) {
        grid.innerHTML = '<p>Chưa có loại sản phẩm nào đang mở bán.</p>';
        return;
    }
    grid.innerHTML = state.productTypes.map(p => `
        <article class="product-card ${state.productType?.id === p.id ? 'selected' : ''}" data-id="${esc(p.id)}">
            <div><strong>${esc(p.name)}</strong><small>Bắt đầu từ ${money(p.minPrice)}</small></div>
        </article>`).join('');
}

function renderLimits() {
    const bar = $('#limitBar');
    if (!state.limits) { bar.innerHTML = ''; return; }
    bar.innerHTML = ['main', 'sub', 'leaf', 'packaging', 'accessory'].map(g => {
        const lim = groupLimit(g), n = groupCount(g);
        if (lim === null || (lim === 0 && n === 0)) return '';
        const cls = n > lim ? 'over' : n === lim ? 'full' : '';
        const on = state.group === g ? 'active' : '';
        return `<button type="button" class="mt-chip ${cls} ${on}" data-group="${g}" aria-pressed="${!!on}">${GROUPS[g].label}: <b>${n}/${lim}</b></button>`;
    }).join('');
}

function renderMaterials() {
    const box = $('#materials');
    const list = state.materials.filter(m => (state.filter === 'all' || GROUPS[m.group].tab === state.filter)
        && (!state.group || m.group === state.group));

    if (!list.length) {
        box.innerHTML = '<p style="color:var(--muted)">Hiện chưa có nguyên liệu phù hợp với lựa chọn của bạn.</p>';
    } else {
        box.innerHTML = list.map(m => {
            const q = qtyOf(m.id);
            const low = m.stock !== null && m.stock <= 5;
            const canAdd = q < maxQty(m);
            return `
            <article class="mt-card ${q ? 'has-qty' : ''}">
                <img class="mt-thumb mt-img" src="${esc(imgSrc(m.imageUrl))}" alt="${esc(m.name)}" loading="lazy">
                <div class="mt-info">
                    <strong>${esc(m.name)}</strong>
                    <span class="mt-group">${esc(m.categoryName || GROUPS[m.group].label)}</span>
                    <span class="mt-price">${money(m.price)}</span>
                    ${m.stock !== null ? `<span class="mt-stock ${low ? 'low' : ''}">${low ? 'Sắp hết · ' : ''}Còn ${m.stock} trong kho</span>` : ''}
                </div>
                <div class="mt-qty">
                    <button type="button" data-a="minus" data-id="${esc(m.id)}" ${q ? '' : 'disabled'} aria-label="Giảm">−</button>
                    <b>${q}</b>
                    <button type="button" data-a="plus" data-id="${esc(m.id)}" ${canAdd ? '' : 'disabled'} aria-label="Tăng">+</button>
                </div>
            </article>`;
        }).join('');
    }
    updateCart();
}

function updateCart() {
    const items = cartItems();
    const total = cartTotal();
    const msg = $('#budgetMessage');
    const raise = $('#raiseBudget');

    $('#cartCount').textContent = items.reduce((s, i) => s + i.quantity, 0);
    $('#cartTotal').textContent = money(total);

    if (total > state.budget) {
        msg.textContent = `Vượt ${money(total - state.budget)}`;
        msg.classList.add('mt-over');
        const next = state.budget + BUDGET_STEP;
        raise.hidden = next > BUDGET_MAX;
        raise.textContent = `Nâng lên ${money(next)}`;
    } else {
        msg.textContent = `Còn ${money(state.budget - total)} trong ngân sách`;
        msg.classList.remove('mt-over');
        raise.hidden = true;
    }

    $('#cartThumbs').innerHTML = items.slice(0, 8)
        .map(i => `<img class="mt-img" src="${esc(imgSrc(i.imageUrl))}" alt="${esc(i.name)}" title="${esc(i.name)} × ${i.quantity}">`).join('');
    renderLimits();
}

function renderSummary() {
    const items = cartItems();
    const total = cartTotal();

    // Moodboard: ghép ảnh nguyên liệu thật; không có ảnh nào thì dùng ảnh mẫu
    const withImg = items.filter(i => i.imageUrl);
    const collage = $('#collage');
    collage.hidden = !withImg.length;
    $('#moodFallback').hidden = !!withImg.length;
    collage.innerHTML = withImg.map(i =>
        `<div class="mt-tile"><img class="mt-img" src="${esc(imgSrc(i.imageUrl))}" alt="${esc(i.name)}"><span>× ${i.quantity}</span></div>`).join('');
    $('#moodItems').textContent = items.map(i => i.name).join(' · ');

    $('#designSummary').innerHTML = `
        <div class="mt-summary">
            <p><b>Loại sản phẩm:</b> ${esc(state.productType?.name ?? '—')}</p>
            <p><b>Ngân sách:</b> ${money(state.budget)}</p>
            <p><b>Phong cách:</b> ${esc(state.style)}</p>
            ${state.themeColor ? `<p><b>Tone màu:</b> ${esc(state.themeColor)}</p>` : ''}
            ${state.occasion ? `<p><b>Mục đích tặng:</b> ${esc(state.occasion)}</p>` : ''}
            <hr>
            ${items.map(i => `
                <div class="mt-sum-row">
                    <img class="mt-img" src="${esc(imgSrc(i.imageUrl))}" alt="">
                    <span>${esc(i.name)} × ${i.quantity}</span>
                    <span>${money(i.price * i.quantity)}</span>
                </div>`).join('')}
            <hr>
            <p><b>Tạm tính</b> <b style="float:right">${money(total)}</b></p>
        </div>`;
}

// ===== Thanh ngân sách: không thấp hơn mức tối thiểu của loại sản phẩm =====
function applyBudgetFloor() {
    const slider = $('#budget');
    const floor = Math.min(BUDGET_MAX, Math.max(BUDGET_MIN,
        Math.ceil((state.productType?.minPrice ?? BUDGET_MIN) / BUDGET_STEP) * BUDGET_STEP));
    slider.min = floor;
    $('.range-labels span:first-child').textContent = money(floor);
    if (state.budget < floor) state.budget = floor;
    slider.value = state.budget;
    $('#budgetText').textContent = money(state.budget);
}

// ===== Tải dữ liệu từ backend =====
async function loadProductTypes() {
    try {
        const data = await api('/products/types');
        state.productTypes = (data || []).map(normType);
        state.productType = state.productTypes.find(p => /bó hoa/i.test(p.name)) || state.productTypes[0] || null;
    } catch (e) {
        $('#productGrid').innerHTML = `<p style="color:#b00">Không tải được loại sản phẩm: ${esc(e.message)}</p>`;
        return;
    }
    renderProductTypes();
    applyBudgetFloor();
}

async function loadMaterials() {
    if (!state.productType) return;
    const token = ++loadToken;                      // bỏ qua kết quả cũ nếu khách đổi lựa chọn liên tục
    $('#materials').innerHTML = '<p style="color:var(--muted)">Đang tải nguyên liệu...</p>';
    try {
        const [mats, limits] = await Promise.all([
            api(`/design/materials?productType=${encodeURIComponent(state.productType.id)}&budgetLevel=${state.budget}`),
            api(`/design/limits?budgetLevel=${state.budget}`).catch(() => null)   // thiếu định mức thì server vẫn kiểm tra khi validate
        ]);
        if (token !== loadToken) return;

        state.materials = (mats || []).map(normMaterial);
        state.limits = limits;

        // Bỏ món không còn hợp lệ; cắt số lượng theo kho
        const byId = new Map(state.materials.map(m => [m.id, m]));
        [...state.quantities].forEach(([id, q]) => {
            const m = byId.get(id);
            if (!m) return state.quantities.delete(id);
            if (m.stock !== null && q > m.stock) state.quantities.set(id, m.stock);
        });
    } catch (e) {
        if (token !== loadToken) return;
        state.materials = [];
        state.limits = null;
        $('#materials').innerHTML = `<p style="color:#b00">Không tải được nguyên liệu: ${esc(e.message)}</p>`;
        updateCart();
        return;
    }
    renderMaterials();
}

async function validateWithServer() {
    const items = cartItems().map(({ materialId, quantity }) => ({ materialId, quantity }));
    await api(`/design/validate?productType=${state.productType.id}&budgetLevel=${state.budget}`, {
        method: 'POST',
        body: JSON.stringify(items)
    });
}

// ===== Điều hướng bước =====
function goTo(n) {
    state.step = n;
    $$('.step').forEach(x => x.classList.toggle('active', +x.dataset.step === n));
    $('#stepNumber').textContent = String(n).padStart(2, '0');
    $('#progressBar').style.width = (n / 6 * 100) + '%';
    if (n >= 5) renderSummary();
    window.scrollTo({ top: document.querySelector('.flow').offsetTop, behavior: 'smooth' });
}

async function tryNext() {
    const n = state.step;
    if (n === 1 && !state.productType) return showModal('Vui lòng chọn một loại sản phẩm.');
    if (n === 4) {
        if (!cartItems().length) return showModal('Bạn chưa chọn nguyên liệu nào!');
        try { await validateWithServer(); }
        catch (e) { return showModal(e.message, { type: 'error', title: 'Chưa thể tiếp tục' }); }
    }
    goTo(Math.min(6, n + 1));
}

// ===== Khởi tạo =====
document.addEventListener('DOMContentLoaded', async () => {
    $$('.next-step').forEach(b => b.onclick = tryNext);
    $$('.prev-step').forEach(b => b.onclick = () => goTo(Math.max(1, state.step - 1)));

    // Bước 1: chọn loại sản phẩm -> cập nhật mức giá tối thiểu và tải lại nguyên liệu
    $('#productGrid').onclick = e => {
        const card = e.target.closest('.product-card');
        if (!card) return;
        const p = state.productTypes.find(x => String(x.id) === card.dataset.id);
        if (!p || p.id === state.productType?.id) return;
        state.productType = p;
        state.quantities.clear();
        renderProductTypes();
        applyBudgetFloor();
        loadMaterials();
    };

    // Bước 2: ngân sách (cập nhật số khi kéo, gọi server khi thả)
    const slider = $('#budget');
    slider.oninput = e => {
        state.budget = +e.target.value;
        $('#budgetText').textContent = money(state.budget);
        updateCart();
    };
    slider.onchange = () => loadMaterials();

    // Bước 3: phong cách (bắt buộc, chọn 1) | tone màu, mục đích tặng (tùy chọn, bấm lại để bỏ chọn)
    const choiceKey = { style: 'style', color: 'themeColor', occasion: 'occasion' };
    $$('.choice-grid[data-choice]').forEach(grid => {
        const key = choiceKey[grid.dataset.choice];
        const optional = grid.hasAttribute('data-optional');
        grid.onclick = e => {
            const btn = e.target.closest('.choice');
            if (!btn) return;
            const wasSelected = btn.classList.contains('selected');
            grid.querySelectorAll('.choice').forEach(x => x.classList.remove('selected'));
            if (wasSelected && optional) { state[key] = null; return; }
            btn.classList.add('selected');
            state[key] = btn.textContent.trim();
        };
        const pre = grid.querySelector('.choice.selected');
        if (pre) state[key] = pre.textContent.trim();
    });

    // Bước 4: lọc nhóm
    $$('.material-tabs button').forEach(b => b.onclick = () => {
        $$('.material-tabs button').forEach(x => x.classList.remove('selected'));
        b.classList.add('selected');
        state.filter = b.dataset.filter;
        state.group = null;
        renderMaterials();
    });

    // Bước 4: bấm nhãn định mức để lọc theo nhóm, bấm lại để tắt (quay về "Tất cả")
    $('#limitBar').onclick = e => {
        const chip = e.target.closest('[data-group]');
        if (!chip) return;
        const g = chip.dataset.group;
        if (state.group === g) { state.group = null; state.filter = 'all'; }
        else { state.group = g; state.filter = GROUPS[g].tab; }
        $$('.material-tabs button').forEach(x => x.classList.toggle('selected', x.dataset.filter === state.filter));
        renderMaterials();
    };

    // Bước 4: tăng/giảm số lượng (chặn theo kho + định mức nhóm)
    $('#materials').onclick = e => {
        const b = e.target.closest('button[data-a]');
        if (!b || b.disabled) return;
        const id = Number(b.dataset.id);
        const m = state.materials.find(x => x.id === id);
        if (!m) return;
        const q = Math.max(0, Math.min(qtyOf(id) + (b.dataset.a === 'plus' ? 1 : -1), maxQty(m)));
        q ? state.quantities.set(id, q) : state.quantities.delete(id);
        renderMaterials();
    };

    // Gợi ý nâng lên mức giá kế tiếp khi vượt ngân sách
    $('#raiseBudget').onclick = () => {
        state.budget = Math.min(BUDGET_MAX, state.budget + BUDGET_STEP);
        slider.value = state.budget;
        $('#budgetText').textContent = money(state.budget);
        loadMaterials();
    };

    $('#validate').onclick = async () => {
        if (!cartItems().length) return showModal('Bạn chưa chọn nguyên liệu nào!');
        try {
            await validateWithServer();
            showModal('Thiết kế đang nằm trong ngân sách.', { type: 'success', title: 'Tuyệt vời!' });
        } catch (e) { showModal(e.message, { type: 'error', title: 'Chưa hợp lệ' }); }
    };

    // Bước 6: lưu thiết kế và sang trang chốt đơn
    $('#confirmForm').onsubmit = e => {
        e.preventDefault();
        const items = cartItems();
        if (!items.length) return showModal('Bạn chưa chọn hoa nào!');
        sessionStorage.setItem('hoaOrder', JSON.stringify({
            productTypeId: state.productType.id,
            productTypeName: state.productType.name,
            budgetLevel: state.budget,
            style: state.style,
            themeColor: state.themeColor,
            occasion: state.occasion,
            total: cartTotal(),
            items: items.map(({ materialId, quantity }) => ({ materialId, quantity })),
            display: items.map(({ name, quantity, price }) => ({ name, quantity, price }))
        }));
        location.href = '/customer/checkout.html';
    };

    $('#budgetText').textContent = money(state.budget);
    await loadProductTypes();
    await loadMaterials();
});