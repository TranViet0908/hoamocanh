/* Trang Cài đặt: đổi mật khẩu (mọi nhân viên) và thông tin tiệm (chỉ ADMIN). */
(async function () {
    const { api, esc, toast } = Admin;
    const { page, user } = await Admin.mount({ active: 'settings.html', title: 'Cài đặt' });
    const isAdmin = user.role === 'ADMIN';

    page.innerHTML = `
      <div class="adm-grid" style="grid-template-columns:repeat(auto-fit,minmax(320px,1fr));align-items:start">
        <section class="adm-card">
          <h2 style="font-size:24px">Đổi mật khẩu</h2>
          <p class="adm-muted" style="margin:6px 0 18px">${esc(user.fullName)} · ${esc(user.phone)} · ${isAdmin ? 'Quản trị viên' : 'Nhân viên'}</p>
          <form id="pw" novalidate>
            <label class="adm-field">Mật khẩu hiện tại<input class="adm-input" name="cur" type="password" autocomplete="current-password" required></label>
            <label class="adm-field">Mật khẩu mới (từ 8 ký tự, có cả chữ và số)<input class="adm-input" name="np" type="password" autocomplete="new-password" required></label>
            <label class="adm-field">Nhập lại mật khẩu mới<input class="adm-input" name="np2" type="password" autocomplete="new-password" required></label>
            <p class="adm-error" id="pwErr" role="alert"></p>
            <button class="adm-btn primary" type="submit">Đổi mật khẩu</button>
          </form>
        </section>
        ${isAdmin ? `<section class="adm-card">
          <h2 style="font-size:24px">Thông tin tiệm</h2>
          <p class="adm-muted" style="margin:6px 0 18px">Hiển thị cho khách ở trang đặt hàng và trang cảm ơn (nút Zalo, gọi hotline, mã QR chuyển khoản).</p>
          <form id="shop" novalidate>
            <label class="adm-field">Hotline<input class="adm-input" name="hotline" type="tel" maxlength="20" placeholder="09xx xxx xxx"></label>
            <label class="adm-field">Số Zalo<input class="adm-input" name="zalo" type="tel" maxlength="20"></label>
            <label class="adm-field">Mã ngân hàng (VD: VCB, MB, TCB)<input class="adm-input" name="bankCode" maxlength="20"></label>
            <label class="adm-field">Số tài khoản<input class="adm-input" name="bankAccount" inputmode="numeric" maxlength="25"></label>
            <label class="adm-field">Chủ tài khoản (viết hoa, không dấu)<input class="adm-input" name="bankHolder" maxlength="60"></label>
            <p class="adm-error" id="shopErr" role="alert"></p>
            <button class="adm-btn primary" type="submit">Lưu thông tin tiệm</button>
          </form>
        </section>` : ''}
      </div>`;

    // ----- Đổi mật khẩu -----
    const pw = page.querySelector('#pw'), pwErr = page.querySelector('#pwErr');
    pw.addEventListener('submit', async e => {
        e.preventDefault();
        const f = pw.elements, btn = pw.querySelector('button');
        pwErr.textContent = '';
        if (f.np.value.length < 8 || !/[A-Za-z]/.test(f.np.value) || !/\d/.test(f.np.value)) return pwErr.textContent = 'Mật khẩu mới cần từ 8 ký tự, có cả chữ và số.';
        if (f.np.value !== f.np2.value) return pwErr.textContent = 'Hai lần nhập mật khẩu mới chưa khớp.';
        btn.disabled = true;
        try {
            await api('/account/password', { method: 'PUT', body: { currentPassword: f.cur.value, newPassword: f.np.value } });
            pw.reset(); toast('Đã đổi mật khẩu.', 'ok');
        } catch (ex) { pwErr.textContent = ex.message; }
        finally { btn.disabled = false; }
    });

    // ----- Thông tin tiệm (ADMIN) -----
    if (!isAdmin) return;
    const shop = page.querySelector('#shop'), shopErr = page.querySelector('#shopErr');
    try {
        const s = await api('/settings/shop');
        ['hotline', 'zalo', 'bankCode', 'bankAccount', 'bankHolder'].forEach(k => shop.elements[k].value = s[k] || '');
    } catch (ex) { shopErr.textContent = ex.message; }

    shop.addEventListener('submit', async e => {
        e.preventDefault();
        const btn = shop.querySelector('button'), body = {};
        ['hotline', 'zalo', 'bankCode', 'bankAccount', 'bankHolder'].forEach(k => body[k] = shop.elements[k].value.trim());
        btn.disabled = true; shopErr.textContent = '';
        try { await api('/settings/shop', { method: 'PUT', body }); toast('Đã lưu thông tin tiệm.', 'ok'); }
        catch (ex) { shopErr.textContent = ex.message; }
        finally { btn.disabled = false; }
    });
})();
