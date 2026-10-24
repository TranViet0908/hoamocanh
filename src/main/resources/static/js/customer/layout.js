/* Khung dùng chung cho web shop Mộc Anh: header, menu dropdown, thanh lọc, footer, giỏ hàng, dữ liệu mẫu.
   Mỗi trang gọi MA.mount('tên-trang') sau khi nạp file này. */
(function () {
  var M = window.MA = {};
  document.documentElement.classList.add('js');   // bật hiệu ứng hiện dần (.js .reveal) trong moc-anh.css
  M.esc = function (s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); };
  M.money = function (n) { return Number(n).toLocaleString('vi-VN') + 'đ'; };
  M.kinds = { bouquet: 'Bó hoa', basket: 'Giỏ hoa', stand: 'Kệ hoa', 'gift-box': 'Hộp & túi hoa', 'cut-set': 'Set mix thả bình', vase: 'Bình hoa thiết kế', 'orchid-pot': 'Hoa lan hồ điệp', wedding: 'Hoa cầm tay & hoa cưới', event: 'Hoa sự kiện', subscription: 'Hoa định kỳ', accessory: 'Phụ kiện' };
  // Menu "Kiểu dáng" theo file Excel của khách (Kệ tang chưa có mẫu trong thư viện ảnh)
  M.kindMenu = [['Bó hoa', 'bouquet'], ['Giỏ hoa', 'basket'], ['Kệ hoa', 'stand'], ['Hộp hoa', 'gift-box'], ['Túi hoa', 'gift-box'], ['Hoa cầm tay', 'wedding'], ['Set mix thả bình', 'cut-set'], ['Bình hoa thiết kế', 'vase'], ['Hoa lan hồ điệp', 'orchid-pot'], ['Kệ tang', 'funeral']];
  // Menu "Hoa theo dịp": [tên, mã, kiểu dáng gợi ý khi chưa gắn thẻ riêng cho mẫu]
  M.occ = [['Hoa tặng sinh nhật', 'birthday', ['bouquet', 'basket']], ['Hoa tặng lãnh đạo, đối tác', 'partner', ['basket', 'stand']], ['Hoa tặng người yêu', 'lover', ['bouquet', 'gift-box']], ['Hoa tặng 20/10 và 8/3', 'women', ['bouquet', 'gift-box']], ['Hoa tặng thầy cô giáo', 'teacher', ['bouquet']], ['Hoa cưới', 'wedding', ['wedding']], ['Hoa thả bình', 'vase', ['cut-set']], ['Hoa chia buồn', 'funeral', ['stand']], ['Hoa lan', 'orchid', ['orchid-pot']], ['Giáng sinh', 'xmas', ['basket']], ['Tết', 'tet', ['orchid-pot', 'basket']]];
  // Tiệm gắn mẫu vào dịp/mục trang chủ tại đây: { bestseller: ['ma-...'], occ: { birthday: ['ma-...'] } }
  M.curation = { bestseller: [], occ: {}, tones: {} };
  // Tone màu dùng chung tên với bước "Tone màu" của công cụ thiết kế
  M.tones = [['pink', 'Hồng pastel'], ['white', 'Trắng kem'], ['purple', 'Tím lavender'], ['red', 'Đỏ'], ['yellow', 'Vàng, cam'], ['mixed', 'Nhiều màu']];
  M.flash = null;
  M.WHY = ['Hoa tươi nhập mỗi ngày, kiểm tra trước khi giao', 'Tự thiết kế theo ngân sách, minh bạch nguyên liệu', 'Florist giàu kinh nghiệm thực hiện đúng mẫu', 'Giao tận nơi, nhận order trước 18g mỗi ngày', 'Hỗ trợ tư vấn qua Zalo và hotline'];

  // Danh sách mẫu nổi bật và Flash sale do quản trị viên chỉnh (GET /customer/shop/curation)
  M.loadCuration = function () {
    return M._cur || (M._cur = fetch('/customer/shop/curation').then(function (r) { return r.ok ? r.json() : null; }).then(function (d) {
      if (d) { M.curation = { bestseller: d.bestseller || [], occ: d.occasions || {}, tones: d.tones || {} }; M.flash = d.flash || null; }
      return d;
    }).catch(function () { return null; }));
  };
  M.ready = function () { return Promise.all([M.catalog(), M.loadCuration()]).then(function (r) { return r[0]; }); };
  M.isFlash = function (p) { var f = M.flash; return !!(f && f.active && f.ids.indexOf(p.id) > -1); };
  M.flashDiscount = function (cart) {
    var f = M.flash; if (!f || !f.active) return 0; var s = 0;
    cart.forEach(function (l) { if (f.ids.indexOf(l.id) > -1) s += l.price * l.qty; });
    return Math.floor(s * f.percent / 100 / 1000) * 1000;
  };

  // Thanh phân trang: el nhận HTML, cb(trangMới) được gọi khi bấm. Trang 1, trang cuối và trang hiện tại ±1 luôn hiện.
  M.pageNav = function (el, total, page, per, cb) {
    var n = Math.max(1, Math.ceil(total / per)); page = Math.min(Math.max(1, page), n);
    if (n < 2) { el.innerHTML = ''; el.className = ''; return; }
    var set = {}; [1, n, page - 1, page, page + 1].forEach(function (x) { if (x >= 1 && x <= n) set[x] = 1; });
    var nums = Object.keys(set).map(Number).sort(function (a, b) { return a - b; }), h = '', last = 0;
    nums.forEach(function (x) {
      if (x - last > 1) h += '<span aria-hidden="true" style="align-self:center">…</span>';
      h += '<button type="button" data-p="' + x + '"' + (x === page ? ' class="on" aria-current="page"' : '') + ' aria-label="Trang ' + x + '">' + x + '</button>';
      last = x;
    });
    el.className = 'pg'; el.setAttribute('role', 'navigation'); el.setAttribute('aria-label', 'Phân trang');
    el.innerHTML = '<button type="button" data-p="' + (page - 1) + '"' + (page === 1 ? ' disabled' : '') + ' aria-label="Trang trước">‹</button>' + h +
      '<button type="button" data-p="' + (page + 1) + '"' + (page === n ? ' disabled' : '') + ' aria-label="Trang sau">›</button>';
    el.onclick = function (e) { var b = e.target.closest('button[data-p]'); if (b && !b.disabled) cb(+b.dataset.p); };
  };

  // Hiện dần khi cuộn tới (thêm class "reveal" cho phần tử). Gọi lại sau khi chèn nội dung động.
  M.reveal = function (root) {
    var els = (root || document).querySelectorAll('.reveal:not(.in)');
    if (!('IntersectionObserver' in window)) { els.forEach(function (e) { e.classList.add('in'); M.count(e); }); return; }
    var io = new IntersectionObserver(function (en) {
      en.forEach(function (x) { if (x.isIntersecting) { x.target.classList.add('in'); io.unobserve(x.target); M.count(x.target); } });
    }, { threshold: .15 });
    els.forEach(function (e) { io.observe(e); });
  };
  // Số đếm tăng dần: <b data-count="939">0</b>
  M.count = function (root) {
    root.querySelectorAll('[data-count]').forEach(function (el) {
      if (el._d) return; el._d = 1;
      var to = +el.dataset.count || 0, t0 = null;
      (function step(t) { if (!t0) t0 = t; var k = Math.min(1, (t - t0) / 1200); el.textContent = Math.round(to * (1 - Math.pow(1 - k, 3))).toLocaleString('vi-VN'); if (k < 1) requestAnimationFrame(step); })(0);
      requestAnimationFrame(function s2(t) { });
    });
  };
  // Lấy n mẫu, ưu tiên mỗi kiểu dáng một mẫu để ảnh đa dạng
  M.pick = function (list, n) {
    var seen = {}, out = [];
    list.forEach(function (p) { if (out.length < n && !seen[p.kind]) { seen[p.kind] = 1; out.push(p); } });
    for (var i = 0; out.length < n && i < list.length; i++) if (out.indexOf(list[i]) < 0) out.push(list[i]);
    return out;
  };
  M.gallery = function (list) {
    return '<div class="gallery">' + list.map(function (p, i) {
      return '<figure class="reveal' + (i % 5 === 0 ? ' g-tall' : '') + '"><img src="' + M.esc(p.photo_url) + '" alt="' + M.esc(M.sample(p)) + '" loading="lazy"><figcaption>' + M.esc(M.custom(p) ? p.name : (M.kinds[p.kind] || 'Hoa')) + '</figcaption></figure>';
    }).join('') + '</div>';
  };
  var cache;
  M.catalog = function () { return cache || (cache = fetch('/api/v1/shop/catalog').then(function (r) { return r.ok ? r.json() : { products: [] }; }).then(function (d) { return d.products || []; }).catch(function () { return []; })); };
  M.minPrice = function (p) { var m = p.price_band && p.price_band.min || 0; return Math.min(1500000, Math.max(400000, Math.ceil(m / 100000) * 100000)); };
  M.byOcc = function (list, key) {
    var ids = M.curation.occ[key], o = M.occ.filter(function (x) { return x[1] === key; })[0];
    if (ids && ids.length) return list.filter(function (p) { return ids.indexOf(p.id) > -1; });
    return o ? list.filter(function (p) { return o[2].indexOf(p.kind) > -1; }) : [];
  };
  // Tên đã đặt riêng ở trang quản trị thì dùng, còn tên mặc định "Mẫu MA-xxxx" thì hiện kiểu dáng + mã
  M.custom = function (p) { return !!p.name && !/^Mẫu\s+MA-/.test(p.name); };
  M.sample = function (p) { return M.custom(p) ? p.name + ' (' + p.code + ')' : (M.kinds[p.kind] || 'Hoa') + ' ' + p.code; };
  M.zalo = function () { return fetch('/customer/shop-info').then(function (r) { return r.ok ? r.json() : {}; }).catch(function () { return {}; }); };

  M.cart = function () { try { return JSON.parse(localStorage.getItem('ma-cart') || '[]'); } catch (e) { return []; } };
  M.saveCart = function (c) { try { localStorage.setItem('ma-cart', JSON.stringify(c)); } catch (e) {} M.badge(); };
  M.add = function (p, price, qty) {
    var c = M.cart(), l = c.filter(function (x) { return x.id === p.id && x.price === price; })[0];
    if (l) l.qty = Math.min(20, l.qty + qty); else c.push({ id: p.id, name: M.sample(p), img: p.thumbnail_url, price: price, qty: qty });
    M.saveCart(c);
  };
  M.badge = function () { var n = M.cart().reduce(function (s, x) { return s + x.qty; }, 0), b = document.getElementById('cartN'); if (b) { b.textContent = n; b.hidden = !n; } };

  M.card = function (p) {
    var id = encodeURIComponent(p.id), mp = M.minPrice(p);
    return '<article class="mc">' + (M.isFlash(p) ? '<span class="fl">-' + M.flash.percent + '%</span>' : '') + '<a href="/customer/product.html?id=' + id + '"><img src="' + M.esc(p.thumbnail_url) + '" alt="' + M.esc(M.sample(p)) + '" loading="lazy" width="420" height="420"></a>' +
      '<h3>' + M.esc(M.custom(p) ? p.name : (M.kinds[p.kind] || 'Hoa')) + '</h3><p>Mã ' + M.esc(p.code) + '</p>' +
      '<div class="mc-act"><a class="b1" href="/customer/product.html?id=' + id + '#mua">Mua ngay</a><a href="/customer/design.html?sample=' + id + '&kind=' + encodeURIComponent(p.kind) + '&budget=' + mp + '">Tự thiết kế</a><a class="zl" href="#" data-zalo>Tư vấn thêm</a></div></article>';
  };
  M.section = function (title, list, href) {
    if (!list.length) return '';
    return '<section class="sec reveal"><div class="sec-h"><h2>' + M.esc(title) + '</h2>' + (href ? '<a href="' + href + '">Xem thêm</a>' : '') + '</div><div class="mg">' + list.slice(0, 8).map(M.card).join('') + '</div></section>';
  };
  M.why = function () { return '<section class="sec why reveal"><h2>Vì sao chọn chúng tôi</h2><ol>' + M.WHY.map(function (w) { return '<li>' + M.esc(w) + '</li>'; }).join('') + '</ol></section>'; };

  var CSS = '.ma-info{background:#eef1ea;color:var(--moss,#2f4538);font:12.5px/1.4 var(--sans,system-ui,sans-serif);padding:7px 16px;display:flex;gap:4px 28px;flex-wrap:wrap;justify-content:center;border-bottom:1px solid var(--line,#e6e1d4)}.ma-info span{white-space:nowrap}.ma-info a{color:inherit;font-weight:600;text-decoration:none}' +
    '.ma-h{position:sticky;top:0;z-index:40;background:rgba(253,250,245,.92);-webkit-backdrop-filter:blur(14px);backdrop-filter:blur(14px);border-bottom:1px solid var(--line,#d9d4c7);font:500 13.5px/1 var(--sans,system-ui,sans-serif)}.ma-h .in{max-width:1240px;margin:auto;min-height:68px;display:flex;align-items:center;gap:6px;padding:0 16px}.ma-h .brand{margin-right:auto;flex:none}' +
    '.ma-h nav{display:flex;align-items:center;gap:2px}.ma-h nav>a,.ma-h .dd>button{background:none;border:0;font:inherit;color:var(--moss,#2f4538);padding:10px 11px;cursor:pointer;text-decoration:none;border-radius:999px;white-space:nowrap}.ma-h nav>a:hover,.ma-h .dd:hover>button,.ma-h .dd:focus-within>button,.ma-h nav a.on{background:#eef1ea}' +
    '.ma-h .dd{position:relative}.ma-h .dm{display:none;position:absolute;left:0;top:100%;min-width:230px;background:#fff;border:1px solid var(--line,#d9d4c7);border-radius:14px;padding:8px;box-shadow:0 10px 28px rgba(0,0,0,.12)}.ma-h .dd:hover .dm,.ma-h .dd:focus-within .dm{display:block}.ma-h .dm a{display:block;padding:9px 12px;color:var(--moss,#2f4538);text-decoration:none;font-weight:400;border-radius:8px;white-space:nowrap}.ma-h .dm a:hover{background:#eef1ea}' +
    '.ma-h .cartb{color:var(--moss,#2f4538);text-decoration:none;font-size:13px;padding:9px 14px;border:1px solid var(--line,#d9d4c7);border-radius:999px;background:#fff;white-space:nowrap;display:inline-flex;align-items:center;flex:none}.ma-h .cartb:hover{border-color:var(--rose,#b25a4e)}.ma-h #cartN{background:#b25a4e;color:#fff;border-radius:99px;font-size:11px;padding:2px 7px;margin-left:6px}.ma-f.in-hero{background:none;border:0;padding:0;margin:26px 0 0;max-width:640px;animation:rise .8s ease .5s both}.ma-f.in-hero form{max-width:none;background:#fff;border:1px solid var(--line,#e6e1d4);border-radius:20px;padding:12px;box-shadow:0 10px 30px rgba(60,85,70,.1)}.ma-f.in-hero select{flex:1 1 140px}.ma-f.in-hero input{flex:1 1 100%}.ma-h .accm{display:none}.ma-h .tg{display:none;background:#fff;border:1px solid var(--line,#d9d4c7);border-radius:999px;padding:9px 14px;font:inherit;color:var(--moss,#2f4538);cursor:pointer}' +
    '.ma-f{background:#fffdf8;border-bottom:1px solid var(--line,#d9d4c7);padding:10px 16px}.ma-f form{max-width:1240px;margin:auto;display:flex;gap:10px;flex-wrap:wrap;align-items:center;font:14px var(--sans,system-ui,sans-serif)}.ma-f select,.ma-f input{padding:9px 12px;border:1px solid var(--line,#d9d4c7);border-radius:10px;background:#fff;font:inherit;width:auto;max-width:100%}.ma-f select{flex:none}.ma-f input{flex:1 1 200px;min-width:160px}.ma-f button{flex:none;padding:9px 22px;border:0;border-radius:10px;background:var(--moss,#2f4538);color:#fff;font:inherit;cursor:pointer}' +
    '.wrap{max-width:1180px;margin:auto;padding:8px 16px 40px}.sec{margin:34px 0}.sec-h{display:flex;justify-content:space-between;align-items:baseline;gap:12px}.sec h2{font:600 26px "Playfair Display",Georgia,serif;margin:0 0 14px;color:#2f4538}.sec-h a{color:#2f4538}' +
    '.mg{display:grid;grid-template-columns:repeat(4,1fr);gap:16px}.mc{background:#fff;border:1px solid #e6e1d4;border-radius:14px;overflow:hidden;font:14px "Be Vietnam Pro",system-ui,sans-serif}.pg{display:flex;gap:6px;justify-content:center;flex-wrap:wrap;margin:22px 0}.pg button{min-width:40px;height:40px;border:1px solid #d9d4c7;background:#fff;border-radius:10px;cursor:pointer;font:inherit;color:#2f4538}.pg button.on{background:#2f4538;color:#fff;border-color:#2f4538}.pg button[disabled]{opacity:.4;cursor:default}.mc{position:relative}.fl{position:absolute;top:8px;left:8px;z-index:1;background:#b25a4e;color:#fff;border-radius:99px;padding:3px 10px;font:600 12px sans-serif}.mc img{display:block;width:100%;aspect-ratio:1;object-fit:cover;background:#eef1ea}.mc h3{margin:10px 12px 0;font:600 15px "Be Vietnam Pro",sans-serif}.mc p{margin:2px 12px 8px;color:#6b6b60}' +
    '.mc-act{display:flex;flex-wrap:wrap;gap:6px;padding:0 12px 12px}.mc-act a{flex:1 1 auto;text-align:center;padding:8px 6px;border:1px solid #d9d4c7;border-radius:99px;color:#2f4538;text-decoration:none;font-size:13px}.mc-act .b1{background:#2f4538;color:#fff;border-color:#2f4538}' +
    '.why ol{display:grid;grid-template-columns:repeat(auto-fit,minmax(190px,1fr));gap:14px;padding:0;list-style:none}.why li{background:#eef1ea;border-radius:14px;padding:16px;line-height:1.5}' +
    '.ma-ft{background:#2f4538;color:#e9efe5;font:14px/1.7 "Be Vietnam Pro",system-ui,sans-serif;padding:30px 16px;margin-top:30px}.ma-ft .in{max-width:1180px;margin:auto;display:grid;grid-template-columns:2fr 1fr 1fr;gap:24px}.ma-ft a{color:#e9efe5;display:block}.ma-ft h4{margin:0 0 6px;color:#fff}.empty{padding:40px 12px;text-align:center;color:#6b6b60}' +
    '.fm{max-width:640px;font:15px "Be Vietnam Pro",system-ui,sans-serif}.fm label{display:block;margin:14px 0 4px;font-weight:600}.fm input,.fm select,.fm textarea{width:100%;box-sizing:border-box;padding:10px 12px;border:1px solid #d9d4c7;border-radius:10px;font:inherit;background:#fff}.fm .row{display:flex;gap:10px;flex-wrap:wrap}.fm .row label{flex:1 1 220px;font-weight:500;border:1px solid #d9d4c7;border-radius:10px;padding:10px 12px;display:flex;gap:8px;align-items:center;margin:6px 0;background:#fff}.fm .row input{width:auto}.fm .err{color:#9a504b;margin:12px 0}.fm .tot{background:#eef1ea;border-radius:12px;padding:14px;margin:16px 0}' +
    '@media(max-width:1120px){.ma-h .tg{display:block}.ma-h nav{display:none;position:absolute;left:0;right:0;top:100%;background:#fffdf8;flex-direction:column;align-items:stretch;gap:0;padding:8px 16px 14px;border-bottom:1px solid var(--line,#d9d4c7);max-height:75vh;overflow:auto}.ma-h nav.open{display:flex}.ma-h .dm{display:block;position:static;box-shadow:none;border:0;padding:0 0 0 12px}.ma-h .dd>button{text-align:left;width:100%}}' +
    '@media(max-width:1120px){.ma-h .accm{display:block}}' +
    '@media(max-width:640px){.ma-h .in{gap:4px;padding:0 10px}.ma-h #acc{display:none}.ma-h .cartb,.ma-h .tg{padding:8px 11px;font-size:12.5px}}' +
    '@media(max-width:860px){.mg{grid-template-columns:repeat(2,1fr)}.ma-ft .in{grid-template-columns:1fr}}';

  M.mount = function (active, opt) {
    var st = document.createElement('style'); st.textContent = CSS; document.head.appendChild(st);
    var occ = M.occ.map(function (o) { return '<a href="/customer/products.html?occasion=' + o[1] + '">' + o[0] + '</a>'; }).join('');
    var kd = M.kindMenu.map(function (k) { return '<a href="/customer/products.html?kind=' + k[1] + '">' + k[0] + '</a>'; }).join('');
    var on = function (k) { return active === k ? ' class="on"' : ''; };
    var top = document.createElement('div'); top.className = 'ma-info';
    top.innerHTML = '<span>Giờ phục vụ: 7g30–18g, Thứ Hai đến Chủ Nhật</span><span>Nhận order trước 18g mỗi ngày</span><span>Hotline (Zalo): <a href="tel:0372899123">0372 899 123</a></span>';
    var h = document.createElement('header'); h.className = 'ma-h';
    h.innerHTML = '<div class="in"><a class="brand" href="/customer/index.html"><span class="brand-mark">MA</span><span><b>MỘC ANH</b><small>flower studio</small></span></a>' +
      '<button class="tg" type="button" aria-expanded="false">Menu</button>' +
      '<nav id="mn"><a href="/customer/products.html"' + on('all') + '>Tất cả sản phẩm</a><div class="dd"><button type="button">Hoa theo dịp ▾</button><div class="dm">' + occ + '</div></div>' +
      '<div class="dd"><button type="button">Kiểu dáng ▾</button><div class="dm">' + kd + '</div></div><a href="/customer/enterprise.html"' + on('biz') + '>Hoa doanh nghiệp</a>' +
      '<a href="/customer/products.html?flash=1"' + on('flash') + '>Flash sale hôm nay</a><a href="/customer/design.html"' + on('design') + '>Tự thiết kế hoa tươi</a><a class="accm" id="accm" href="/customer/login.html">Đăng nhập</a></nav>' +
      '<a class="cartb" id="acc" href="/customer/login.html">Đăng nhập</a><a class="cartb" href="/customer/cart.html">Giỏ hàng<span id="cartN" hidden>0</span></a></div>';
    var q = new URLSearchParams(location.search);
    var f = document.createElement('div'); f.className = 'ma-f';
    f.innerHTML = '<form action="/customer/products.html" method="get"><select name="occasion" aria-label="Dịp tặng"><option value="">Dịp tặng</option>' + M.occ.map(function (o) { return '<option value="' + o[1] + '"' + (q.get('occasion') === o[1] ? ' selected' : '') + '>' + o[0] + '</option>'; }).join('') + '</select>' +
      '<select name="kind" aria-label="Kiểu dáng"><option value="">Kiểu dáng</option>' + Object.keys(M.kinds).map(function (k) { return '<option value="' + k + '"' + (q.get('kind') === k ? ' selected' : '') + '>' + M.kinds[k] + '</option>'; }).join('') + '</select>' +
      '<select name="price" aria-label="Ngân sách"><option value="">Ngân sách</option>' + [['400000-599999', '400.000đ – 599.000đ'], ['600000-999999', '600.000đ – 999.000đ'], ['1000000-', 'Từ 1.000.000đ']].map(function (o) { return '<option value="' + o[0] + '"' + (q.get('price') === o[0] ? ' selected' : '') + '>' + o[1] + '</option>'; }).join('') + '</select>' +
      '<select name="tone" id="toneSel" aria-label="Tone màu" hidden><option value="">Tone màu</option>' + M.tones.map(function (o) { return '<option value="' + o[0] + '"' + (q.get('tone') === o[0] ? ' selected' : '') + '>' + o[1] + '</option>'; }).join('') + '</select>' +
      '<input name="q" placeholder="Tìm theo tên hoặc mã mẫu" value="' + M.esc(q.get('q') || '') + '" aria-label="Tìm theo tên hoặc mã mẫu"><button type="submit">Lọc</button></form>';
    var ft = document.createElement('footer'); ft.className = 'ma-ft';
    ft.innerHTML = '<div class="in"><div><h4>Công ty TNHH Hoa Mộc Anh</h4>Đồng hành cùng bạn nuôi dưỡng các mối quan hệ bằng hoa tươi<br>Giờ phục vụ: 7g30–18g, Thứ Hai đến Chủ Nhật<br>Nhận order trước 18g mỗi ngày · Giao hàng tận nơi<br>Hình ảnh mang tính mô phỏng, sản phẩm thực tế thay đổi theo nguồn hoa tươi trong ngày.</div>' +
      '<div><h4>Về Mộc Anh</h4><a href="/customer/about.html">Giới thiệu về Mộc Anh</a><a href="/customer/order-lookup.html">Tra cứu đơn hàng</a><a href="#" data-zalo>Liên hệ</a></div>' +
      '<div><h4>Dịch vụ kèm theo</h4><a href="/customer/design.html">Tự thiết kế &amp; đặt lịch tại tiệm</a><a href="/customer/policy.html#payment">Chính sách thanh toán</a><a href="/customer/policy.html#delivery">Chính sách giao nhận</a><a href="/customer/policy.html#warranty">Chính sách bảo hành</a><a href="/customer/policy.html#signage">Dịch vụ biển, thiệp</a></div></div>';
    var fh = opt && opt.filterIn && document.querySelector(opt.filterIn);
    if (fh) { f.classList.add('in-hero'); fh.appendChild(f); }
    else if (!(opt && opt.nofilter)) document.body.prepend(f);
    document.body.prepend(top); document.body.prepend(h); document.body.appendChild(ft);
    h.querySelector('.tg').onclick = function () { var n = document.getElementById('mn'), o = n.classList.toggle('open'); this.setAttribute('aria-expanded', o); };
    // Chỉ hiện ô lọc Tone màu khi tiệm đã gắn tone cho mẫu (tránh khách lọc ra danh sách trống)
    M.loadCuration().then(function () { var t = document.getElementById('toneSel'), c = M.curation.tones || {}; if (t && Object.keys(c).some(function (k) { return c[k].length; })) t.hidden = false; });
    M.badge();
    fetch('/customer/auth/me').then(function (r) { return r.ok ? r.json() : null; }).then(function (u) { var a = document.getElementById('acc'), m = document.getElementById('accm'); if (u && a) { a.textContent = u.name || u.phone; a.href = '/customer/account.html'; if (m) { m.textContent = 'Tài khoản: ' + (u.name || u.phone); m.href = '/customer/account.html'; } } }).catch(function () {});
    M.zalo().then(function (s) {
      var z = String(s.zalo || '0372899123').replace(/\D/g, '');
      var tl = document.querySelector('.ma-info a'); if (tl && s.hotline) { tl.href = 'tel:' + String(s.hotline).replace(/[^\d+]/g, ''); tl.textContent = s.hotline; }
      document.querySelectorAll('[data-zalo]').forEach(function (a) { a.href = 'https://zalo.me/' + z; a.target = '_blank'; a.rel = 'noopener'; });
    });
  };
})();