package com.hoamocanh.controller.customer;

import com.hoamocanh.core.entity.Order;
import com.hoamocanh.core.entity.Role;
import com.hoamocanh.core.entity.User;
import com.hoamocanh.dto.customer.MyOrderRes;
import com.hoamocanh.repository.MyOrderRepository;
import com.hoamocanh.repository.RoleRepository;
import com.hoamocanh.repository.UserRepository;
import com.hoamocanh.service.customer.CustomerShopOrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Tài khoản khách hàng (vai trò CUSTOMER). Đăng nhập khách KHÔNG đi qua AuthenticationManager của trang quản trị,
 * nên khách không thể vào /admin/**. Mua hàng không bắt buộc đăng nhập.
 */
@RestController
@RequestMapping("/customer/auth")
@RequiredArgsConstructor
public class CustomerAuthController {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final SecurityContextRepository contextRepository;
    private final MyOrderRepository myOrders;
    private final CustomerShopOrderService shopOrders;

    // Chống dò mật khẩu: sai 5 lần liên tiếp thì khóa số điện thoại đó 10 phút (lưu trong bộ nhớ, mất khi khởi động lại)
    private final java.util.concurrent.ConcurrentHashMap<String, long[]> fails = new java.util.concurrent.ConcurrentHashMap<>();

    private void check(String key) {
        long[] f = fails.get(key);
        if (f != null && f[1] > System.currentTimeMillis())
            throw new IllegalArgumentException("Bạn nhập sai quá nhiều lần. Vui lòng thử lại sau 10 phút.");
    }

    private void fail(String key) {
        long now = System.currentTimeMillis();
        fails.compute(key, (k, v) -> {
            if (v == null || (v[1] != 0 && v[1] <= now)) v = new long[]{0, 0};
            if (++v[0] >= 5) { v[1] = now + 600_000; v[0] = 0; }
            return v;
        });
    }

    public record PasswordReq(String currentPassword, String newPassword) { }

    public record RegisterReq(String phone, String password, String fullName) { }
    public record LoginReq(String phone, String password) { }

    private static String norm(String p) {
        String s = p == null ? "" : p.replaceAll("[\\s.-]", "");
        return s.startsWith("+84") ? "0" + s.substring(3) : s;
    }

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<Map<String, String>> register(@RequestBody RegisterReq r, HttpServletRequest req, HttpServletResponse res) {
        String phone = norm(r.phone());
        if (!phone.matches("^0\\d{9,10}$")) throw new IllegalArgumentException("Số điện thoại chưa đúng định dạng.");
        if (r.password() == null || r.password().length() < 6 || r.password().getBytes().length > 72)
            throw new IllegalArgumentException("Mật khẩu từ 6 đến 72 ký tự.");
        if (r.fullName() == null || r.fullName().isBlank() || r.fullName().trim().length() > 100)
            throw new IllegalArgumentException("Vui lòng nhập họ tên (tối đa 100 ký tự).");
        if (users.findByPhone(phone).isPresent()) throw new IllegalArgumentException("Số điện thoại này đã được đăng ký.");
        Role role = roles.findByName("CUSTOMER").orElseGet(() -> roles.save(Role.builder().name("CUSTOMER").description("Khách hàng").build()));
        User u = users.save(User.builder().role(role).phone(phone).passwordHash(encoder.encode(r.password()))
                .fullName(r.fullName().trim()).isDeleted(false).build());
        signIn(u, req, res);
        return ResponseEntity.ok(profile(u));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody LoginReq r, HttpServletRequest req, HttpServletResponse res) {
        final String phone = norm(r.phone());
        check(phone);
        User u = users.findByPhone(phone)
                .filter(x -> !Boolean.TRUE.equals(x.getIsDeleted()))
                .filter(x -> "CUSTOMER".equals(x.getRole().getName()))
                .filter(x -> x.getPasswordHash() != null && r.password() != null && encoder.matches(r.password(), x.getPasswordHash()))
                .orElseThrow(() -> { fail(phone); return new BadCredentialsException("Sai số điện thoại hoặc mật khẩu"); });
        fails.remove(phone);
        signIn(u, req, res);
        return ResponseEntity.ok(profile(u));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication a) {
        return shopOrders.findCustomer(a).<ResponseEntity<?>>map(u -> ResponseEntity.ok(profile(u)))
                .orElseGet(() -> ResponseEntity.status(401).body(Map.of("error", "Unauthorized", "message", "Bạn chưa đăng nhập.")));
    }

    @PostMapping("/password")
    @Transactional
    public ResponseEntity<Void> changePassword(@RequestBody PasswordReq r, Authentication a) {
        User u = shopOrders.findCustomer(a).orElseThrow(() -> new BadCredentialsException("Bạn chưa đăng nhập"));
        check(u.getPhone());
        if (r.currentPassword() == null || !encoder.matches(r.currentPassword(), u.getPasswordHash())) {
            fail(u.getPhone());
            throw new IllegalArgumentException("Mật khẩu hiện tại chưa đúng.");
        }
        if (r.newPassword() == null || r.newPassword().length() < 6 || r.newPassword().getBytes().length > 72)
            throw new IllegalArgumentException("Mật khẩu mới từ 6 đến 72 ký tự.");
        u.setPasswordHash(encoder.encode(r.newPassword()));
        users.save(u);
        fails.remove(u.getPhone());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest req) {
        if (req.getSession(false) != null) req.getSession(false).invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/orders")
    @Transactional(readOnly = true)
    public ResponseEntity<?> orders(Authentication a) {
        var me = shopOrders.findCustomer(a);
        if (me.isEmpty()) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized", "message", "Bạn chưa đăng nhập."));
        User u = me.get();
        List<MyOrderRes> list = myOrders.findMine(u.getId(), u.getPhone(), PageRequest.of(0, 100)).stream().map(CustomerAuthController::row).toList();
        return ResponseEntity.ok(list);
    }

    private static MyOrderRes row(Order o) {
        BigDecimal d = o.getDiscountAmount() == null ? BigDecimal.ZERO : o.getDiscountAmount();
        BigDecimal ship = o.getShippingFee() == null ? BigDecimal.ZERO : o.getShippingFee();
        return new MyOrderRes("MA-" + o.getId(), o.getStatus(), o.getDeliveryType(),
                o.getPaymentStatus() == null ? "UNPAID" : o.getPaymentStatus(),
                o.getTotalPrice().subtract(d).add(ship).max(BigDecimal.ZERO), o.getCreatedAt());
    }

    private static Map<String, String> profile(User u) {
        return Map.of("name", u.getFullName() == null ? "" : u.getFullName(), "phone", u.getPhone());
    }

    private void signIn(User u, HttpServletRequest req, HttpServletResponse res) {
        if (req.getSession(false) != null) req.changeSessionId();   // chống session fixation
        var auth = UsernamePasswordAuthenticationToken.authenticated(u.getPhone(), null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        contextRepository.saveContext(ctx, req, res);
    }
}