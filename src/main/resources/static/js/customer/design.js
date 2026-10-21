const API_BASE = '/customer';
const BUDGET_MIN = 400000, BUDGET_MAX = 1500000, BUDGET_STEP = 100000;

// ===== Helpers =====
const qs = s => document.querySelector(s);
const qsa = s => document.querySelectorAll(s);
const money = n => Number(n || 0).toLocaleString('vi-VN') + 'đ';
const esc = s => String(s ?? '').replace(/[&<>"']/g,
    c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

const PLACEHOLDER = 'data:image/svg+xml;utf8,' + encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96"><rect width="100%" height="100%" fill="#f3ece4"/>' +
    '<text x="50%" y="58%" font-size="36" text-anchor="middle">🌸</text></svg>');
const imgSrc = u => !u ? PLACEHOLDER : (/^(https?:|\/|data:)/.test(u) ? u : '/' + u);

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
    if (!res.ok) throw new Error(data?.message || ('Lỗi máy chủ (' + res.status + ')'));
    return data;
}

// ===== Nhóm nguyên liệu =====
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
    group: null,
    budget: 700000,
    levels: [],
    style: 'Dịu dàng',
    themeColor: null,
    occasion: null,
    productTypes: [],
    productType: null,
    materials: [],
    limits: null,
    quantities: new Map()
};
let loadToken = 0;

// ===== Chuẩn hóa dữ liệu =====
function normType(p) {
    return {
        id: p.id,
        name: p.name ?? ('Loại ' + p.id),
        minPrice: Number(p.minBudget ?? BUDGET_MIN)
    };
}

function normMaterial(m) {
    const rawGroup = String(m.designGroup || '').toLowerCase();
    const group = GROUPS[rawGroup] ? rawGroup : 'other';
    return {
        id: m.id,
        categoryId: m.categoryId,
        group: group,
        categoryName: m.categoryName || '',
        name: m.name,
        price: Number(m.price ?? 0),
        imageUrl: m.imageUrl || null,
        stock: m.stock == null ? null : Number(m.stock),
        unit: m.unit || 'đơn vị'
    };
}

// ===== Tính toán Giỏ hàng & Giới hạn theo Ngân sách (Mục 5 file .docx) =====
const qtyOf = id => state.quantities.get(id) || 0;

function cartItems() {
    return state.materials
        .filter(m => qtyOf(m.id) > 0)
        .map(m => ({
            materialId: m.id,
            categoryId: m.categoryId,
            quantity: qtyOf(m.id),
            name: m.name,
            price: m.price,
            imageUrl: m.imageUrl,
            group: m.group,
            unit: m.unit
        }));
}
const cartTotal = () => cartItems().reduce((s, i) => s + i.price * i.quantity, 0);

// Tiền nguyên liệu tối đa của gói = mức giá x (1 - tỷ lệ nhân sự/lợi nhuận). Server (/design/limits) là nguồn quyết định.
const materialRatio = () => (state.limits && state.limits.materialRatio != null) ? Number(state.limits.materialRatio) : 1;
const materialCapOf = level => Math.floor(level * materialRatio() / 1000 + 1e-9) * 1000;
const budgetCap = () => (state.limits && state.limits.materialBudget != null) ? Number(state.limits.materialBudget) : state.budget;

const groupCount = g => cartItems().filter(i => i.group === g).reduce((s, i) => s + i.quantity, 0);
const groupLimit = g => {
    const key = GROUPS[g].limit;
    return key && state.limits && state.limits[key] != null ? Number(state.limits[key]) : null;
};

function maxQty(m) {
    let max = m.stock ?? Infinity;
    const lim = groupLimit(m.group);
    if (lim !== null) {
        max = Math.min(max, qtyOf(m.id) + Math.max(0, lim - groupCount(m.group)));
    }
    if (m.price > 0) {
        const remainingBudget = Math.max(0, budgetCap() - cartTotal());
        const affordableExtra = Math.floor(remainingBudget / m.price);
        max = Math.min(max, qtyOf(m.id) + affordableExtra);
    }
    return max;
}

