/* Trang "Sản phẩm mẫu": đặt tên, chọn kiểu dáng và nhập thành phần hoa cho từng mẫu trong thư viện ảnh của tiệm. */
(async function () {
    const { api, esc, toast } = Admin;
    const { page } = await Admin.mount({ active: 'products.html', title: 'Sản phẩm mẫu' });
    const KINDS = { bouquet: 'Bó hoa', basket: 'Giỏ hoa', vase: 'Bình hoa', 'gift-box': 'Hộp & túi hoa', stand: 'Kệ hoa', 'cut-set': 'Set mix thả bình', 'orchid-pot': 'Hoa lan hồ điệp', subscription: 'Hoa định kỳ', wedding: 'Hoa cưới', event: 'Hoa sự kiện', accessory: 'Phụ kiện', other: 'Khác' };
    const PER = 20;
    const custom = p => !!p.name && !/^Mẫu\s+MA-/.test(p.name);

    let products = [], pg = 1, kind = '', term = '', only = '';
    try {
        const r = await fetch('/api/v1/shop/catalog');
        products = r.ok ? (await r.json()).products || [] : [];
    } catch (ex) { page.innerHTML = `<p class="adm-error">${esc(ex.message)}</p>`; return; }

    page.innerHTML = `
      <section class="adm-card">
        <p class="adm-muted" style="margin:0 0 14px">Đặt tên và nhập thành phần để khách thấy đúng thông tin trên trang sản phẩm. Giá do khách tự chọn khi đặt hàng nên không nhập ở đây.</p>
        <div class="pr-bar">
          <input class="adm-input" id="q" placeholder="Tìm theo tên hoặc mã mẫu">
          <select class="adm-input" id="kind"><option value="">Mọi kiểu dáng</option>${Object.keys(KINDS).map(k => `<option value="${k}">${esc(KINDS[k])}</option>`).join('')}</select>
          <select class="adm-input" id="only"><option value="">Tất cả mẫu</option><option value="noing">Chưa có thành phần</option><option value="noname">Chưa đặt tên riêng</option><option value="done">Đã đặt tên và có thành phần</option></select>
        </div>
        <p class="adm-muted" id="cnt" role="status"></p>
        <div id="list"></div><div id="pg"></div>
      </section>
      <div class="pr-modal" id="md" role="dialog" aria-modal="true" aria-labelledby="mdT"><div class="pr-box">
        <img class="big" id="mImg" alt="">
        <h2 id="mdT" style="font-size:22px;margin:0 0 10px"></h2>
        <form id="f" novalidate>
          <label class="adm-field">Tên mẫu<input class="adm-input" id="fn" maxlength="160"></label>
          <label class="adm-field">Kiểu dáng<select class="adm-input" id="fk">${Object.keys(KINDS).map(k => `<option value="${k}">${esc(KINDS[k])}</option>`).join('')}</select></label>
          <label class="adm-field">Thành phần hoa (mỗi dòng một loại, ví dụ "Hoa hồng Ecuador 10 bông")<textarea class="adm-input" id="fi" rows="6" maxlength="2400"></textarea></label>
          <label class="adm-field" style="display:flex;gap:10px;align-items:center"><input type="checkbox" id="fv" style="width:auto"> Thành phần đã được tiệm kiểm tra</label>
          <p class="adm-error" id="fe" role="alert"></p>
          <div style="display:flex;gap:8px;flex-wrap:wrap"><button class="adm-btn primary" type="submit" id="fs">Lưu</button><button class="adm-btn" type="button" id="fc">Đóng</button></div>
        </form></div></div>`;

    const $ = s => page.querySelector(s);
    let cur = null;
    function view() {
        return products.filter(p => (!kind || p.kind === kind)
            && (!term || (String(p.name) + ' ' + p.code).toLowerCase().includes(term))
            && (only === '' || (only === 'noing' && !(p.ingredients || []).length) || (only === 'noname' && !custom(p)) || (only === 'done' && custom(p) && (p.ingredients || []).length)));
    }
    function draw() {
        const v = view(), pages = Math.max(1, Math.ceil(v.length / PER));
        if (pg > pages) pg = pages;
        const part = v.slice((pg - 1) * PER, pg * PER);
        $('#cnt').textContent = v.length ? `Hiển thị ${(pg - 1) * PER + 1}–${(pg - 1) * PER + part.length} / ${v.length} mẫu` : 'Không có mẫu nào phù hợp.';
        $('#list').innerHTML = part.map(p => `<div class="pr-row" data-id="${esc(p.id)}" tabindex="0" role="button">
            <img src="${esc(p.thumbnail_url)}" alt="" loading="lazy"><div class="mid"><b>${esc(custom(p) ? p.name : 'Chưa đặt tên')}</b><br><small>${esc(p.code)} · ${esc(KINDS[p.kind] || p.kind)}</small></div>
            <small>${(p.ingredients || []).length ? (p.ingredients.length + ' dòng thành phần') : 'Chưa có thành phần'}</small></div>`).join('');
        Admin.pageNav($('#pg'), v.length, pg, PER, n => { pg = n; draw(); window.scrollTo({ top: 0, behavior: 'smooth' }); });
    }
    function open(id) {
        cur = products.find(p => p.id === id); if (!cur) return;
        $('#mImg').src = cur.photo_url; $('#mdT').textContent = 'Mẫu ' + cur.code;
        $('#fn').value = custom(cur) ? cur.name : ''; $('#fk').value = KINDS[cur.kind] ? cur.kind : 'other';
        $('#fi').value = (cur.ingredients || []).join('\n'); $('#fv').checked = !!cur.ingredients_verified; $('#fe').textContent = '';
        $('#md').classList.add('on'); $('#fn').focus();
    }
    const close = () => { $('#md').classList.remove('on'); cur = null; };

    $('#list').addEventListener('click', e => { const r = e.target.closest('.pr-row'); if (r) open(r.dataset.id); });
    $('#list').addEventListener('keydown', e => { if (e.key === 'Enter') { const r = e.target.closest('.pr-row'); if (r) open(r.dataset.id); } });
    $('#q').oninput = e => { term = e.target.value.trim().toLowerCase(); pg = 1; draw(); };
    $('#kind').onchange = e => { kind = e.target.value; pg = 1; draw(); };
    $('#only').onchange = e => { only = e.target.value; pg = 1; draw(); };
    $('#fc').onclick = close;
    $('#md').addEventListener('click', e => { if (e.target === $('#md')) close(); });
    document.addEventListener('keydown', e => { if (e.key === 'Escape') close(); });

    $('#f').addEventListener('submit', async e => {
        e.preventDefault();
        if (!cur) return;
        const name = $('#fn').value.trim(), btn = $('#fs');
        if (!name) { $('#fe').textContent = 'Vui lòng nhập tên mẫu.'; return; }
        const body = { name, kind: $('#fk').value, ingredients: $('#fi').value.split('\n').map(s => s.trim()).filter(Boolean), ingredientsVerified: $('#fv').checked };
        btn.disabled = true; $('#fe').textContent = '';
        try {
            const u = await api('/shop-products/' + encodeURIComponent(cur.id), { method: 'PUT', body });
            Object.assign(cur, u && u.name ? u : { name: body.name, kind: body.kind, ingredients: body.ingredients, ingredients_verified: body.ingredientsVerified });
            toast('Đã lưu thông tin mẫu.', 'ok'); close(); draw();
        } catch (ex) { $('#fe').textContent = ex.message; } finally { btn.disabled = false; }
    });
    draw();
})();