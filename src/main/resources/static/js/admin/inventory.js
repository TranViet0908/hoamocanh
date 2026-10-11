/* Trang Kho nguyên liệu: cập nhật nhanh tồn kho, thêm/sửa, ẩn/hiện, đánh dấu hết hàng. */
(async function () {
    const { api, esc, money, toast } = Admin;
    const PLACEHOLDER = 'data:image/svg+xml;utf8,' + encodeURIComponent(
        '<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96"><rect width="100%" height="100%" fill="#f1ede4"/>' +
        '<text x="50%" y="60%" font-size="36" text-anchor="middle">🌸</text></svg>');
    const imgSrc = u => !u ? PLACEHOLDER : (/^(https?:|\/|data:)/.test(u) ? u : '/' + u);

    const STATUS = { ok: ['Đang bán', 'ok'], low: ['Sắp hết', 'warn'], out: ['Hết hàng', 'bad'], hidden: ['Đã ẩn', ''] };
    const statusOf = m => m.hidden ? 'hidden' : m.stock <= 0 ? 'out' : m.stock <= m.lowStockThreshold ? 'low' : 'ok';

    let items = [], cats = [];
    const f = { q: '', cat: '', status: '' };
    let editing = null, pendingImage = null;
    const timers = {};

    const { page } = await Admin.mount({
        active: 'inventory.html', title: 'Kho nguyên liệu',
        actions: '<button type="button" class="adm-btn primary" id="addBtn">+ Thêm nguyên liệu</button>'
    });

    page.innerHTML = `
      <div class="inv-sum" id="sum"></div>
      <div class="inv-bar">
        <input class="adm-input" id="q" type="search" placeholder="Tìm theo tên hoặc màu...">
        <select class="adm-input" id="st"><option value="">Mọi trạng thái</option><option value="ok">Đang bán</option>
          <option value="low">Sắp hết</option><option value="out">Hết hàng</option><option value="hidden">Đã ẩn</option></select>
      </div>
      <div class="inv-cats" id="cats"></div>
      <div id="list"></div>
      <div class="adm-overlay" id="ov"></div>
      <aside class="adm-drawer" id="dw" aria-hidden="true">
        <div class="adm-drawer-head"><h2 id="dwTitle"></h2><button type="button" class="adm-btn" id="dwClose">Đóng</button></div>
        <form id="mf" class="adm-drawer-body" novalidate>
          <div class="inv-pic adm-field"><img id="pic" alt=""><label>Ảnh nguyên liệu (jpg, png, webp, tối đa 5MB)
            <input type="file" id="file" accept="image/jpeg,image/png,image/webp"></label></div>
          <label class="adm-field">Tên nguyên liệu<input class="adm-input" name="name" required maxlength="150"></label>
          <label class="adm-field">Danh mục<select class="adm-input" name="categoryId" required></select></label>
          <div class="adm-2col">
            <label class="adm-field">Màu sắc<input class="adm-input" name="color" maxlength="50" placeholder="VD: Hồng pastel"></label>
            <label class="adm-field">Đơn vị tính<input class="adm-input" name="unit" maxlength="20" placeholder="cành, bó, cuộn..."></label>
          </div>
          <div class="adm-2col">
            <label class="adm-field">Giá bán (đ)<input class="adm-input" name="price" type="number" min="0" step="1000" required></label>
            <label class="adm-field">Báo sắp hết khi ≤<input class="adm-input" name="low" type="number" min="0" value="5"></label>
          </div>
          <label class="adm-field" id="stockField">Tồn kho ban đầu<input class="adm-input" name="stock" type="number" min="0" value="0"></label>
          <label class="adm-field">Mô tả<textarea class="adm-input" name="description" rows="3"></textarea></label>
          <p class="adm-error" id="mfErr" role="alert"></p>
        </form>
        <div class="adm-drawer-foot"><button type="button" class="adm-btn" id="dwCancel">Hủy</button>
          <button type="submit" form="mf" class="adm-btn primary" id="dwSave">Lưu</button></div>
      </aside>`;

    const $ = s => page.querySelector(s);
    const mf = $('#mf');

    // ---------- Hiển thị ----------
    function render() {
        const cnt = { all: items.length, low: 0, out: 0, hidden: 0 };
        items.forEach(m => { const s = statusOf(m); if (cnt[s] !== undefined) cnt[s]++; });
        $('#sum').innerHTML = [['Tất cả', cnt.all], ['Sắp hết', cnt.low], ['Hết hàng', cnt.out], ['Đang ẩn', cnt.hidden]]
            .map(([t, n]) => `<div><b>${n}</b><span>${t}</span></div>`).join('');

        $('#cats').innerHTML = [{ id: '', name: 'Tất cả' }, ...cats]
            .map(c => `<button type="button" data-cat="${c.id}" class="${String(c.id) === f.cat ? 'on' : ''}">${esc(c.name)}</button>`).join('');

        const q = f.q.toLowerCase();
        const list = items.filter(m =>
            (!f.cat || String(m.categoryId) === f.cat) &&
            (!f.status || statusOf(m) === f.status) &&
            (!q || (m.name + ' ' + (m.color || '')).toLowerCase().includes(q)));

        $('#list').innerHTML = list.length ? list.map(m => {
            const s = statusOf(m), [label, cls] = STATUS[s];
            return `<div class="inv-row ${m.hidden ? 'dim' : ''}" data-id="${m.id}">
              <img class="inv-img" src="${esc(imgSrc(m.imageUrl))}" alt="" loading="lazy" onerror="this.onerror=null;this.src='${PLACEHOLDER}'">
              <div class="inv-info"><b>${esc(m.name)}</b><small>${esc([m.categoryName, m.color, money(m.price) + (m.unit ? '/' + m.unit : '')].filter(Boolean).join(' · '))}</small></div>
              <div class="inv-stock"><button type="button" data-act="dec" aria-label="Giảm">−</button>
                <input type="number" min="0" value="${m.stock}" data-act="set" aria-label="Tồn kho ${esc(m.name)}">
                <button type="button" data-act="inc" aria-label="Tăng">+</button></div>
              <span class="adm-badge inv-badge ${cls}">${label}</span>
              <div class="inv-act">
                ${m.stock > 0 ? '<button type="button" data-act="zero">Hết hàng</button>' : ''}
                <button type="button" data-act="edit">Sửa</button>
                <button type="button" data-act="hide">${m.hidden ? 'Hiện lại' : 'Ẩn'}</button></div>
            </div>`;
        }).join('') : '<div class="adm-card inv-empty">Không có nguyên liệu nào phù hợp.</div>';
    }

    const adopt = (m, r) => { Object.assign(m, r); m._saved = r.stock; };

    async function load() {
        [items, cats] = await Promise.all([api('/materials'), api('/materials/categories')]);
        items.forEach(m => m._saved = m.stock);
        mf.categoryId.innerHTML = cats.map(c => `<option value="${c.id}">${esc(c.name)}</option>`).join('');
        render();
    }

    // ---------- Tồn kho: đổi ngay trên màn hình, gửi server sau 0,5 giây ----------
    function setStock(m, value) {
        m.stock = Math.max(0, Math.floor(Number(value) || 0));
        render();
        clearTimeout(timers[m.id]);
        timers[m.id] = setTimeout(async () => {
            try { adopt(m, await api(`/materials/${m.id}/stock`, { method: 'PATCH', body: { stock: m.stock } })); }
            catch (e) { m.stock = m._saved; toast(e.message, 'error'); }
            render();
        }, 500);
    }

    async function toggleHidden(m) {
        try {
            adopt(m, await api(`/materials/${m.id}/visibility`, { method: 'PATCH', body: { hidden: !m.hidden } }));
            toast(m.hidden ? 'Đã ẩn khỏi trang khách.' : 'Đã hiện lại cho khách.', 'ok');
            render();
        } catch (e) { toast(e.message, 'error'); }
    }

    $('#list').addEventListener('click', e => {
        const btn = e.target.closest('[data-act]'); if (!btn || btn.tagName === 'INPUT') return;
        const m = items.find(x => x.id === +btn.closest('.inv-row').dataset.id);
        ({ inc: () => setStock(m, m.stock + 1), dec: () => setStock(m, m.stock - 1), zero: () => setStock(m, 0),
           edit: () => openForm(m), hide: () => toggleHidden(m) })[btn.dataset.act]?.();
    });
    $('#list').addEventListener('change', e => {
        if (e.target.dataset.act !== 'set') return;
        setStock(items.find(x => x.id === +e.target.closest('.inv-row').dataset.id), e.target.value);
    });

    // ---------- Bộ lọc ----------
    $('#q').oninput = e => { f.q = e.target.value.trim(); render(); };
    $('#st').onchange = e => { f.status = e.target.value; render(); };
    $('#cats').onclick = e => { const b = e.target.closest('[data-cat]'); if (b) { f.cat = b.dataset.cat; render(); } };

    // ---------- Thêm / sửa ----------
    const dw = $('#dw'), ov = $('#ov');
    function openForm(m) {
        editing = m || null; pendingImage = m ? m.imageUrl : null;
        mf.reset();
        $('#dwTitle').textContent = m ? 'Sửa nguyên liệu' : 'Thêm nguyên liệu';
        $('#stockField').hidden = !!m;   // tồn kho của hàng đã có chỉnh ở danh sách
        $('#mfErr').textContent = '';
        if (m) {
            mf.name.value = m.name; mf.categoryId.value = m.categoryId; mf.color.value = m.color || '';
            mf.unit.value = m.unit || ''; mf.price.value = m.price; mf.low.value = m.lowStockThreshold;
            mf.description.value = m.description || '';
        } else { mf.unit.value = 'cành'; }
        $('#pic').src = imgSrc(pendingImage);
        dw.classList.add('show'); ov.classList.add('show'); dw.setAttribute('aria-hidden', 'false');
        mf.name.focus();
    }
    function closeForm() { dw.classList.remove('show'); ov.classList.remove('show'); dw.setAttribute('aria-hidden', 'true'); }
    document.getElementById('addBtn').onclick = () => openForm(null);
    $('#dwClose').onclick = $('#dwCancel').onclick = ov.onclick = closeForm;
    document.addEventListener('keydown', e => { if (e.key === 'Escape') closeForm(); });

    $('#file').onchange = async e => {
        const file = e.target.files[0]; if (!file) return;
        const fd = new FormData(); fd.append('file', file);
        $('#dwSave').disabled = true;
        try { pendingImage = (await api('/uploads/material-image', { method: 'POST', body: fd })).url; $('#pic').src = imgSrc(pendingImage); }
        catch (err) { toast(err.message, 'error'); e.target.value = ''; }
        finally { $('#dwSave').disabled = false; }
    };

    mf.addEventListener('submit', async e => {
        e.preventDefault();
        const err = $('#mfErr'), btn = $('#dwSave');
        if (!mf.name.value.trim()) return err.textContent = 'Vui lòng nhập tên nguyên liệu.';
        if (mf.price.value === '' || +mf.price.value < 0) return err.textContent = 'Vui lòng nhập giá bán hợp lệ.';
        const body = {
            categoryId: +mf.categoryId.value, name: mf.name.value.trim(), description: mf.description.value.trim() || null,
            imageUrl: pendingImage || null, price: +mf.price.value, color: mf.color.value.trim() || null,
            unit: mf.unit.value.trim() || null, lowStockThreshold: +mf.low.value || 0
        };
        if (!editing) body.stock = Math.max(0, +mf.stock.value || 0);
        btn.disabled = true; err.textContent = '';
        try {
            await api(editing ? '/materials/' + editing.id : '/materials', { method: editing ? 'PUT' : 'POST', body });
            closeForm(); await load();
            toast(editing ? 'Đã lưu thay đổi.' : 'Đã thêm nguyên liệu.', 'ok');
        } catch (ex) { err.textContent = ex.message; }
        finally { btn.disabled = false; }
    });

    try { await load(); } catch (e) { $('#list').innerHTML = `<div class="adm-card inv-empty">${esc(e.message)}</div>`; }
})();