// "Đổi hoa": thay một nguyên liệu đang chọn bằng nguyên liệu khác cùng nhóm, vẫn nằm trong ngân sách & tồn kho
function swapMaterial(id) {
    const cur = state.materials.find(x => x.id === id);
    const q = qtyOf(id);
    if (!cur || !q) return;
    const budgetLeft = budgetCap() - (cartTotal() - cur.price * q);
    const options = [];
    state.materials.forEach(x => {
        if (x.id === id || x.group !== cur.group || qtyOf(x.id) > 0) return;
        let n = Math.min(q, x.stock ?? Infinity);
        if (x.price > 0) n = Math.min(n, Math.floor(budgetLeft / x.price));
        if (n > 0) options.push({ m: x, n });
    });
    if (!options.length) {
        return showModal('Hiện chưa có lựa chọn khác phù hợp ngân sách trong nhóm này.');
    }
    const pick = options[Math.floor(Math.random() * options.length)];
    state.quantities.delete(id);
    state.quantities.set(pick.m.id, pick.n);
    renderMaterials();
}

// ===== Moodboard (bước 6): lưới ảnh nguyên liệu đã chọn =====
function renderMoodboard() {
    const box = qs('#moodboard');
    if (!box) return;
    const order = ['packaging', 'main', 'sub', 'leaf', 'accessory', 'other'];
    const items = cartItems().sort((a, b) => order.indexOf(a.group) - order.indexOf(b.group));
    box.innerHTML = items.map(i =>
        '<figure style="margin:0;text-align:center">' +
            '<img class="mt-img" src="' + esc(imgSrc(i.imageUrl)) + '" alt="' + esc(i.name) + '" loading="lazy" style="width:100%;aspect-ratio:1;object-fit:cover;border-radius:14px">' +
            '<figcaption style="font-size:12px;margin-top:4px">' + esc(i.name) + ' × ' + i.quantity + '</figcaption>' +
        '</figure>'
    ).join('');
}

