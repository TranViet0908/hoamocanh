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
    main:      { label: 'Hoa chính',    tab: 'flower',    limit: 'maxMainFlowers', weight: 1.2 },
    sub:       { label: 'Hoa phụ',      tab: 'flower',    limit: 'maxSubFlowers',  weight: 0.8 },
    leaf:      { label: 'Lá trang trí', tab: 'flower',    limit: 'maxLeaves',      weight: 0.7 },
    packaging: { label: 'Bao bì',       tab: 'packaging', limit: 'maxPackaging',   weight: 1.0 },
    accessory: { label: 'Phụ kiện',     tab: 'accessory', limit: 'maxAccessories', weight: 0.5 },
    other:     { label: 'Khác',         tab: 'accessory', limit: null,             weight: 0.5 }
};

// ===== Cấu hình cho 6 Loại sản phẩm =====
const PRODUCT_SIM_CONFIG = {
    vase: {
        title: 'Mô phỏng Set mix thả bình',
        formLabel: '01 · Dáng cắm thả bình',
        packLabel: 'Tông nền lớp bọc bảo vệ hoa',
        allowPhotoMode: true,
        containerPhoto: null,
        forms: [
            { id: 'natural', name: 'Thả bình tự nhiên' },
            { id: 'round',   name: 'Cắm vòm tròn đầy' },
            { id: 'long',    name: 'Cắm dáng cao thanh thoát' }
        ]
    },
    bouquet: {
        title: 'Mô phỏng Bó hoa thiết kế',
        formLabel: '01 · Chọn dáng bó hoa',
        packLabel: 'Màu giấy gói (Category: Giấy Gói)',
        allowPhotoMode: true,
        containerPhoto: null,
        forms: [
            { id: 'round',   name: 'Bó tròn' },
            { id: 'natural', name: 'Bó tự nhiên' },
            { id: 'long',    name: 'Bó dài' }
        ]
    },
    basket: {
        title: 'Mô phỏng Giỏ hoa tươi',
        formLabel: '01 · Chọn dáng giỏ hoa',
        packLabel: 'Màu giấy lót / giỏ hoa',
        allowPhotoMode: true,
        containerPhoto: '/images/materials/photos/basket.jpg',
        forms: [
            { id: 'round',   name: 'Giỏ đầy tròn truyền thống' },
            { id: 'natural', name: 'Giỏ vòm tự nhiên bất đối xứng' },
            { id: 'long',    name: 'Giỏ cắm dáng cao' }
        ]
    },
    box: {
        title: 'Mô phỏng Box hoa cao cấp',
        formLabel: '01 · Chọn kiểu hộp hoa',
        packLabel: 'Màu hộp hoa / giấy lót',
        allowPhotoMode: true,
        containerPhoto: '/images/materials/photos/box.jpg',
        forms: [
            { id: 'round',   name: 'Box trụ tròn cổ điển' },
            { id: 'natural', name: 'Box vòm tự nhiên hiện đại' },
            { id: 'long',    name: 'Box cắm dáng cao sang trọng' }
        ]
    },
    bag: {
        title: 'Mô phỏng Túi hoa quà tặng',
        formLabel: '01 · Chọn dáng túi hoa',
        packLabel: 'Màu túi quà tặng',
        allowPhotoMode: true,
        containerPhoto: '/images/materials/photos/bag.jpg',
        forms: [
            { id: 'natural', name: 'Túi đứng tự nhiên' },
            { id: 'round',   name: 'Túi ngang tròn đầy' },
            { id: 'long',    name: 'Túi cắm vươn cao' }
        ]
    },
    stand: {
        title: 'Mô phỏng Kệ hoa chúc mừng',
        formLabel: '01 · Chọn kết cấu kệ hoa',
        packLabel: 'Tông màu giấy bọc kệ',
        allowPhotoMode: true,
        containerPhoto: '/images/materials/photos/stand.jpg',
        forms: [
            { id: 'long',    name: 'Kệ dáng cao trang trọng' },
            { id: 'natural', name: 'Kệ vòm tự nhiên hiện đại' },
            { id: 'round',   name: 'Kệ đài tròn đầy đặn' }
        ]
    }
};

