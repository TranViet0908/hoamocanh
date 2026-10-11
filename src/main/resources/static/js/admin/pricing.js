/* Trang Mức giá & định mức (chỉ ADMIN): định mức theo mức giá, loại sản phẩm, danh mục + nhóm. */
(async function () {
    const { api, esc, money, toast } = Admin;
    const GROUPS = { MAIN: 'Hoa chính', SUB: 'Hoa phụ', LEAF: 'Lá trang trí', PACKAGING: 'Giấy gói/hộp', ACCESSORY: 'Phụ kiện', OTHER: 'Khác (không giới hạn)' };
    const FIELDS = [['maxMainFlowers', 'Hoa chính'], ['maxSubFlowers', 'Hoa phụ'], ['maxLeaves', 'Lá'], ['maxAccessories', 'Phụ kiện'], ['maxPackaging', 'Bao bì']];

    const { page, user } = await Admin.mount({ active: 'pricing.html', title: 'Mức giá & định mức' });
    if (user.role !== 'ADMIN') { page.innerHTML = '<div class="adm-card adm-muted">Chỉ quản trị viên được xem trang này.</div>'; return; }

    let rules = [], types = [], cats = [], tab = 'rules';
    page.innerHTML = `<div class="pr-tabs" id="tabs"></div><div id="pane"></div>`;
    const $ = s => page.querySelector(s);
    const num = v => v === '' ? null : Number(v);

    async function load() {
        [rules, types, cats] = await Promise.all([api('/pricing/budget-rules'), api('/pricing/product-types'), api('/pricing/categories')]);
        render();
    }
    const groupOptions = sel => Object.entries(GROUPS).map(([k, v]) => `<option value="${k}" ${k === sel ? 'selected' : ''}>${v}</option>`).join('');

    // ---------- Tab 1: mức giá ----------
    function viewRules() {
        const last = rules[rules.length - 1] || { budgetLevel: 300000, maxMainFlowers: 3, maxSubFlowers: 3, maxLeaves: 3, maxAccessories: 2, maxPackaging: 1 };
        const inputs = (r, ro) => FIELDS.map(([k, t]) => `<label>${t}<input class="adm-input pr-n" type="number" min="0" max="99" data-f="${k}" value="${r[k]}"></label>`).join('');
        return `
          <div class="pr-note">Mỗi mức giá giới hạn <b>số lượng từng nhóm nguyên liệu</b> khách được chọn. Ngoài ra hệ thống tự kiểm tra
            <b>tổng tiền nguyên liệu không vượt mức giá</b>. Khóa một mức thì khách không chọn được mức đó nữa (đơn cũ không ảnh hưởng).</div>
          <div class="adm-card" style="margin-bottom:14px"><h2 style="font-size:22px;margin-bottom:12px">Thêm mức giá</h2>
            <form id="newRule" class="pr-row" novalidate>
              <label>Mức giá (đ)<input class="adm-input" style="width:140px" name="level" type="number" min="1000" step="100000" value="${Number(last.budgetLevel) + 100000}"></label>
              ${inputs(last)}<button class="adm-btn primary" type="submit">Thêm mức</button></form>
            <p class="adm-muted" style="margin:8px 0 0;font-size:12px">Định mức được điền sẵn theo mức cao nhất hiện có, bạn chỉnh lại nếu cần.</p></div>
          <div class="adm-card"><table class="adm-table"><thead><tr><th>Mức giá</th>${FIELDS.map(f => `<th>${f[1]}</th>`).join('')}<th>Trạng thái</th><th></th></tr></thead><tbody>
            ${rules.map(r => `<tr data-id="${r.id}" style="${r.active ? '' : 'opacity:.6'}"><td data-label="Mức giá"><b>${money(r.budgetLevel)}</b></td>
              ${FIELDS.map(([k, t]) => `<td data-label="${t}"><input class="adm-input pr-n" type="number" min="0" max="99" data-f="${k}" value="${r[k]}"></td>`).join('')}
              <td data-label="Trạng thái"><span class="adm-badge ${r.active ? 'ok' : 'bad'}">${r.active ? 'Đang mở' : 'Đã khóa'}</span></td>
              <td><div style="display:flex;gap:6px"><button type="button" class="adm-btn pr-save" data-act="saveRule" disabled>Lưu</button>
                <button type="button" class="adm-btn" data-act="toggleRule">${r.active ? 'Khóa' : 'Mở lại'}</button></div></td></tr>`).join('')}
          </tbody></table></div>`;
    }

    // ---------- Tab 2: loại sản phẩm ----------
    function viewTypes() {
        return `<div class="pr-note">Chọn <b>danh mục nguyên liệu</b> mà mỗi kiểu dáng được dùng và <b>mức giá tối thiểu</b> khách phải chọn.</div><div class="pr-stack">` +
            types.map(t => `<div class="adm-card pr-card" data-id="${t.id}"><div class="adm-2col">
              <label class="adm-field">Tên loại sản phẩm<input class="adm-input" data-f="name" maxlength="100" value="${esc(t.name)}"></label>
              <label class="adm-field">Mức giá tối thiểu (đ)<input class="adm-input" data-f="minBudget" type="number" min="0" step="100000" value="${t.minBudget}"></label></div>
              <p class="adm-muted" style="margin:0">Danh mục được phép dùng</p>
              <div class="pr-cats">${cats.map(c => `<label class="pr-chk"><input type="checkbox" value="${c.id}" ${t.categoryIds.includes(c.id) ? 'checked' : ''}>${esc(c.name)}<small>${GROUPS[c.designGroup]}</small></label>`).join('')}</div>
              <button type="button" class="adm-btn primary" data-act="saveType">Lưu</button></div>`).join('') + '</div>';
    }

    // ---------- Tab 3: danh mục + nhóm ----------
    function viewCats() {
        return `<div class="pr-note"><b>Nhóm định mức</b> quyết định danh mục được tính vào giới hạn nào (hoa chính, hoa phụ, lá, bao bì, phụ kiện).
            Thêm danh mục mới chỉ cần chọn nhóm, không phải sửa code. Nhớ vào tab <b>Loại sản phẩm</b> để cho phép kiểu dáng dùng danh mục mới.</div>
          <div class="adm-card" style="margin-bottom:14px"><h2 style="font-size:22px;margin-bottom:12px">Thêm danh mục</h2>
            <form id="newCat" class="pr-row" novalidate>
              <label>Tên danh mục<input class="adm-input" name="name" maxlength="100" required></label>
              <label>Nhóm định mức<select class="adm-input" name="group">${groupOptions('MAIN')}</select></label>
              <label>Mô tả<input class="adm-input" name="description"></label>
              <button class="adm-btn primary" type="submit">Thêm</button></form></div>
          <div class="adm-card"><table class="adm-table"><thead><tr><th>Tên danh mục</th><th>Nhóm định mức</th><th>Mô tả</th><th></th></tr></thead><tbody>
            ${cats.map(c => `<tr data-id="${c.id}"><td data-label="Tên"><input class="adm-input" data-f="name" maxlength="100" value="${esc(c.name)}"></td>
              <td data-label="Nhóm"><select class="adm-input" data-f="designGroup">${groupOptions(c.designGroup)}</select></td>
              <td data-label="Mô tả"><input class="adm-input" data-f="description" value="${esc(c.description || '')}"></td>
              <td><button type="button" class="adm-btn pr-save" data-act="saveCat" disabled>Lưu</button></td></tr>`).join('')}
          </tbody></table></div>`;
    }

    function render() {
        $('#tabs').innerHTML = [['rules', 'Mức giá & định mức'], ['types', 'Loại sản phẩm'], ['cats', 'Danh mục & nhóm']]
            .map(([k, t]) => `<button type="button" data-tab="${k}" class="${k === tab ? 'on' : ''}">${t}</button>`).join('');
        $('#pane').innerHTML = { rules: viewRules, types: viewTypes, cats: viewCats }[tab]();
    }

    // ---------- Sự kiện ----------
    $('#tabs').onclick = e => { const b = e.target.closest('[data-tab]'); if (b) { tab = b.dataset.tab; render(); } };
    const pane = $('#pane');
    const read = row => { const o = {}; row.querySelectorAll('[data-f]').forEach(i => o[i.dataset.f] = i.type === 'number' ? num(i.value) : i.value.trim()); return o; };
    const guard = async (btn, fn) => {
        btn.disabled = true;
        try { await fn(); await load(); } catch (ex) { toast(ex.message, 'error'); btn.disabled = false; }
    };

    pane.addEventListener('input', e => {   // có thay đổi thì mới bật nút Lưu của dòng đó
        const row = e.target.closest('tr[data-id]');
        if (row) row.querySelector('.pr-save').disabled = false;
    });

    pane.addEventListener('click', e => {
        const btn = e.target.closest('[data-act]'); if (!btn) return;
        const holder = btn.closest('[data-id]'), id = holder && +holder.dataset.id;
        if (btn.dataset.act === 'saveRule') {
            const r = rules.find(x => x.id === id);
            guard(btn, async () => { await api('/pricing/budget-rules/' + id, { method: 'PUT', body: { budgetLevel: r.budgetLevel, ...read(holder) } }); toast('Đã lưu định mức.', 'ok'); });
        } else if (btn.dataset.act === 'toggleRule') {
            const r = rules.find(x => x.id === id);
            guard(btn, async () => { await api(`/pricing/budget-rules/${id}/active`, { method: 'PATCH', body: { active: !r.active } }); toast(r.active ? 'Đã khóa mức giá.' : 'Đã mở lại mức giá.', 'ok'); });
        } else if (btn.dataset.act === 'saveType') {
            const v = read(holder), ids = [...holder.querySelectorAll('input[type=checkbox]:checked')].map(i => +i.value);
            guard(btn, async () => { await api('/pricing/product-types/' + id, { method: 'PUT', body: { name: v.name, minBudget: v.minBudget, categoryIds: ids } }); toast('Đã lưu loại sản phẩm.', 'ok'); });
        } else if (btn.dataset.act === 'saveCat') {
            const v = read(holder);
            guard(btn, async () => { await api('/pricing/categories/' + id, { method: 'PUT', body: { name: v.name, description: v.description || null, designGroup: v.designGroup } }); toast('Đã lưu danh mục.', 'ok'); });
        }
    });

    pane.addEventListener('submit', e => {
        e.preventDefault();
        const btn = e.target.querySelector('button[type=submit]');
        if (e.target.id === 'newRule') {
            const v = read(e.target.querySelector('.pr-row') || e.target);
            const level = num(e.target.elements.level.value);
            guard(btn, async () => { await api('/pricing/budget-rules', { method: 'POST', body: { budgetLevel: level, ...v } }); toast('Đã thêm mức giá.', 'ok'); });
        } else if (e.target.id === 'newCat') {
            const f = e.target.elements;
            guard(btn, async () => { await api('/pricing/categories', { method: 'POST', body: { name: f.name.value.trim(), designGroup: f.group.value, description: f.description.value.trim() || null } }); toast('Đã thêm danh mục.', 'ok'); });
        }
    });

    try { await load(); } catch (ex) { pane.innerHTML = `<div class="adm-card adm-muted">${esc(ex.message)}</div>`; }
})();
