/* Trang "Mẫu & Flash sale": chọn mẫu cho từng mục trang chủ / từng dịp, và cài đặt Flash sale (ADMIN). */
(async function () {
    const { api, esc, toast } = Admin;
    const { page, user } = await Admin.mount({ active: 'curation.html', title: 'Mẫu nổi bật & Flash sale' });
    const isAdmin = user.role === 'ADMIN';
    const KINDS = { bouquet: 'Bó hoa', basket: 'Giỏ hoa', stand: 'Kệ hoa', 'gift-box': 'Hộp & túi hoa', 'cut-set': 'Set mix thả bình', vase: 'Bình hoa thiết kế', 'orchid-pot': 'Hoa lan hồ điệp', wedding: 'Hoa cưới', event: 'Hoa sự kiện', subscription: 'Hoa định kỳ' };
    const LISTS = [['bestseller', 'Hoa bán chạy hôm nay (trang chủ)'], ['flash', 'Mẫu Flash sale hôm nay'],
        ['occ:birthday', 'Dịp: Sinh nhật'], ['occ:partner', 'Dịp: Lãnh đạo, đối tác'], ['occ:lover', 'Dịp: Người yêu'], ['occ:women', 'Dịp: 20/10 và 8/3'],
        ['occ:teacher', 'Dịp: Thầy cô giáo'], ['occ:wedding', 'Dịp: Hoa cưới'], ['occ:vase', 'Dịp: Hoa thả bình'], ['occ:funeral', 'Dịp: Hoa chia buồn'],
        ['occ:orchid', 'Dịp: Hoa lan'], ['occ:xmas', 'Dịp: Giáng sinh'], ['occ:tet', 'Dịp: Tết'],
        ['tone:pink', 'Tone màu: Hồng pastel'], ['tone:white', 'Tone màu: Trắng kem'], ['tone:purple', 'Tone màu: Tím lavender'],
        ['tone:red', 'Tone màu: Đỏ'], ['tone:yellow', 'Tone màu: Vàng, cam'], ['tone:mixed', 'Tone màu: Nhiều màu']];
    const MAX = 100, MAX_TONE = 1000, PER = 60;
    const maxOf = k => k.startsWith('tone:') ? MAX_TONE : MAX;

    let state, products = [];
    try {
        state = await api('/curation');
        const r = await fetch('/api/v1/shop/catalog');
        products = r.ok ? (await r.json()).products || [] : [];
    } catch (ex) { page.innerHTML = `<p class="adm-error">${esc(ex.message)}</p>`; return; }

    const sel = { bestseller: new Set(state.bestseller), flash: new Set(state.flashIds) };
    LISTS.slice(2).forEach(([k]) => sel[k] = new Set(k.startsWith('tone:') ? (state.tones || {})[k.slice(5)] || [] : (state.occasions || {})[k.slice(4)] || []));
    let cur = 'bestseller', kind = '', term = '', pg = 1;
    const f = state.flash;

    page.innerHTML = `
      <section class="adm-card" style="margin-bottom:18px">
        <h2 style="font-size:24px">Flash sale hôm nay</h2>
        <p class="adm-muted" style="margin:6px 0 16px">Trong khung giờ đã đặt, các mẫu được chọn ở danh sách "Mẫu Flash sale" được giảm giá cho khách. ${isAdmin ? '' : 'Chỉ quản trị viên được đổi mức giảm và giờ.'}</p>
        <form id="fl" novalidate>
          <label class="adm-field" style="display:flex;gap:10px;align-items:center"><input type="checkbox" name="enabled" ${f.enabled ? 'checked' : ''} ${isAdmin ? '' : 'disabled'} style="width:auto"> Bật Flash sale</label>
          <div style="display:flex;gap:12px;flex-wrap:wrap">
            <label class="adm-field" style="flex:1;min-width:140px">Mức giảm (%)<input class="adm-input" name="percent" type="number" min="1" max="70" value="${f.percent}" ${isAdmin ? '' : 'disabled'}></label>
            <label class="adm-field" style="flex:1;min-width:140px">Từ giờ (mỗi ngày)<input class="adm-input" name="start" type="time" value="${esc(f.start)}" ${isAdmin ? '' : 'disabled'}></label>
            <label class="adm-field" style="flex:1;min-width:140px">Đến giờ<input class="adm-input" name="end" type="time" value="${esc(f.end)}" ${isAdmin ? '' : 'disabled'}></label>
          </div>
          <p class="adm-error" id="flErr" role="alert"></p>
          ${isAdmin ? '<button class="adm-btn primary" type="submit">Lưu Flash sale</button>' : ''}
        </form>
      </section>
      <section class="adm-card">
        <h2 style="font-size:24px">Chọn mẫu cho từng mục</h2>
        <p class="adm-muted" style="margin:6px 0 14px">Bấm vào ảnh để chọn hoặc bỏ chọn. Mục nào chưa chọn mẫu nào thì web tự gợi ý theo kiểu dáng.</p>
        <div class="cu-bar">
          <select class="adm-input" id="list">${LISTS.map(([k, n]) => `<option value="${k}">${esc(n)}</option>`).join('')}</select>
          <select class="adm-input" id="kind"><option value="">Mọi kiểu dáng</option>${Object.keys(KINDS).map(k => `<option value="${k}">${esc(KINDS[k])}</option>`).join('')}</select>
          <input class="adm-input" id="q" placeholder="Tìm theo mã mẫu">
        </div>
        <p class="adm-muted" style="margin:12px 0 0"><button class="adm-btn" id="detect" type="button">Tự nhận diện tone màu từ ảnh</button> <span id="dst"></span></p>
        <p class="adm-muted" id="cnt" style="margin:12px 0 0"></p>
        <div class="cu-grid" id="grid"></div>
        <div id="pg"></div>
        <p class="adm-error" id="lsErr" role="alert"></p>
        <button class="adm-btn primary" id="save" type="button">Lưu tất cả danh sách</button>
      </section>`;

    const $ = s => page.querySelector(s);
    function view() { return products.filter(p => (!kind || p.kind === kind) && (!term || String(p.code).toLowerCase().includes(term))); }
    function draw() {
        const v = view(), s = sel[cur];
        const pages = Math.max(1, Math.ceil(v.length / PER));
        if (pg > pages) pg = pages;
        $('#cnt').textContent = `Đã chọn ${s.size}/${maxOf(cur)} mẫu cho mục này · ${v.length} mẫu (trang ${pg}/${pages})`;
        Admin.pageNav($('#pg'), v.length, pg, PER, p => { pg = p; draw(); });
        $('#grid').innerHTML = v.slice((pg - 1) * PER, pg * PER).map(p => `<button type="button" class="cu-it ${s.has(p.id) ? 'on' : ''}" data-id="${esc(p.id)}" aria-pressed="${s.has(p.id)}"><img src="${esc(p.thumbnail_url)}" alt="" loading="lazy"><small>${esc(KINDS[p.kind] || '')} ${esc(p.code)}</small></button>`).join('');
    }
    $('#grid').addEventListener('click', e => {
        const b = e.target.closest('.cu-it'); if (!b) return;
        const s = sel[cur], id = b.dataset.id;
        if (s.has(id)) s.delete(id); else if (s.size >= maxOf(cur)) return toast(`Mỗi mục tối đa ${maxOf(cur)} mẫu.`, 'bad'); else s.add(id);
        draw();
    });
    $('#list').onchange = e => { cur = e.target.value; pg = 1; draw(); };
    $('#kind').onchange = e => { kind = e.target.value; pg = 1; draw(); };
    $('#q').oninput = e => { term = e.target.value.trim().toLowerCase(); pg = 1; draw(); };

    $('#save').onclick = async () => {
        const btn = $('#save'), err = $('#lsErr'), occasions = {}, tones = {};
        LISTS.slice(2).forEach(([k]) => { if (k.startsWith('tone:')) tones[k.slice(5)] = [...sel[k]]; else occasions[k.slice(4)] = [...sel[k]]; });
        btn.disabled = true; err.textContent = '';
        try { await api('/curation/lists', { method: 'PUT', body: { bestseller: [...sel.bestseller], occasions, tones, flashIds: [...sel.flash] } }); toast('Đã lưu các danh sách.', 'ok'); }
        catch (ex) { err.textContent = ex.message; } finally { btn.disabled = false; }
    };

    const fl = $('#fl');
    fl.addEventListener('submit', async e => {
        e.preventDefault();
        if (!isAdmin) return;
        const el = fl.elements, err = $('#flErr'), btn = fl.querySelector('button');
        btn.disabled = true; err.textContent = '';
        try {
            await api('/curation/flash', { method: 'PUT', body: { enabled: el.enabled.checked, percent: Number(el.percent.value), start: el.start.value, end: el.end.value } });
            toast('Đã lưu Flash sale.', 'ok');
        } catch (ex) { err.textContent = ex.message; } finally { btn.disabled = false; }
    });
    // ----- Tự nhận diện tone màu từ ảnh (chạy ngay trong trình duyệt, kết quả chỉ là gợi ý: xem lại rồi bấm Lưu) -----
    const TONE_KEYS = ['pink', 'white', 'purple', 'red', 'yellow', 'mixed'];
    function toneOf(img) {
        const c = document.createElement('canvas'); c.width = c.height = 32;
        const x = c.getContext('2d', { willReadFrequently: true }), w = img.naturalWidth, h = img.naturalHeight;
        x.drawImage(img, w * 0.2, h * 0.15, w * 0.6, h * 0.6, 0, 0, 32, 32);   // lấy vùng giữa để bớt nền
        const d = x.getImageData(0, 0, 32, 32).data, t = { pink: 0, white: 0, purple: 0, red: 0, yellow: 0 };
        let n = 0;
        for (let i = 0; i < d.length; i += 4) {
            const r = d[i], g = d[i + 1], b = d[i + 2], mx = Math.max(r, g, b), mn = Math.min(r, g, b), v = mx / 255, s = mx ? (mx - mn) / mx : 0;
            if (v < 0.18) continue;
            if (s < 0.1) { if (v > 0.85) { t.white += 0.5; n += 0.5; } continue; }
            let hue = mx === r ? ((g - b) / (mx - mn)) % 6 : mx === g ? (b - r) / (mx - mn) + 2 : (r - g) / (mx - mn) + 4;
            hue = (hue * 60 + 360) % 360;
            if (hue >= 60 && hue < 170 && s > 0.2) continue;   // lá xanh
            let k;
            if (s < 0.3 && v > 0.8 && hue >= 20 && hue < 70) k = 'white';
            else if (hue < 15 || hue >= 345) k = (s < 0.5 && v > 0.7) ? 'pink' : 'red';
            else if (hue < 65) k = 'yellow';
            else if (hue >= 312) k = 'pink';
            else if (hue >= 215) k = 'purple';
            else continue;
            t[k]++; n++;
        }
        if (n < 20) return null;
        const r = Object.entries(t).sort((a, b) => b[1] - a[1]);
        return (r[0][1] / n < 0.45 && r[1][1] / n > 0.25) ? 'mixed' : r[0][0];
    }
    const loadImg = u => new Promise(res => { const i = new Image(); i.onload = () => res(i); i.onerror = () => res(null); i.src = u; });
    $('#detect').onclick = async () => {
        if (TONE_KEYS.some(k => sel['tone:' + k].size) && !confirm('Các danh sách Tone màu đang chọn sẽ được thay bằng kết quả nhận diện. Tiếp tục?')) return;
        const btn = $('#detect'), found = {}; TONE_KEYS.forEach(k => found[k] = new Set());
        btn.disabled = true;
        for (let i = 0; i < products.length; i += 12) {
            await Promise.all(products.slice(i, i + 12).map(async p => {
                const img = await loadImg(p.thumbnail_url); if (!img) return;
                let k = null; try { k = toneOf(img); } catch (e) { /* ảnh không đọc được */ }
                if (k) found[k].add(p.id);
            }));
            $('#dst').textContent = `Đang xử lý ${Math.min(i + 12, products.length)}/${products.length}…`;
        }
        TONE_KEYS.forEach(k => { sel['tone:' + k] = new Set([...found[k]].slice(0, MAX_TONE)); });
        $('#dst').textContent = 'Xong: ' + TONE_KEYS.map(k => `${found[k].size} mẫu ${k}`).join(', ') + '. Xem lại từng tone rồi bấm Lưu.';
        btn.disabled = false; draw();
    };
    draw();
})();