// ===== Thư viện Ảnh Chụp Studio Chuẩn: Đủ cả 3 dáng (round, natural, long) cho mọi Mix =====
const SIM_TEMPLATES = [
    // 1. Nhóm rose-only (Chỉ hoa chính, không hoa phụ)
    {
        id: 'rose-only-round',
        mix: 'rose-only',
        form: 'round',
        candidates: [
            '/images/photo-sample/round-rose-blush-v1.png',
            '/images/materials/photo-sample/round-rose-blush-v1.png'
        ],
        base_colors: { flower: '#e4b7c1', secondary: '#ffffff', paper: '#ddc5a7', ribbon: '#e6b2c2' },
        regions: {
            flower: { cx: 0.5, cy: 0.34, rx: 0.38, ry: 0.28 },
            ribbon: { top: 0.625, bottom: 0.92, left: 0.31, right: 0.69 },
            paper:  { top: 0.04,  bottom: 0.99, left: 0.03,  right: 0.97 }
        }
    },
    {
        id: 'rose-only-natural',
        mix: 'rose-only',
        form: 'natural',
        candidates: [
            '/images/photo-sample/natural-rose-blush-v1.png',
            '/images/materials/photo-sample/natural-rose-blush-v1.png'
        ],
        base_colors: { flower: '#e4b7c1', secondary: '#ffffff', paper: '#ddc5a7', ribbon: '#e6b2c2' },
        regions: {
            flower: { cx: 0.5, cy: 0.365, rx: 0.46, ry: 0.30 },
            ribbon: { top: 0.70, bottom: 0.97, left: 0.35, right: 0.63 },
            paper:  { top: 0.03, bottom: 0.99, left: 0.02, right: 0.98 }
        }
    },
    {
        id: 'rose-only-long',
        mix: 'rose-only',
        form: 'long',
        candidates: [
            '/images/photo-sample/long-rose-blush-v1.png',
            '/images/materials/photo-sample/long-rose-blush-v1.png'
        ],
        base_colors: { flower: '#e4b7c1', secondary: '#ffffff', paper: '#ddc5a7', ribbon: '#e6b2c2' },
        regions: {
            flower: { cx: 0.5, cy: 0.285, rx: 0.31, ry: 0.285 },
            ribbon: { top: 0.69, bottom: 0.92, left: 0.37, right: 0.61 },
            paper:  { top: 0.03, bottom: 0.99, left: 0.02, right: 0.98 }
        }
    },

    // 2. Nhóm rose-baby (Hoa chính + Baby trắng)
    {
        id: 'rose-baby-round',
        mix: 'rose-baby',
        form: 'round',
        candidates: [
            '/images/photo-sample/mix-rose-baby-v1.png',
            '/images/materials/photo-sample/mix-rose-baby-v1.png',
            '/images/photo-sample/round-rose-blush-v1.png',
            '/images/materials/photo-sample/round-rose-blush-v1.png'
        ],
        base_colors: { flower: '#e4b7c1', secondary: '#ffffff', paper: '#ddc5a7', ribbon: '#e6b2c2' },
        regions: {
            flower: { cx: 0.5, cy: 0.335, rx: 0.43, ry: 0.29 },
            ribbon: { top: 0.675, bottom: 1.0, left: 0.28, right: 0.70 },
            paper:  { top: 0.03,  bottom: 0.99, left: 0.02, right: 0.98 }
        }
    },
    {
        id: 'rose-baby-natural',
        mix: 'rose-baby',
        form: 'natural',
        candidates: [
            '/images/photo-sample/natural-rose-blush-v1.png',
            '/images/materials/photo-sample/natural-rose-blush-v1.png'
        ],
        base_colors: { flower: '#e4b7c1', secondary: '#ffffff', paper: '#ddc5a7', ribbon: '#e6b2c2' },
        regions: {
            flower: { cx: 0.5, cy: 0.365, rx: 0.46, ry: 0.30 },
            ribbon: { top: 0.70, bottom: 0.97, left: 0.35, right: 0.63 },
            paper:  { top: 0.03, bottom: 0.99, left: 0.02, right: 0.98 }
        }
    },
    {
        id: 'rose-baby-long',
        mix: 'rose-baby',
        form: 'long',
        candidates: [
            '/images/photo-sample/long-rose-blush-v1.png',
            '/images/materials/photo-sample/long-rose-blush-v1.png'
        ],
        base_colors: { flower: '#e4b7c1', secondary: '#ffffff', paper: '#ddc5a7', ribbon: '#e6b2c2' },
        regions: {
            flower: { cx: 0.5, cy: 0.285, rx: 0.31, ry: 0.285 },
            ribbon: { top: 0.69, bottom: 0.92, left: 0.37, right: 0.61 },
            paper:  { top: 0.03, bottom: 0.99, left: 0.02, right: 0.98 }
        }
    },

    // 3. Nhóm rose-daisy (Hoa chính + Cúc Tana / Thạch thảo)
    {
        id: 'rose-daisy-natural',
        mix: 'rose-daisy',
        form: 'natural',
        candidates: [
            '/images/photo-sample/mix-rose-daisy-v1.png',
            '/images/materials/photo-sample/mix-rose-daisy-v1.png'
        ],
        base_colors: { flower: '#e4b7c1', secondary: '#b592c7', paper: '#ddc5a7', ribbon: '#e6b2c2' },
        regions: {
            flower:    { cx: 0.5, cy: 0.335, rx: 0.43, ry: 0.29 },
            secondary: { cx: 0.5, cy: 0.335, rx: 0.43, ry: 0.29 },
            ribbon:    { top: 0.675, bottom: 1.0, left: 0.28, right: 0.70 },
            paper:     { top: 0.03,  bottom: 0.99, left: 0.02, right: 0.98 }
        }
    },
    {
        id: 'rose-daisy-round',
        mix: 'rose-daisy',
        form: 'round',
        candidates: [
            '/images/photo-sample/mix-rose-baby-v1.png',
            '/images/materials/photo-sample/mix-rose-baby-v1.png',
            '/images/photo-sample/round-rose-blush-v1.png',
            '/images/materials/photo-sample/round-rose-blush-v1.png'
        ],
        base_colors: { flower: '#e4b7c1', secondary: '#ffffff', paper: '#ddc5a7', ribbon: '#e6b2c2' },
        regions: {
            flower: { cx: 0.5, cy: 0.335, rx: 0.43, ry: 0.29 },
            ribbon: { top: 0.675, bottom: 1.0, left: 0.28, right: 0.70 },
            paper:  { top: 0.03,  bottom: 0.99, left: 0.02, right: 0.98 }
        }
    },
    {
        id: 'rose-daisy-long',
        mix: 'rose-daisy',
        form: 'long',
        candidates: [
            '/images/photo-sample/long-rose-blush-v1.png',
            '/images/materials/photo-sample/long-rose-blush-v1.png'
        ],
        base_colors: { flower: '#e4b7c1', secondary: '#ffffff', paper: '#ddc5a7', ribbon: '#e6b2c2' },
        regions: {
            flower: { cx: 0.5, cy: 0.285, rx: 0.31, ry: 0.285 },
            ribbon: { top: 0.69, bottom: 0.92, left: 0.37, right: 0.61 },
            paper:  { top: 0.03, bottom: 0.99, left: 0.02, right: 0.98 }
        }
    },

    // 4. Nhóm tulip-baby (Tulip chủ đạo)
    {
        id: 'tulip-baby-long',
        mix: 'tulip-baby',
        form: 'long',
        candidates: [
            '/images/photo-sample/mix-tulip-baby-v1.png',
            '/images/materials/photo-sample/mix-tulip-baby-v1.png'
        ],
        base_colors: { flower: '#e4b7c1', secondary: '#ffffff', paper: '#ddc5a7', ribbon: '#e6b2c2' },
        regions: {
            flower: { cx: 0.5, cy: 0.335, rx: 0.35, ry: 0.29 },
            ribbon: { top: 0.675, bottom: 1.0, left: 0.28, right: 0.70 },
            paper:  { top: 0.03,  bottom: 0.99, left: 0.02, right: 0.98 }
        }
    },
    {
        id: 'tulip-baby-natural',
        mix: 'tulip-baby',
        form: 'natural',
        candidates: [
            '/images/photo-sample/natural-rose-blush-v1.png',
            '/images/materials/photo-sample/natural-rose-blush-v1.png'
        ],
        base_colors: { flower: '#e4b7c1', secondary: '#ffffff', paper: '#ddc5a7', ribbon: '#e6b2c2' },
        regions: {
            flower: { cx: 0.5, cy: 0.365, rx: 0.46, ry: 0.30 },
            ribbon: { top: 0.70, bottom: 0.97, left: 0.35, right: 0.63 },
            paper:  { top: 0.03, bottom: 0.99, left: 0.02, right: 0.98 }
        }
    },
    {
        id: 'tulip-baby-round',
        mix: 'tulip-baby',
        form: 'round',
        candidates: [
            '/images/photo-sample/round-rose-blush-v1.png',
            '/images/materials/photo-sample/round-rose-blush-v1.png'
        ],
        base_colors: { flower: '#e4b7c1', secondary: '#ffffff', paper: '#ddc5a7', ribbon: '#e6b2c2' },
        regions: {
            flower: { cx: 0.5, cy: 0.34, rx: 0.38, ry: 0.28 },
            ribbon: { top: 0.625, bottom: 0.92, left: 0.31, right: 0.69 },
            paper:  { top: 0.04,  bottom: 0.99, left: 0.03, right: 0.97 }
        }
    }
];

const SIM_PRESETS = {
    'blush-beige': { flower: '#e4b7c1', sub: '#fffef6', paper: '#ddc5a7', ribbon: '#e6b2c2' },
    'cream-brown': { flower: '#eadfcf', sub: '#f4efd7', paper: '#c9a99a', ribbon: '#eadfcf' },
    'lilac-white': { flower: '#c5a0c4', sub: '#ad77bb', paper: '#f3eee6', ribbon: '#9ab3a4' }
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
    quantities: new Map(),
    sim: {
        mode: 'photo',
        kind: 'bouquet',
        mix: 'rose-baby',
        form: 'natural',
        flowerColor: '#e4b7c1',
        subColor: '#fffef6',
        paperColor: '#ddc5a7',
        ribbonColor: '#e6b2c2',
        cartColors: { flower: '#e4b7c1', sub: '#fffef6', paper: '#ddc5a7', ribbon: '#e6b2c2' },
        slotA: null,
        slotB: null
    }
};
let loadToken = 0;
const imageCache = new Map();