// ===== Bước 5: nhúng trang mô phỏng /static/index.html =====
const SIM_URL = '/static/index.html';
const COLOR_MAP = { 'Hồng pastel': '#e4b7c1', 'Trắng kem': '#eadfcf', 'Tím lavender': '#c5a0c4', 'Đỏ': '#b92042' };
const normText = s => String(s || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').toLowerCase().trim();

function simDoc() {
    try {
        const frame = qs('#colleagueFullHtmlFrame');
        return frame?.contentDocument || frame?.contentWindow?.document || null;
    } catch (_) { return null; }
}

// Dữ liệu khách đã chọn ở bước 1-4
function buildDesignContext() {
    const items = cartItems();
    return {
        productTypeId: state.productType?.id ?? null,
        productTypeName: state.productType?.name ?? '',
        budgetLevel: state.budget,
        style: state.style,
        themeColor: state.themeColor,
        occasion: state.occasion,
        total: cartTotal(),
        items: items.map(i => ({ materialId: i.materialId, name: i.name, quantity: i.quantity, price: i.price, group: i.group, unit: i.unit }))
    };
}

// Tên loại sản phẩm của khách (DB) -> id "kiểu sản phẩm" trong thư viện mô phỏng (kinds trong shop-studio.mjs)
function shopKindFromName(name) {
    const t = normText(name);
    if (/\b(set|mix)\b|tha binh/.test(t)) return 'cut-set';
    if (/binh|\blo\b/.test(t)) return 'vase';
    if (/\bgio\b/.test(t)) return 'basket';
    if (/\b(box|hop|tui|vali)\b/.test(t)) return 'gift-box';
    if (/\bke\b/.test(t)) return 'stand';
    if (/\bbo\b/.test(t)) return 'bouquet';
    return 'all';
}
// "Nhu cầu" của thư viện lọc theo kiểu sản phẩm, nên phải đặt đúng thì mới không bị rỗng danh sách
const SHOP_NEED = { bouquet: 'gift', basket: 'gift', 'gift-box': 'gift', stand: 'celebration', vase: 'home', 'cut-set': 'home' };

// Điền sẵn vào trang mô phỏng + ẩn phần thừa (header, footer, bước gửi yêu cầu riêng)
function applyContextToFrame(doc, ctx) {
    if (!doc || !doc.body) return;
    if (!doc.getElementById('moc-embed-style')) {
        const st = doc.createElement('style');
        st.id = 'moc-embed-style';
        st.textContent =
            'body>header,main>.intro,main>footer,.request-history,#shop-step-4,#to-request,' +
            '.shop-flow button[data-step="4"]{display:none!important}' +
            'body{background:transparent!important}main{padding-top:0!important}';
        doc.head.appendChild(st);
    }
    const fire = (el, t) => el.dispatchEvent(new Event(t, { bubbles: true }));

    // Chờ shop-studio.mjs tải xong catalog (lúc đó ô "Kiểu sản phẩm" mới có option)
    let tries = 0;
    const timer = setInterval(() => {
        const kind = doc.getElementById('kind');
        const ready = kind && kind.options.length > 1 && doc.getElementById('shop-budget');
        if (!ready) { if (++tries > 50) clearInterval(timer); return; }
        clearInterval(timer);

        const k = shopKindFromName(ctx.productTypeName);
        const need = doc.getElementById('need');
        if (need && SHOP_NEED[k]) { need.value = SHOP_NEED[k]; fire(need, 'change'); }
        if (k !== 'all') { kind.value = k; fire(kind, 'change'); }

        // Hai ô này chỉ được đọc khi cần nên không phải bắn sự kiện
        const budget = doc.getElementById('shop-budget');
        if (budget && ctx.budgetLevel) budget.value = ctx.budgetLevel;
        // Nếu bộ lọc kiểu sản phẩm / nhu cầu khiến danh sách mẫu trống (0 ảnh) thì nới dần để khách vẫn thấy mẫu và thử màu
        const isEmpty = () => /(^|\s)0 ảnh mẫu phù hợp/.test(doc.body ? (doc.body.innerText || doc.body.textContent || '') : '');
        const resetSelect = el => {
            if (!el) return false;
            const opt = [...el.options].find(o => o.value === 'all' || o.value === '');
            if (!opt) return false;
            el.value = opt.value; fire(el, 'change'); return true;
        };
        // Mẫu thử màu thường không nằm trong đúng kiểu/nhu cầu của loại sản phẩm -> bỏ cả hai bộ lọc này, giữ ô "chỉ xem mẫu có thể thử màu"
        const relaxSteps = [() => { resetSelect(kind); resetSelect(need); }];
        let relaxIdx = 0;
        const relax = setInterval(() => {
            if (!doc.body) return clearInterval(relax);
            if (!isEmpty() || relaxIdx >= relaxSteps.length) return clearInterval(relax);
            relaxSteps[relaxIdx++]();
        }, 800);

        const color = COLOR_MAP[ctx.themeColor];
        const colorSel = doc.getElementById('shop-flower-color');
        if (color && colorSel && [...colorSel.options].some(o => o.value === color)) colorSel.value = color;
    }, 300);
}

// Gọi mỗi khi vào bước 5. Nếu dữ liệu không đổi thì giữ nguyên phiên đang chỉnh dở.
function initSimulation() {
    const frame = qs('#colleagueFullHtmlFrame');
    if (!frame) return;
    const ctx = buildDesignContext();
    try { sessionStorage.setItem('hoaDesignContext', JSON.stringify(ctx)); } catch (_) {}

    const sig = JSON.stringify(ctx);
    if (frame.dataset.sig === sig && frame.getAttribute('src')) return;
    frame.dataset.sig = sig;

    frame.onload = () => {
        applyContextToFrame(simDoc(), ctx);
        try { frame.contentWindow.postMessage({ type: 'mocanh:design-context', payload: ctx }, location.origin); } catch (_) {}
    };
    frame.src = frame.dataset.src || SIM_URL;
}

// Chuyển ảnh (kể cả blob:) thành data URL nhỏ để còn dùng được sau khi iframe bị hủy
function snapshotImage(img) {
    try {
        const w = img.naturalWidth, h = img.naturalHeight;
        if (!w || !h) return null;
        const r = Math.min(1, 800 / Math.max(w, h));
        const c = document.createElement('canvas');
        c.width = Math.round(w * r); c.height = Math.round(h * r);
        c.getContext('2d').drawImage(img, 0, 0, c.width, c.height);
        return c.toDataURL('image/jpeg', 0.8);
    } catch (_) { return null; }
}

// Lấy kết quả mô phỏng hiện tại trong iframe để gửi sang trang chốt đơn
function extractColleagueSimulationSummary() {
    const doc = simDoc();
    if (!doc || !doc.getElementById('shop-preview')) return null;
    try {
        const text = id => doc.getElementById(id)?.textContent?.trim() || '';
        const mode = doc.getElementById('design-mode')?.selectedOptions?.[0]?.textContent?.trim() || '';
        const samples = [...doc.querySelectorAll('#selected-samples figcaption')]
            .map(e => e.textContent.trim().replace(/\s+/g, ' ')).filter(Boolean);

        let imageUrl = null;
        const img = doc.getElementById('shop-preview');
        const cv = doc.getElementById('shop-canvas');
        if (img && !img.hidden && img.src) {
            imageUrl = img.src.startsWith('blob:') ? snapshotImage(img) : img.src;
        } else if (cv && !cv.hidden) {
            try { imageUrl = cv.toDataURL('image/jpeg', 0.8); } catch (_) {}
        }

        return { mode, summary: text('design-summary'), kind: text('preview-kind'), samples, imageUrl };
    } catch (_) { return null; }
}

// ===== Render =====
function renderProductTypes() {
    const grid = qs('#productGrid');
    if (!state.productTypes.length) {
        grid.innerHTML = '<p>Chưa có loại sản phẩm nào đang mở bán.</p>';
        return;
    }
    grid.innerHTML = state.productTypes.map(p =>
        '<article class="product-card ' + (state.productType?.id === p.id ? 'selected' : '') + '" data-id="' + esc(p.id) + '">' +
            '<div><strong>' + esc(p.name) + '</strong><small>Bắt đầu từ ' + money(p.minPrice) + '</small></div>' +
        '</article>'
    ).join('');
}

function renderLimits() {
    const bar = qs('#limitBar');
    if (!state.limits) { bar.innerHTML = ''; return; }
    bar.innerHTML = ['main', 'sub', 'leaf', 'packaging', 'accessory'].map(g => {
        const lim = groupLimit(g), n = groupCount(g);
        if (lim === null || (lim === 0 && n === 0)) return '';
        const cls = n > lim ? 'over' : n === lim ? 'full' : '';
        const on = state.group === g ? 'active' : '';
        return '<button type="button" class="mt-chip ' + cls + ' ' + on + '" data-group="' + g + '" aria-pressed="' + Boolean(on) + '">' +
            GROUPS[g].label + ': <b>' + n + '/' + lim + '</b></button>';
    }).join('');
}

function renderMaterials() {
    const box = qs('#materials');
    const list = state.materials.filter(m =>
        (state.filter === 'all' || GROUPS[m.group].tab === state.filter) &&
        (!state.group || m.group === state.group)
    );

    if (!list.length) {
        box.innerHTML = '<p style="color:var(--muted)">Hiện chưa có nguyên liệu phù hợp với lựa chọn của bạn.</p>';
    } else {
        box.innerHTML = list.map(m => {
            const q = qtyOf(m.id);
            const low = m.stock !== null && m.stock <= 5;
            const canAdd = q < maxQty(m);
            return '<article class="mt-card ' + (q ? 'has-qty' : '') + '">' +
                '<img class="mt-thumb mt-img" src="' + esc(imgSrc(m.imageUrl)) + '" alt="' + esc(m.name) + '" loading="lazy">' +
                '<div class="mt-info">' +
                    '<strong>' + esc(m.name) + '</strong>' +
                    '<span class="mt-group">' + esc(m.categoryName || GROUPS[m.group].label) + '</span>' +
                    '<span class="mt-price">' + money(m.price) + ' <small style="color:var(--muted);font-family:var(--sans)">/ ' + esc(m.unit) + '</small></span>' +
                    (q ? '<button type="button" data-a="swap" data-id="' + esc(m.id) + '" style="align-self:flex-start;margin-top:4px;padding:2px 10px;font-size:12px;border:1px solid var(--line,#d8cfc0);border-radius:999px;background:transparent;cursor:pointer">⟳ Đổi</button>' : '') +
                    (m.stock !== null ? '<span class="mt-stock ' + (low ? 'low' : '') + '">' + (low ? 'Sắp hết · ' : '') + 'Còn ' + m.stock + ' ' + esc(m.unit) + '</span>' : '') +
                '</div>' +
                '<div class="mt-qty">' +
                    '<button type="button" data-a="minus" data-id="' + esc(m.id) + '" ' + (q ? '' : 'disabled') + ' aria-label="Giảm">−</button>' +
                    '<b>' + q + '</b>' +
                    '<button type="button" data-a="plus" data-id="' + esc(m.id) + '" ' + (canAdd ? '' : 'disabled') + ' aria-label="Tăng">+</button>' +
                '</div>' +
            '</article>';
        }).join('');
    }
    updateCart();
}

function updateCart() {
    const items = cartItems();
    const total = cartTotal();
    const msg = qs('#budgetMessage');
    const raise = qs('#raiseBudget');

    qs('#cartCount').textContent = items.reduce((s, i) => s + i.quantity, 0);
    qs('#cartTotal').textContent = money(total);

    const cap = budgetCap();
    if (total > cap) {
        msg.textContent = 'Vượt ' + money(total - cap) + ' định mức nguyên liệu của gói';
        msg.classList.add('mt-over');
        const next = nextLevel();
        raise.hidden = next === null;
        if (next !== null) raise.textContent = 'Nâng lên ' + money(next);
    } else {
        msg.textContent = 'Còn ' + money(cap - total) + ' để chọn nguyên liệu (đã trừ công thực hiện)';
        msg.classList.remove('mt-over');
        raise.hidden = true;
    }

    qs('#cartThumbs').innerHTML = items.slice(0, 8)
        .map(i => '<img class="mt-img" src="' + esc(imgSrc(i.imageUrl)) + '" alt="' + esc(i.name) + '" title="' + esc(i.name) + ' × ' + i.quantity + '">').join('');
    renderLimits();
}

// ===== Thanh ngân sách =====
async function loadBudgetLevels() {
    try {
        const d = await api('/design/budget-levels');
        state.levels = (d || []).map(Number).filter(n => n > 0).sort((x, y) => x - y);
    } catch (_) {
        state.levels = [];
        for (let v = BUDGET_MIN; v <= BUDGET_MAX; v += BUDGET_STEP) state.levels.push(v);
    }
}

function availableLevels() {
    const min = state.productType?.minPrice ?? 0;
    return state.levels.filter(l => l >= min);
}

function nextLevel() {
    // Ưu tiên mức giá gần nhất mà định mức nguyên liệu đủ chứa giỏ hiện tại; không có thì lên một bậc
    const total = cartTotal();
    const higher = availableLevels().filter(l => l > state.budget);
    const fit = higher.find(l => materialCapOf(l) >= total);
    const n = fit !== undefined ? fit : higher[0];
    return n === undefined ? null : n;
}

function applyBudgetFloor() {
    const slider = qs('#budget'), lv = availableLevels();
    const labels = qsa('.range-labels span');
    if (!lv.length) {
        slider.disabled = true;
        qs('#budgetText').textContent = 'Chưa có mức giá';
        labels.forEach(s => s.textContent = '—');
        return;
    }
    let idx = lv.findIndex(l => l >= state.budget);
    if (idx < 0) idx = lv.length - 1;
    state.budget = lv[idx];
    slider.min = 0; slider.max = lv.length - 1; slider.step = 1; slider.value = idx;
    slider.disabled = lv.length < 2;
    slider.setAttribute('aria-valuetext', money(state.budget));
    labels[0].textContent = money(lv[0]);
    labels[labels.length - 1].textContent = money(lv[lv.length - 1]);
    qs('#budgetText').textContent = money(state.budget);
}

// ===== Tải dữ liệu từ backend =====
async function loadProductTypes() {
    try {
        const data = await api('/products/types');
        state.productTypes = (data || []).map(normType);
        state.productType = state.productTypes[0] || null;
    } catch (e) {
        qs('#productGrid').innerHTML = '<p style="color:#b00">Không tải được loại sản phẩm: ' + esc(e.message) + '</p>';
        return;
    }
    renderProductTypes();
    applyBudgetFloor();
}

async function loadMaterials() {
    if (!state.productType) return;
    const token = ++loadToken;
    qs('#materials').innerHTML = '<p style="color:var(--muted)">Đang tải nguyên liệu...</p>';
    try {
        const [mats, limits] = await Promise.all([
            api('/design/materials?productType=' + encodeURIComponent(state.productType.id) + '&budgetLevel=' + state.budget),
            api('/design/limits?budgetLevel=' + state.budget).catch(() => null)
        ]);
        if (token !== loadToken) return;

        state.materials = (mats || []).map(normMaterial);
        state.limits = limits;

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
        qs('#materials').innerHTML = '<p style="color:#b00">Không tải được nguyên liệu: ' + esc(e.message) + '</p>';
        updateCart();
        return;
    }
    renderMaterials();
}

async function validateWithServer() {
    const items = cartItems().map(({ materialId, quantity }) => ({ materialId, quantity }));
    await api('/design/validate?productType=' + state.productType.id + '&budgetLevel=' + state.budget, {
        method: 'POST',
        body: JSON.stringify(items)
    });
}

// ===== Điều hướng bước: xem goTo() ở trên =====

async function tryNext() {
    const n = state.step;
    if (n === 1 && !state.productType) return showModal('Vui lòng chọn một loại sản phẩm.');
    if (n === 2 && !availableLevels().length) return showModal('Hiện chưa có mức giá phù hợp với loại sản phẩm này. Vui lòng liên hệ cửa hàng.');
    if (n === 4) {
        if (!cartItems().length) return showModal('Bạn chưa chọn nguyên liệu nào!');
        if (groupCount('main') + groupCount('sub') <= 0) {
            return showModal('Hãy chọn ít nhất một loại hoa cho thiết kế của bạn!', { type: 'error', title: 'Thiếu hoa' });
        }
        try { await validateWithServer(); }
        catch (e) { return showModal(e.message, { type: 'error', title: 'Chưa thể tiếp tục' }); }
    }
    goTo(Math.min(6, n + 1));
}

const STATE_KEY = 'hoaDesignState';
const STATE_TTL_MS = 24 * 60 * 60 * 1000;

// Lưu trạng thái hiện tại vào sessionStorage để không mất dữ liệu khi quay lại / sửa thiết kế
function saveDesignState() {
    try {
        const payload = {
            step: state.step,
            budget: state.budget,
            style: state.style,
            themeColor: state.themeColor,
            occasion: state.occasion,
            productTypeId: state.productType?.id ?? null,
            quantities: Array.from(state.quantities.entries())
        };
        payload.savedAt = Date.now();
        localStorage.setItem(STATE_KEY, JSON.stringify(payload));
    } catch (_) {}
}

// Xóa thiết kế tạm (gọi sau khi đặt hàng thành công)
function clearDesignState() {
    try { localStorage.removeItem(STATE_KEY); sessionStorage.removeItem('hoaOrder'); } catch (_) {}
}

function loadSavedDesignState() {
    try {
        const raw = localStorage.getItem(STATE_KEY);
        if (raw) {
            const s = JSON.parse(raw);
            // Bản lưu quá 24 giờ thì bỏ, tránh hiện lại thiết kế cũ của lần ghé trước
            if (s && s.savedAt && Date.now() - s.savedAt < STATE_TTL_MS) return s;
            localStorage.removeItem(STATE_KEY);
        }
        // Fallback nếu khách quay lại từ trang checkout mà chỉ có hoaOrder
        const orderRaw = sessionStorage.getItem('hoaOrder');
        if (orderRaw) {
            const o = JSON.parse(orderRaw);
            return {
                step: 5,
                budget: o.budgetLevel,
                style: o.style,
                themeColor: o.themeColor,
                occasion: o.occasion,
                productTypeId: o.productTypeId,
                quantities: (o.items || []).map(i => [i.materialId, i.quantity])
            };
        }
    } catch (_) {}
    return null;
}

// Gợi ý nhanh (tùy chọn, chỉ chạy khi khách bấm nút): phối nguyên liệu mẫu theo ngân sách và màu sắc.
// Mặc định khách tự chọn hoa ở Bước 4.
async function runAutoFill(force = false) {
    if (!state.productType) return;
    if (!force && state.quantities.size > 0) return;
    // Khách đã tự chọn rồi thì hỏi trước khi thay bằng gợi ý
    if (force && state.quantities.size > 0 &&
        !(await showConfirm('Gợi ý nhanh sẽ thay các nguyên liệu bạn đang chọn. Tiếp tục?',
            { title: 'Phối lại nguyên liệu', okText: 'Tiếp tục', cancelText: 'Giữ lựa chọn của tôi' }))) return;
    try {
        const params = new URLSearchParams({
            productType: state.productType.id,
            budgetLevel: state.budget
        });
        if (state.themeColor) params.set('themeColor', state.themeColor);
        if (force) params.set('seed', String(Date.now()));   // bấm "Phối lại" -> ra bộ khác
        const suggested = await api('/design/auto-fill?' + params.toString());
        if (Array.isArray(suggested) && suggested.length > 0) {
            state.quantities.clear();
            const byId = new Map(state.materials.map(m => [m.id, m]));
            suggested.forEach(({ materialId, quantity }) => {
                const m = byId.get(materialId);
                if (m && quantity > 0) {
                    const validQty = m.stock !== null ? Math.min(quantity, m.stock) : quantity;
                    if (validQty > 0) state.quantities.set(materialId, validQty);
                }
            });
            saveDesignState();
            renderMaterials();
        }
    } catch (e) {
        // Tự chạy ngầm thì bỏ qua lỗi; chỉ báo khi khách chủ động bấm nút
        if (force) showModal((e && e.message) || 'Không gợi ý được nguyên liệu lúc này, vui lòng thử lại.', { type: 'error', title: 'Gợi ý nhanh' });
    }
}

// ===== Điều hướng bước (Đồng bộ History API cho điện thoại) =====
function goTo(n, pushHistory = true) {
    state.step = n;
    qsa('section.step').forEach(x => x.classList.toggle('active', +x.dataset.step === n));
    qs('#stepNumber').textContent = String(n).padStart(2, '0');
    qs('#progressBar').style.width = (n / 6 * 100) + '%';

    // Ẩn nút Quay lại ở Bước 1, hiện từ Bước 2 trở đi
    const topBack = qs('#topStepNav');
    if (topBack) topBack.hidden = (n <= 1);

    saveDesignState();
    if (pushHistory) {
        try { history.pushState({ step: n }, '', '#step-' + n); } catch (_) {}
    }

    window.scrollTo({ top: document.querySelector('.flow').offsetTop, behavior: 'smooth' });
    if (n === 5) initSimulation();
    if (n === 6) renderMoodboard();
}

// ===== Khởi tạo =====
document.addEventListener('DOMContentLoaded', async () => {
    // Bắt sự kiện nút Back cứng trên điện thoại / trình duyệt
    window.addEventListener('popstate', e => {
        if (e.state && typeof e.state.step === 'number') {
            goTo(e.state.step, false);
        } else if (state.step > 1) {
            goTo(state.step - 1, false);
        }
    });

    const autoFillBtn = qs('#autoFillBtn');
    if (autoFillBtn) {
        autoFillBtn.onclick = () => runAutoFill(true);
    }

    qsa('.next-step').forEach(b => b.onclick = tryNext);
    qsa('.prev-step').forEach(b => b.onclick = () => goTo(Math.max(1, state.step - 1)));

    qs('#productGrid').onclick = e => {
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

    const slider = qs('#budget');
    slider.oninput = e => {
        const lv = availableLevels();
        state.budget = lv[+e.target.value] ?? state.budget;
        slider.setAttribute('aria-valuetext', money(state.budget));
        qs('#budgetText').textContent = money(state.budget);
        updateCart();
    };
    slider.onchange = () => loadMaterials();

    const choiceKey = { style: 'style', color: 'themeColor', occasion: 'occasion' };
    qsa('.choice-grid[data-choice]').forEach(grid => {
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

    qsa('.material-tabs button').forEach(b => b.onclick = () => {
        qsa('.material-tabs button').forEach(x => x.classList.remove('selected'));
        b.classList.add('selected');
        state.filter = b.dataset.filter;
        state.group = null;
        renderMaterials();
    });

    qs('#limitBar').onclick = e => {
        const chip = e.target.closest('[data-group]');
        if (!chip) return;
        const g = chip.dataset.group;
        if (state.group === g) { state.group = null; state.filter = 'all'; }
        else { state.group = g; state.filter = GROUPS[g].tab; }
        qsa('.material-tabs button').forEach(x => x.classList.toggle('selected', x.dataset.filter === state.filter));
        renderMaterials();
    };

    qs('#materials').onclick = e => {
        const b = e.target.closest('button[data-a]');
        if (!b || b.disabled) return;
        const id = Number(b.dataset.id);
        const m = state.materials.find(x => x.id === id);
        if (!m) return;
        if (b.dataset.a === 'swap') { swapMaterial(id); return; }
        const q = Math.max(0, Math.min(qtyOf(id) + (b.dataset.a === 'plus' ? 1 : -1), maxQty(m)));
        q ? state.quantities.set(id, q) : state.quantities.delete(id);
        renderMaterials();
    };

    // Lưu state mỗi khi khách chọn phong cách / màu sắc / dịp tặng hoặc tăng giảm số lượng
    qsa('.choice-grid[data-choice]').forEach(grid => {
        grid.addEventListener('click', () => saveDesignState());
    });
    qs('#materials').addEventListener('click', () => saveDesignState());
    qs('#productGrid').addEventListener('click', () => saveDesignState());
    slider.addEventListener('change', () => saveDesignState());

    qs('#raiseBudget').onclick = () => {
        const next = nextLevel();
        if (next === null) return;
        state.budget = next;
        applyBudgetFloor();
        loadMaterials();
    };

    qs('#validate').onclick = async () => {
        if (!cartItems().length) return showModal('Bạn chưa chọn nguyên liệu nào!');
        try {
            await validateWithServer();
            showModal('Thiết kế đang nằm trong ngân sách.', { type: 'success', title: 'Tuyệt vời!' });
        } catch (e) { showModal(e.message, { type: 'error', title: 'Chưa hợp lệ' }); }
    };

    // Bước 6: lưu thiết kế + thông tin kết hợp mẫu (nếu có) và sang trang chốt đơn
    qs('#confirmForm').onsubmit = e => {
        e.preventDefault();
        const items = cartItems();
        if (!items.length) return showModal('Bạn chưa chọn hoa nào!');
        const simInfo = extractColleagueSimulationSummary();
        const order = {
            productTypeId: state.productType.id,
            productTypeName: state.productType.name,
            budgetLevel: state.budget,
            style: state.style,
            themeColor: state.themeColor,
            occasion: state.occasion,
            simulation: simInfo,
            total: cartTotal(),
            items: items.map(({ materialId, quantity }) => ({ materialId, quantity })),
            display: items.map(({ name, quantity, price, imageUrl }) => ({ name, quantity, price, imageUrl }))
        };
        try { sessionStorage.setItem('hoaOrder', JSON.stringify(order)); }
        catch (_) {
            // ảnh quá lớn -> bỏ ảnh, giữ phần còn lại
            if (order.simulation) order.simulation.imageUrl = null;
            sessionStorage.setItem('hoaOrder', JSON.stringify(order));
        }
        location.href = '/customer/checkout.html';
    };

    qs('#budgetText').textContent = money(state.budget);

    // Khởi tạo dữ liệu & Khôi phục lựa chọn cũ nếu khách quay lại từ trang Chốt đơn ("Sửa thiết kế")
    const saved = loadSavedDesignState();
    await loadBudgetLevels();
    await loadProductTypes();

    if (saved) {
        const foundType = state.productTypes.find(p => p.id === saved.productTypeId);
        if (foundType) state.productType = foundType;
        if (saved.budget) state.budget = Number(saved.budget);
        if (saved.style) state.style = saved.style;
        state.themeColor = saved.themeColor ?? null;
        state.occasion = saved.occasion ?? null;

        // Đồng bộ lại giao diện các nút Phong cách / Màu sắc / Dịp tặng
        const reverseChoice = { style: state.style, color: state.themeColor, occasion: state.occasion };
        qsa('.choice-grid[data-choice]').forEach(grid => {
            const val = reverseChoice[grid.dataset.choice];
            grid.querySelectorAll('.choice').forEach(btn => {
                btn.classList.toggle('selected', val != null && btn.textContent.trim() === val);
            });
        });

        renderProductTypes();
        applyBudgetFloor();
    }

    await loadMaterials();

    if (saved && Array.isArray(saved.quantities)) {
        const byId = new Map(state.materials.map(m => [m.id, m]));
        saved.quantities.forEach(([id, q]) => {
            const m = byId.get(Number(id));
            if (m && q > 0) {
                state.quantities.set(m.id, m.stock !== null ? Math.min(q, m.stock) : q);
            }
        });
        renderMaterials();
    }

    // Mở lại đúng bước đã lưu (nếu có)
    const initialStep = saved?.step && saved.step >= 1 && saved.step <= 6 ? saved.step : 1;
    try { history.replaceState({ step: initialStep }, '', '#step-' + initialStep); } catch (_) {}
    goTo(initialStep, false);
});