// ===== Nhận diện loại sản phẩm =====
function detectProductKind(name) {
    const t = String(name || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/\u0111/g, 'd').toLowerCase();
    if (/binh/.test(t)) return 'vase';
    if (/\bgio\b/.test(t)) return 'basket';
    if (/\b(box|hop)\b/.test(t)) return 'box';
    if (/\btui\b/.test(t)) return 'bag';
    if (/\bke\b/.test(t)) return 'stand';
    return 'bouquet';
}

function guessFallbackColor(name, group) {
    const n = String(name || '').toLowerCase();
    if (/đỏ|red/.test(n)) return '#b92042';
    if (/vàng|hướng dương|yellow/.test(n)) return '#edc365';
    if (/tím|tulip|thạch thảo/.test(n)) return '#c5a0c4';
    if (/cẩm chướng/.test(n)) return '#e7a2a0';
    if (/tana|cúc/.test(n)) return '#f4efd7';
    if (/baby|trắng|kính mờ/.test(n)) return '#f3eee6';
    if (/kraft|nâu|mây|lẵng/.test(n)) return '#ddc5a7';
    if (group === 'leaf') return '#78988c';
    return '#e4b7c1';
}

// ===== Chuẩn hóa dữ liệu =====
function normType(p) {
    return {
        id: p.id,
        name: p.name ?? ('Loại ' + p.id),
        kind: detectProductKind(p.name),
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
        color: m.color || guessFallbackColor(m.name, group),
        unit: m.unit || 'đơn vị'
    };
}

// ===== Tính toán Giỏ hàng =====
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
            color: m.color,
            unit: m.unit
        }));
}
const cartTotal = () => cartItems().reduce((s, i) => s + i.price * i.quantity, 0);

const groupCount = g => cartItems().filter(i => i.group === g).reduce((s, i) => s + i.quantity, 0);
const groupLimit = g => {
    const key = GROUPS[g].limit;
    return key && state.limits && state.limits[key] != null ? Number(state.limits[key]) : null;
};

function maxQty(m) {
    let max = m.stock ?? Infinity;
    const lim = groupLimit(m.group);
    if (lim !== null) max = Math.min(max, qtyOf(m.id) + Math.max(0, lim - groupCount(m.group)));
    return max;
}

// ===== Rút gọn nhãn hiển thị trong Dropdown 00 để không bao giờ bị tràn ngang =====
function buildCompactFlowerLabel(flowers) {
    if (!flowers.length) return 'Chưa chọn hoa';
    const totalStems = flowers.reduce((s, i) => s + i.quantity, 0);
    if (flowers.length <= 2) {
        return flowers.map(i => i.quantity + ' ' + i.name).join(' + ');
    }
    const firstTwo = flowers.slice(0, 2).map(i => i.quantity + ' ' + i.name).join(' + ');
    const remainCount = flowers.length - 2;
    return firstTwo + ' (+' + remainCount + ' loại · tổng ' + totalStems + ' cành)';
}

function ensureSelectOption(selectEl, value, label) {
    if (!selectEl || !value) return;
    const exists = Array.from(selectEl.options).some(o => o.value === value);
    if (!exists) {
        selectEl.add(new Option(label, value));
    }
    selectEl.value = value;
}

// ===== ĐỒNG BỘ CHẶT BƯỚC 5 VỚI LOẠI SẢN PHẨM + SỐ LƯỢNG + CATEGORY TRONG GIỎ =====
function autoSyncSimulationFromCart() {
    const items = cartItems();
    const kind = state.productType?.kind || 'bouquet';
    state.sim.kind = kind;
    state.sim.mode = 'photo';
    const cfg = PRODUCT_SIM_CONFIG[kind] || PRODUCT_SIM_CONFIG.bouquet;

    if (qs('#simProductTitle')) qs('#simProductTitle').textContent = cfg.title;
    const photoBtn = qs('#simPhotoModeBtn');
    if (photoBtn) photoBtn.hidden = false;
    qsa('#simModeSwitch .mt-chip').forEach(b => b.classList.toggle('active', b.dataset.mode === state.sim.mode));

    const mains = items.filter(i => i.group === 'main');
    const subs = items.filter(i => i.group === 'sub');
    const leaves = items.filter(i => i.group === 'leaf');
    const packs = items.filter(i => i.group === 'packaging');
    const accs = items.filter(i => i.group === 'accessory');

    const totalMainQty = mains.reduce((s, i) => s + i.quantity, 0);
    const totalSubQty = subs.reduce((s, i) => s + i.quantity, 0);
    const totalLeafQty = leaves.reduce((s, i) => s + i.quantity, 0);

    if (qs('#simQtySummary')) {
        qs('#simQtySummary').textContent = (state.productType?.name || '') + ' · ' + totalMainQty + ' hoa chính · ' + totalSubQty + ' hoa phụ · ' + totalLeafQty + ' lá';
    }

    // 1. Mục 00 · Tổ hợp hoa trong giỏ (Ngắn gọn, không bị tràn dropdown)
    const mixSelect = qs('#simMixSelect');
    const allFlowers = [...mains, ...subs];
    const fullFlowerText = allFlowers.map(i => i.quantity + ' ' + i.name).join(' + ') || 'Chưa chọn hoa';
    const compactLabel = buildCompactFlowerLabel(allFlowers);

    const mainNames = mains.map(i => i.name.toLowerCase()).join(' ');
    const subNames = subs.map(i => i.name.toLowerCase()).join(' ');
    const hasTulip = /tulip/.test(mainNames);
    const hasNonTulipMain = mains.some(i => !/tulip/i.test(i.name));
    const hasDaisyOrTana = /cúc|tana|thạch thảo|daisy/.test(subNames);
    const hasBaby = /baby/.test(subNames);

    const allowedMixOptions = [];

    if (hasTulip && !hasNonTulipMain) {
        state.sim.mix = 'tulip-baby';
        state.sim.form = 'long';
        allowedMixOptions.push({ value: 'tulip-baby', label: 'Đang chọn: ' + compactLabel });
    } else if (hasDaisyOrTana) {
        state.sim.mix = 'rose-daisy';
        state.sim.form = 'natural';
        allowedMixOptions.push({ value: 'rose-daisy', label: 'Phom Hoa chính + Cúc: ' + compactLabel });
    } else if (subs.length === 0) {
        state.sim.mix = 'rose-only';
        state.sim.form = 'round';
        allowedMixOptions.push({ value: 'rose-only', label: 'Chỉ hoa chính: ' + compactLabel });
    } else {
        state.sim.mix = 'rose-baby';
        state.sim.form = 'round';
        allowedMixOptions.push({ value: 'rose-baby', label: 'Phom Hoa chính + Baby: ' + compactLabel });
    }

    // Nếu trong giỏ có cả Cúc Tana và Baby, hoặc cả Tulip và Hồng, cho phép đổi nhanh giữa các phom hoa có trong giỏ
    if (hasDaisyOrTana && hasBaby) {
        allowedMixOptions.push({ value: 'rose-baby', label: 'Xem phom Hoa chính + Baby (' + allFlowers.length + ' loại)' });
    }
    if (hasTulip && hasNonTulipMain) {
        allowedMixOptions.push({ value: 'tulip-baby', label: 'Xem phom Tulip chủ đạo (' + allFlowers.length + ' loại)' });
    }

    if (mixSelect) {
        mixSelect.innerHTML = allowedMixOptions.map(o =>
            '<option value="' + esc(o.value) + '">' + esc(o.label) + '</option>'
        ).join('');
        mixSelect.value = state.sim.mix;
        mixSelect.disabled = allowedMixOptions.length <= 1;
    }

    // 2. Mục 01 · Chọn kiểu dáng (Đảm bảo có đủ 3 dáng và đồng bộ tức thì)
    const formSelect = qs('#simFormSelect');
    if (qs('#labelSimForm')) qs('#labelSimForm').textContent = cfg.formLabel;
    if (formSelect) {
        formSelect.innerHTML = cfg.forms.map(f => '<option value="' + esc(f.id) + '">' + esc(f.name) + '</option>').join('');
        if (!cfg.forms.some(f => f.id === state.sim.form)) {
            state.sim.form = cfg.forms[0].id;
        }
        formSelect.value = state.sim.form;
    }

    // 3. Lấy đúng mã màu (color) từ nguyên liệu có số lượng cao nhất trong mỗi nhóm
    const topMain = [...mains].sort((a, b) => b.quantity - a.quantity)[0];
    const topSub = [...subs].sort((a, b) => b.quantity - a.quantity)[0];
    const topPack = [...packs].sort((a, b) => b.quantity - a.quantity)[0];
    const ribbonItem = accs.find(i => /ruy băng|nơ/i.test(i.name));
    const tagItem = accs.find(i => /biển|tag|thiệp/i.test(i.name));

    const flowerHex = topMain?.color || '#e4b7c1';
    const subHex = topSub?.color || '#fffef6';
    const paperHex = topPack?.color || '#ddc5a7';
    const ribbonHex = ribbonItem ? (ribbonItem.color || '#e6b2c2') : (accs.length === 0 ? 'none' : '#e6b2c2');

    state.sim.cartColors = { flower: flowerHex, sub: subHex, paper: paperHex, ribbon: ribbonHex };
    state.sim.flowerColor = flowerHex;
    state.sim.subColor = subHex;
    state.sim.paperColor = paperHex;
    state.sim.ribbonColor = ribbonHex;

    qsa('#simPresets .mt-chip').forEach(c => c.classList.toggle('active', c.dataset.preset === 'cart'));

    if (qs('#fieldMainColor')) qs('#fieldMainColor').hidden = mains.length === 0;
    if (topMain && qs('#labelMainColor')) {
        qs('#labelMainColor').textContent = 'Màu hoa chính (' + topMain.name + ' ×' + topMain.quantity + ')';
        ensureSelectOption(qs('#simFlowerColor'), flowerHex, 'Màu gốc: ' + topMain.name);
    }

    if (qs('#fieldSubColor')) qs('#fieldSubColor').hidden = subs.length === 0;
    if (topSub && qs('#labelSubColor')) {
        qs('#labelSubColor').textContent = 'Màu hoa phụ (' + topSub.name + ' ×' + topSub.quantity + ')';
        ensureSelectOption(qs('#simSubColor'), subHex, 'Màu gốc: ' + topSub.name);
    }

    if (qs('#labelPackColor')) {
        qs('#labelPackColor').textContent = topPack
            ? (cfg.packLabel + ' (' + topPack.name + ' ×' + topPack.quantity + ')')
            : (cfg.packLabel + ' (Mặc định theo tiệm)');
        ensureSelectOption(qs('#simPaperColor'), paperHex, topPack ? ('Màu gốc: ' + topPack.name) : 'Màu mặc định');
    }

    const accSummary = accs.map(i => i.name + ' ×' + i.quantity).join(', ');
    if (qs('#labelRibbonColor')) {
        qs('#labelRibbonColor').textContent = accs.length
            ? ('Màu phụ kiện (' + accSummary + ')')
            : 'Màu nơ / ruy băng';
    }
    if (qs('#simRibbonColor')) qs('#simRibbonColor').value = ribbonHex;

    // Hiển thị đầy đủ danh sách nguyên liệu ở hộp ghi chú ngữ cảnh
    const contextParts = [];
    contextParts.push('Sản phẩm: ' + (state.productType?.name || ''));
    contextParts.push('Giỏ hoa: ' + fullFlowerText);
    if (packs.length) contextParts.push('Bao bì: ' + packs.map(p => p.name).join(', '));
    if (tagItem) contextParts.push('Kèm Biển Tag chúc mừng');
    if (qs('#simContextNote')) qs('#simContextNote').textContent = contextParts.join(' · ');
}

// ===== Thanh tỷ lệ diện tích màu sắc từ giỏ hàng =====
function renderPaletteBar() {
    const items = cartItems();
    const bar = qs('#simPaletteBar');
    const swatches = qs('#simSwatches');
    if (!bar || !swatches) return;

    const weighted = items.map(i => {
        let activeColor = i.color;
        if (i.group === 'main') activeColor = state.sim.flowerColor;
        else if (i.group === 'sub') activeColor = state.sim.subColor;
        else if (i.group === 'packaging') activeColor = state.sim.paperColor;
        else if (i.group === 'accessory' && state.sim.ribbonColor !== 'none') activeColor = state.sim.ribbonColor;
        const w = i.quantity * (GROUPS[i.group]?.weight || 1);
        return { ...i, activeColor, weight: w };
    });

    const totalW = weighted.reduce((s, i) => s + i.weight, 0) || 1;
    bar.innerHTML = weighted.map(i => {
        const pct = Math.max(4, Math.round((i.weight / totalW) * 100));
        return '<span style="width:' + pct + '%;background:' + esc(i.activeColor) + '" title="' + esc(i.name) + ': ×' + i.quantity + '"></span>';
    }).join('');

    swatches.innerHTML = weighted.map(i =>
        '<span><i style="background:' + esc(i.activeColor) + '"></i>' + esc(i.name) + ' (×' + i.quantity + ')</span>'
    ).join('');
}

// ===== THUẬT TOÁN TRUYỀN MÀU BẢO TOÀN ĐỘ SÁNG & ĐỔI MÀU TOÀN BỘ GIẤY GÓI TRONG + NGOÀI =====
function hexToRgb(hex) {
    const clean = String(hex || '#ffffff').replace('#', '');
    const n = parseInt(clean, 16);
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

function clamp01(v) {
    return v < 0 ? 0 : (v > 1 ? 1 : v);
}

function transferChannelColor(r, g, b, br, bg, bb, tr, tg, tb, weight) {
    if (weight <= 0.001) return [r, g, b];
    const lumPix  = 0.299 * r  + 0.587 * g  + 0.114 * b;
    const lumBase = Math.max(18, 0.299 * br + 0.587 * bg + 0.114 * bb);
    const ratio = lumPix / lumBase;

    let nr, ng, nb;
    if (ratio <= 1.0) {
        const sCurve = Math.pow(ratio, 0.94);
        nr = tr * sCurve;
        ng = tg * sCurve;
        nb = tb * sCurve;
    } else {
        const hi = Math.min(1, (ratio - 1.0) * 0.52);
        nr = tr + (255 - tr) * hi;
        ng = tg + (255 - tg) * hi;
        nb = tb + (255 - tb) * hi;
    }

    return [
        Math.round(Math.min(255, Math.max(0, r * (1 - weight) + nr * weight))),
        Math.round(Math.min(255, Math.max(0, g * (1 - weight) + ng * weight))),
        Math.round(Math.min(255, Math.max(0, b * (1 - weight) + nb * weight)))
    ];
}

function loadSingleImage(url) {
    if (imageCache.has(url)) return Promise.resolve(imageCache.get(url));
    return new Promise((resolve, reject) => {
        const img = new Image();
        img.crossOrigin = 'anonymous';
        img.onload = () => { imageCache.set(url, img); resolve(img); };
        img.onerror = reject;
        img.src = url;
    });
}

async function loadFirstAvailableImage(candidates) {
    for (const url of candidates) {
        if (!url) continue;
        try {
            return await loadSingleImage(url);
        } catch (_) {
            // Thử đường dẫn tiếp theo
        }
    }
    throw new Error('Không tìm thấy ảnh mẫu Studio.');
}

async function drawRealMaterialOverlayDock(ctx, w, h) {
    const items = cartItems();
    const kind = state.sim.kind;
    const cfg = PRODUCT_SIM_CONFIG[kind] || PRODUCT_SIM_CONFIG.bouquet;
    const hasTag = items.some(i => /biển|tag|thiệp/i.test(i.name));

    if (cfg.containerPhoto) {
        try {
            const cImg = await loadSingleImage(cfg.containerPhoto);
            ctx.save();
            ctx.shadowColor = 'rgba(0,0,0,0.28)';
            ctx.shadowBlur = 14;
            ctx.shadowOffsetY = 4;
            ctx.fillStyle = '#ffffff';
            ctx.beginPath();
            ctx.roundRect(16, 16, 116, 134, 14);
            ctx.fill();
            ctx.shadowColor = 'transparent';

            ctx.save();
            ctx.beginPath();
            ctx.roundRect(22, 22, 104, 96, 10);
            ctx.clip();
            ctx.drawImage(cImg, 22, 22, 104, 96);
            ctx.restore();

            ctx.fillStyle = '#24312b';
            ctx.font = '600 11px system-ui';
            ctx.textAlign = 'center';
            ctx.fillText(state.productType?.name || 'Bao bì', 74, 136);
            ctx.restore();
        } catch (_) {}
    }

    if (hasTag) {
        ctx.save();
        ctx.translate(w - 92, 66);
        ctx.rotate(0.06);
        ctx.shadowColor = 'rgba(0,0,0,0.22)';
        ctx.shadowBlur = 12;
        ctx.shadowOffsetY = 4;
        ctx.fillStyle = '#fdfaf4';
        ctx.strokeStyle = '#c8ad7f';
        ctx.lineWidth = 1.5;
        ctx.beginPath();
        ctx.roundRect(-64, -28, 128, 56, 8);
        ctx.fill();
        ctx.stroke();
        ctx.shadowColor = 'transparent';

        ctx.fillStyle = '#3c5546';
        ctx.font = '600 10px system-ui';
        ctx.textAlign = 'center';
        ctx.fillText('MỘC ANH STUDIO', 0, -6);
        ctx.fillStyle = '#a6615b';
        ctx.font = 'italic 12px Georgia';
        ctx.fillText(state.occasion || 'Best Wishes', 0, 13);
        ctx.restore();
    }

    const topItems = items.slice(0, 5);
    if (!topItems.length) return;

    const dockH = 68;
    const dockY = h - dockH - 12;
    ctx.save();
    ctx.fillStyle = 'rgba(28, 38, 33, 0.82)';
    ctx.beginPath();
    ctx.roundRect(14, dockY, w - 28, dockH, 16);
    ctx.fill();

    let xCursor = 28;
    for (const item of topItems) {
        const thumbR = 22;
        const centerY = dockY + dockH / 2;
        try {
            if (item.imageUrl) {
                const mImg = await loadSingleImage(imgSrc(item.imageUrl));
                ctx.save();
                ctx.beginPath();
                ctx.arc(xCursor + thumbR, centerY, thumbR, 0, Math.PI * 2);
                ctx.clip();
                ctx.drawImage(mImg, xCursor, centerY - thumbR, thumbR * 2, thumbR * 2);
                ctx.restore();
            }
        } catch (_) {
            ctx.fillStyle = item.color || '#e4b7c1';
            ctx.beginPath();
            ctx.arc(xCursor + thumbR, centerY, thumbR, 0, Math.PI * 2);
            ctx.fill();
        }

        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.arc(xCursor + thumbR, centerY, thumbR, 0, Math.PI * 2);
        ctx.stroke();

        ctx.fillStyle = '#a6615b';
        ctx.beginPath();
        ctx.roundRect(xCursor + thumbR * 2 - 18, centerY + 4, 24, 18, 9);
        ctx.fill();
        ctx.fillStyle = '#ffffff';
        ctx.font = '600 10px system-ui';
        ctx.textAlign = 'center';
        ctx.fillText('×' + item.quantity, xCursor + thumbR * 2 - 6, centerY + 16);

        xCursor += 56;
    }

    const mainsQty = items.filter(i => i.group === 'main').reduce((s, i) => s + i.quantity, 0);
    const subsQty = items.filter(i => i.group === 'sub').reduce((s, i) => s + i.quantity, 0);
    const leavesQty = items.filter(i => i.group === 'leaf').reduce((s, i) => s + i.quantity, 0);

    ctx.fillStyle = '#ffffff';
    ctx.font = '600 12.5px system-ui';
    ctx.textAlign = 'right';
    ctx.fillText((state.productType?.name || 'Thiết kế') + ' · ' + money(state.budget), w - 28, dockY + 28);
    ctx.fillStyle = '#d3e1d8';
    ctx.font = '11.5px system-ui';
    ctx.fillText(mainsQty + ' hoa chính · ' + subsQty + ' hoa phụ · ' + leavesQty + ' lá', w - 28, dockY + 48);
    ctx.restore();
}

// ===== CHẾ ĐỘ 1: ẢNH CHỤP STUDIO THẬT (ĐỔI DÁNG BÓ TỨC THÌ & ĐỔI MÀU TOÀN BỘ GIẤY TRONG + NGOÀI) =====
async function renderPhotoStudioCanvas() {
    const canvas = qs('#simCanvas');
    if (!canvas) return;
    const emptyBox = qs('#simEmpty');
    if (emptyBox) emptyBox.hidden = true;

    // Ưu tiên khớp cả (mix + form); nếu không có thì ưu tiên khớp đúng form khách chọn để ảnh luôn thay đổi khi đổi dáng!
    const tpl = SIM_TEMPLATES.find(t => t.mix === state.sim.mix && t.form === state.sim.form)
        || SIM_TEMPLATES.find(t => t.form === state.sim.form)
        || SIM_TEMPLATES.find(t => t.mix === state.sim.mix)
        || SIM_TEMPLATES[0];

    let img;
    try {
        img = await loadFirstAvailableImage(tpl.candidates);
    } catch (_) {
        await renderRealPhotoMoodboardCanvas();
        return;
    }

    const ctx = canvas.getContext('2d', { willReadFrequently: true });
    const w = canvas.width, h = canvas.height;
    ctx.clearRect(0, 0, w, h);

    const scale = Math.max(w / img.naturalWidth, h / img.naturalHeight);
    const drawW = img.naturalWidth * scale;
    const drawH = img.naturalHeight * scale;
    const offsetX = (w - drawW) / 2;
    const offsetY = (h - drawH) / 2;
    ctx.drawImage(img, offsetX, offsetY, drawW, drawH);

    const frame = ctx.getImageData(0, 0, w, h);
    const data = frame.data;

    const [fR, fG, fB] = hexToRgb(state.sim.flowerColor);
    const [sR, sG, sB] = hexToRgb(state.sim.subColor);
    const [pR, pG, pB] = hexToRgb(state.sim.paperColor);
    const effRibbonHex = state.sim.ribbonColor === 'none' ? state.sim.paperColor : state.sim.ribbonColor;
    const [rR, rG, rB] = hexToRgb(effRibbonHex);

    const [bfR, bfG, bfB] = hexToRgb(tpl.base_colors.flower);
    const [bsR, bsG, bsB] = hexToRgb(tpl.base_colors.secondary || '#b592c7');
    const [bpR, bpG, bpB] = hexToRgb(tpl.base_colors.paper);
    const [brR, brG, brB] = hexToRgb(tpl.base_colors.ribbon);

    const fl = tpl.regions.flower;
    const rb = tpl.regions.ribbon;
    const pp = tpl.regions.paper;

    for (let y = 0; y < h; y++) {
        const ny = y / h;
        for (let x = 0; x < w; x++) {
            const nx = x / w;
            const idx = (y * w + x) * 4;
            const r = data[idx], g = data[idx + 1], b = data[idx + 2];

            // 1. Bảo vệ phông nền xám xanh Studio (Kênh Lam B lớn hơn Đỏ R và Lục G)
            if (b > r + 2 && b >= g - 2) continue;

            // 2. Bảo vệ lá bạc xanh tự nhiên (Kênh Lục G lớn hơn hoặc bằng Đỏ R)
            if (g >= r - 2 && g >= b - 4) continue;

            const inFlowerDist = Math.pow((nx - fl.cx) / fl.rx, 2) + Math.pow((ny - fl.cy) / fl.ry, 2);
            const inRibbon = nx >= rb.left && nx <= rb.right && ny >= rb.top && ny <= rb.bottom;
            const inPaper = nx >= pp.left && nx <= pp.right && ny >= pp.top && ny <= pp.bottom;

            // 3. Chỉ bảo vệ hoa Baby trắng tinh BÊN TRONG tán hoa (không chặn lớp giấy gói sáng màu bên ngoài!)
            const maxC = Math.max(r, g, b), minC = Math.min(r, g, b);
            if (inFlowerDist <= 0.88 && ny < 0.56 && minC > 214 && (maxC - minC) < 20) continue;

            const petalScore = (r - g) - 1.35 * Math.max(0, g - b);
            const pinkness = r + b - 2 * g;

            // 4. Vùng Hoa phụ tím (trong mẫu mix-rose-daisy)
            if (tpl.regions.secondary && inFlowerDist <= 1.02 && ny < 0.62 && b > r && r > g + 8 && pinkness >= 18) {
                const wSub = clamp01((pinkness - 16) / 24) * 0.88;
                const [nr, ng, nb] = transferChannelColor(r, g, b, bsR, bsG, bsB, sR, sG, sB, wSub);
                data[idx] = nr; data[idx + 1] = ng; data[idx + 2] = nb;
                continue;
            }

            // 5. Vùng Hoa chính: nằm trong tán hoa (ny < 0.60) VÀ có sắc hồng cánh hoa (petalScore >= 11)
            if (inFlowerDist <= 0.96 && ny < 0.60 && petalScore >= 11 && r > g + 12) {
                const edgeFeather = inFlowerDist > 0.78 ? clamp01((0.96 - inFlowerDist) / 0.18) : 1.0;
                const wFlower = clamp01((petalScore - 9) / 18) * edgeFeather * 0.90;
                const [nr, ng, nb] = transferChannelColor(r, g, b, bfR, bfG, bfB, fR, fG, fB, wFlower);
                data[idx] = nr; data[idx + 1] = ng; data[idx + 2] = nb;
                continue;
            }

            // 6. Vùng Nơ / Ruy băng lụa: nằm trong khung ribbon VÀ có sắc hồng lụa (petalScore >= 11)
            if (inRibbon && petalScore >= 11 && r > g + 12) {
                const wRibbon = clamp01((petalScore - 9) / 18) * 0.88;
                const [nr, ng, nb] = transferChannelColor(r, g, b, brR, brG, brB, rR, rG, rB, wRibbon);
                data[idx] = nr; data[idx + 1] = ng; data[idx + 2] = nb;
                continue;
            }

            // 7. Vùng Giấy gói (Bao gồm CẢ nếp gấp bên trong LẪN các cánh giấy sáng màu bao bên ngoài và phần chân bó)
            // Mọi điểm ảnh ấm (r > b + 4, r >= g, g >= b - 3) nằm trong khung giấy mà không phải hoa/nơ/lá
            if (inPaper && r > b + 4 && r >= g && g >= b - 3 && r > 90) {
                // Nếu điểm ảnh nằm sâu trong tâm cụm hoa (inFlowerDist < 0.75 && ny < 0.52), giảm nhẹ trọng số để tránh viền cánh hoa
                const inCoreFlower = (inFlowerDist < 0.75 && ny < 0.52) ? clamp01((10 - petalScore) / 12) : 1.0;
                const warmMask = clamp01((r - b - 3) / 14);
                const wPaper = warmMask * inCoreFlower * 0.86;
                if (wPaper > 0.02) {
                    const [nr, ng, nb] = transferChannelColor(r, g, b, bpR, bpG, bpB, pR, pG, pB, wPaper);
                    data[idx] = nr; data[idx + 1] = ng; data[idx + 2] = nb;
                }
            }
        }
    }
    ctx.putImageData(frame, 0, 0);

    await drawRealMaterialOverlayDock(ctx, w, h);
}

// ===== CHẾ ĐỘ 2: MOODBOARD ẢNH CHỤP NGUYÊN LIỆU THẬT =====
async function renderRealPhotoMoodboardCanvas() {
    const canvas = qs('#simCanvas');
    if (!canvas) return;
    if (qs('#simEmpty')) qs('#simEmpty').hidden = true;

    const ctx = canvas.getContext('2d');
    const w = canvas.width, h = canvas.height;
    const items = cartItems();
    const kind = state.sim.kind;
    const cfg = PRODUCT_SIM_CONFIG[kind] || PRODUCT_SIM_CONFIG.bouquet;

    const grad = ctx.createLinearGradient(0, 0, w, h);
    grad.addColorStop(0, '#f7f2ea');
    grad.addColorStop(1, '#e6ded2');
    ctx.fillStyle = grad;
    ctx.fillRect(0, 0, w, h);

    ctx.fillStyle = '#24312b';
    ctx.font = '600 16px Georgia, serif';
    ctx.textAlign = 'center';
    ctx.fillText('MOODBOARD NGUYÊN LIỆU THẬT · ' + (state.productType?.name || '').toUpperCase(), w / 2, 36);

    const cards = [];
    if (cfg.containerPhoto) {
        cards.push({
            title: state.productType?.name || 'Kiểu dáng',
            sub: qs('#simFormSelect')?.selectedOptions[0]?.textContent || '',
            url: cfg.containerPhoto,
            badge: money(state.budget),
            accent: state.sim.paperColor
        });
    }
    items.forEach(i => {
        cards.push({
            title: i.name,
            sub: GROUPS[i.group]?.label || '',
            url: imgSrc(i.imageUrl),
            badge: '×' + i.quantity + ' ' + (i.unit || ''),
            accent: i.color
        });
    });

    const displayCards = cards.slice(0, 6);
    const cols = displayCards.length <= 2 ? 2 : 3;
    const rows = Math.ceil(displayCards.length / cols);
    const cardW = cols === 2 ? 230 : 168;
    const cardH = rows === 1 ? 260 : 215;
    const gapX = 18, gapY = 18;
    const totalGridW = cols * cardW + (cols - 1) * gapX;
    const startX = (w - totalGridW) / 2;
    const startY = rows === 1 ? 150 : 62;

    for (let idx = 0; idx < displayCards.length; idx++) {
        const c = displayCards[idx];
        const col = idx % cols;
        const row = Math.floor(idx / cols);
        const x = startX + col * (cardW + gapX);
        const y = startY + row * (cardH + gapY);

        ctx.save();
        ctx.shadowColor = 'rgba(36, 49, 43, 0.16)';
        ctx.shadowBlur = 16;
        ctx.shadowOffsetY = 6;
        ctx.fillStyle = '#ffffff';
        ctx.beginPath();
        ctx.roundRect(x, y, cardW, cardH, 14);
        ctx.fill();
        ctx.shadowColor = 'transparent';

        const imgH = cardH - 56;
        try {
            const pic = await loadSingleImage(c.url);
            ctx.save();
            ctx.beginPath();
            ctx.roundRect(x + 8, y + 8, cardW - 16, imgH, 10);
            ctx.clip();
            const sc = Math.max((cardW - 16) / pic.naturalWidth, imgH / pic.naturalHeight);
            const dw = pic.naturalWidth * sc, dh = pic.naturalHeight * sc;
            ctx.drawImage(pic, x + 8 + ((cardW - 16) - dw) / 2, y + 8 + (imgH - dh) / 2, dw, dh);
            ctx.restore();
        } catch (_) {
            ctx.fillStyle = c.accent || '#f4e3de';
            ctx.beginPath();
            ctx.roundRect(x + 8, y + 8, cardW - 16, imgH, 10);
            ctx.fill();
        }

        ctx.fillStyle = 'rgba(36, 49, 43, 0.88)';
        ctx.beginPath();
        ctx.roundRect(x + cardW - 68, y + 16, 52, 22, 11);
        ctx.fill();
        ctx.fillStyle = '#ffffff';
        ctx.font = '600 11px system-ui';
        ctx.textAlign = 'center';
        ctx.fillText(c.badge, x + cardW - 42, y + 31);

        ctx.fillStyle = '#24312b';
        ctx.font = '600 12px system-ui';
        ctx.textAlign = 'left';
        const shortTitle = c.title.length > 20 ? c.title.slice(0, 19) + '…' : c.title;
        ctx.fillText(shortTitle, x + 12, y + imgH + 26);

        ctx.fillStyle = '#6f7b74';
        ctx.font = '11px system-ui';
        ctx.fillText(c.sub, x + 12, y + imgH + 42);
        ctx.restore();
    }

    await drawRealMaterialOverlayDock(ctx, w, h);
}

async function renderSimulationCanvas() {
    renderPaletteBar();
    const formName = qs('#simFormSelect')?.selectedOptions[0]?.textContent || state.sim.form;
    if (qs('#simCaption')) {
        qs('#simCaption').textContent = (state.productType?.name || '') + ' · ' + formName;
    }

    if (state.sim.mode === 'photo') {
        await renderPhotoStudioCanvas();
    } else {
        await renderRealPhotoMoodboardCanvas();
    }
}

function saveComparisonSlot(slot) {
    const canvas = qs('#simCanvas');
    if (!canvas || (qs('#simEmpty') && !qs('#simEmpty').hidden)) return;
    const dataUrl = canvas.toDataURL('image/png');
    const snapshot = {
        mode: state.sim.mode,
        mix: state.sim.mix,
        form: state.sim.form,
        flowerColor: state.sim.flowerColor,
        subColor: state.sim.subColor,
        paperColor: state.sim.paperColor,
        ribbonColor: state.sim.ribbonColor
    };
    const summaryText =
        'Phương án ' + slot + ': ' + (state.productType?.name || '') +
        ' (' + (qs('#simFormSelect')?.selectedOptions[0]?.textContent || '') + ')' +
        ' · Hoa ' + (qs('#simFlowerColor')?.selectedOptions[0]?.textContent || '') +
        ', Bao bì ' + (qs('#simPaperColor')?.selectedOptions[0]?.textContent || '');

    if (slot === 'A') {
        state.sim.slotA = { dataUrl, summaryText, snapshot };
        qs('#slotAImg').src = dataUrl;
        qs('#slotAText').textContent = summaryText;
        qs('#restoreSlotA').hidden = false;
    } else {
        state.sim.slotB = { dataUrl, summaryText, snapshot };
        qs('#slotBImg').src = dataUrl;
        qs('#slotBText').textContent = summaryText;
        qs('#restoreSlotB').hidden = false;
    }
    qs('#simCompare').hidden = false;
}

function restoreComparisonSlot(slot) {
    const saved = slot === 'A' ? state.sim.slotA : state.sim.slotB;
    if (!saved?.snapshot) return;
    Object.assign(state.sim, saved.snapshot);
    qs('#simMixSelect').value = state.sim.mix;
    qs('#simFormSelect').value = state.sim.form;
    qs('#simFlowerColor').value = state.sim.flowerColor;
    qs('#simSubColor').value = state.sim.subColor;
    qs('#simPaperColor').value = state.sim.paperColor;
    qs('#simRibbonColor').value = state.sim.ribbonColor;
    qsa('#simModeSwitch .mt-chip').forEach(b => b.classList.toggle('active', b.dataset.mode === state.sim.mode));
    renderSummary();
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

    if (total > state.budget) {
        msg.textContent = 'Vượt ' + money(total - state.budget);
        msg.classList.add('mt-over');
        const next = nextLevel();
        raise.hidden = next === null;
        if (next !== null) raise.textContent = 'Nâng lên ' + money(next);
    } else {
        msg.textContent = 'Còn ' + money(state.budget - total) + ' trong ngân sách';
        msg.classList.remove('mt-over');
        raise.hidden = true;
    }

    qs('#cartThumbs').innerHTML = items.slice(0, 8)
        .map(i => '<img class="mt-img" src="' + esc(imgSrc(i.imageUrl)) + '" alt="' + esc(i.name) + '" title="' + esc(i.name) + ' × ' + i.quantity + '">').join('');
    renderLimits();
}

function renderSummary() {
    const items = cartItems();
    const total = cartTotal();

    const withImg = items.filter(i => i.imageUrl);
    const collage = qs('#collage');
    collage.hidden = !withImg.length;
    qs('#moodFallback').hidden = Boolean(withImg.length);
    collage.innerHTML = withImg.map(i =>
        '<div class="mt-tile"><img class="mt-img" src="' + esc(imgSrc(i.imageUrl)) + '" alt="' + esc(i.name) + '"><span>' + esc(i.name) + ' × ' + i.quantity + '</span></div>'
    ).join('');
    qs('#moodItems').textContent = items.map(i => i.name + ' (×' + i.quantity + ')').join(' · ');

    renderSimulationCanvas();

    const formLabel = qs('#simFormSelect')?.selectedOptions[0]?.textContent || '';
    const mainColorLabel = qs('#simFlowerColor')?.selectedOptions[0]?.textContent || '';
    const packColorLabel = qs('#simPaperColor')?.selectedOptions[0]?.textContent || '';
    const ribbonColorLabel = qs('#simRibbonColor')?.selectedOptions[0]?.textContent || '';
    const simNote = (state.productType?.name || '') + ' (' + formLabel + ') — Hoa: ' + mainColorLabel + ', Bao bì: ' + packColorLabel + ', Phụ kiện: ' + ribbonColorLabel;

    qs('#designSummary').innerHTML =
        '<div class="mt-summary">' +
            '<p><b>Loại sản phẩm:</b> ' + esc(state.productType?.name ?? '—') + '</p>' +
            '<p><b>Ngân sách gói:</b> ' + money(state.budget) + '</p>' +
            '<p><b>Phong cách:</b> ' + esc(state.style) + '</p>' +
            (state.themeColor ? '<p><b>Tone màu:</b> ' + esc(state.themeColor) + '</p>' : '') +
            (state.occasion ? '<p><b>Mục đích tặng:</b> ' + esc(state.occasion) + '</p>' : '') +
            '<p><b>Bản phối mô phỏng:</b> ' + esc(simNote) + '</p>' +
            '<hr>' +
            items.map(i =>
                '<div class="mt-sum-row">' +
                    '<img class="mt-img" src="' + esc(imgSrc(i.imageUrl)) + '" alt="">' +
                    '<span>' + esc(i.name) + ' <small style="color:var(--muted)">(' + esc(GROUPS[i.group]?.label || '') + ')</small> × ' + i.quantity + '</span>' +
                    '<span>' + money(i.price * i.quantity) + '</span>' +
                '</div>'
            ).join('') +
            '<hr>' +
            '<p><b>Tạm tính nguyên liệu</b> <b style="float:right">' + money(total) + '</b></p>' +
        '</div>';
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
    const n = availableLevels().find(l => l > state.budget);
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
        state.productType = state.productTypes.find(p => p.kind === 'bouquet') || state.productTypes[0] || null;
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

// ===== Điều hướng bước =====
function goTo(n) {
    const prevStep = state.step;
    state.step = n;
    qsa('.step').forEach(x => x.classList.toggle('active', +x.dataset.step === n));
    qs('#stepNumber').textContent = String(n).padStart(2, '0');
    qs('#progressBar').style.width = (n / 6 * 100) + '%';
    if (n === 5 && prevStep <= 4) {
        autoSyncSimulationFromCart();
    }
    if (n >= 5) renderSummary();
    window.scrollTo({ top: document.querySelector('.flow').offsetTop, behavior: 'smooth' });
}

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

// ===== Khởi tạo =====
document.addEventListener('DOMContentLoaded', async () => {
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
        const q = Math.max(0, Math.min(qtyOf(id) + (b.dataset.a === 'plus' ? 1 : -1), maxQty(m)));
        q ? state.quantities.set(id, q) : state.quantities.delete(id);
        renderMaterials();
    };

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

    // ===== Sự kiện cho Studio Mô phỏng ở Bước 5 =====
    qs('#simModeSwitch')?.addEventListener('click', e => {
        const btn = e.target.closest('[data-mode]');
        if (!btn || btn.hidden) return;
        state.sim.mode = btn.dataset.mode;
        qsa('#simModeSwitch .mt-chip').forEach(b => b.classList.toggle('active', b.dataset.mode === state.sim.mode));
        renderSummary();
    });

    qs('#simMixSelect')?.addEventListener('change', e => {
        state.sim.mix = e.target.value;
        renderSummary();
    });

    qs('#simFormSelect')?.addEventListener('change', e => {
        state.sim.form = e.target.value;
        renderSummary();
    });

    const clearPresetActive = () => qsa('#simPresets .mt-chip').forEach(c => c.classList.remove('active'));

    qs('#simFlowerColor')?.addEventListener('change', e => { clearPresetActive(); state.sim.flowerColor = e.target.value; renderSummary(); });
    qs('#simSubColor')?.addEventListener('change', e => { clearPresetActive(); state.sim.subColor = e.target.value; renderSummary(); });
    qs('#simPaperColor')?.addEventListener('change', e => { clearPresetActive(); state.sim.paperColor = e.target.value; renderSummary(); });
    qs('#simRibbonColor')?.addEventListener('change', e => { clearPresetActive(); state.sim.ribbonColor = e.target.value; renderSummary(); });

    qs('#simPresets')?.addEventListener('click', e => {
        const btn = e.target.closest('[data-preset]');
        if (!btn) return;
        qsa('#simPresets .mt-chip').forEach(c => c.classList.remove('active'));
        btn.classList.add('active');
        const p = btn.dataset.preset === 'cart' ? state.sim.cartColors : SIM_PRESETS[btn.dataset.preset];
        if (!p) return;
        state.sim.flowerColor = p.flower;
        state.sim.subColor = p.sub;
        state.sim.paperColor = p.paper;
        state.sim.ribbonColor = p.ribbon;
        qs('#simFlowerColor').value = p.flower;
        qs('#simSubColor').value = p.sub;
        qs('#simPaperColor').value = p.paper;
        qs('#simRibbonColor').value = p.ribbon;
        renderSummary();
    });

    qs('#simResetBtn')?.addEventListener('click', () => {
        autoSyncSimulationFromCart();
        renderSummary();
    });

    qs('#simDownloadBtn')?.addEventListener('click', () => {
        const canvas = qs('#simCanvas');
        if (!canvas) return;
        const a = document.createElement('a');
        a.href = canvas.toDataURL('image/png');
        a.download = 'MocAnh-' + state.sim.kind + '-' + state.sim.form + '.png';
        a.click();
    });

    qs('#saveSlotA')?.addEventListener('click', () => saveComparisonSlot('A'));
    qs('#saveSlotB')?.addEventListener('click', () => saveComparisonSlot('B'));
    qs('#restoreSlotA')?.addEventListener('click', () => restoreComparisonSlot('A'));
    qs('#restoreSlotB')?.addEventListener('click', () => restoreComparisonSlot('B'));

    // Bước 6: lưu thiết kế và sang trang chốt đơn
    qs('#confirmForm').onsubmit = e => {
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
            simulation: {
                kind: state.sim.kind,
                mode: state.sim.mode,
                mix: state.sim.mix,
                form: qs('#simFormSelect')?.selectedOptions[0]?.textContent || state.sim.form,
                flowerColor: qs('#simFlowerColor')?.selectedOptions[0]?.textContent || state.sim.flowerColor,
                paperColor: qs('#simPaperColor')?.selectedOptions[0]?.textContent || state.sim.paperColor,
                ribbonColor: qs('#simRibbonColor')?.selectedOptions[0]?.textContent || state.sim.ribbonColor
            },
            total: cartTotal(),
            items: items.map(({ materialId, quantity }) => ({ materialId, quantity })),
            display: items.map(({ name, quantity, price }) => ({ name, quantity, price }))
        }));
        location.href = '/customer/checkout.html';
    };

    qs('#budgetText').textContent = money(state.budget);
    await loadBudgetLevels();
    await loadProductTypes();
    await loadMaterials();
});