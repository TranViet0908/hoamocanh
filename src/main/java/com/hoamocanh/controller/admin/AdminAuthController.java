package com.hoamocanh.controller.admin;

import com.hoamocanh.core.entity.User;
import com.hoamocanh.core.exception.ResourceNotFoundException;
import com.hoamocanh.dto.admin.AdminProfileRes;
import com.hoamocanh.dto.admin.LoginReq;
import com.hoamocanh.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final UserRepository userRepository;

    // Sai SĐT/mật khẩu -> AuthenticationException -> GlobalExceptionHandler trả 401
    @PostMapping("/login")
    public ResponseEntity<AdminProfileRes> login(@Valid @RequestBody LoginReq req,
                                                 HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(req.getPhone().trim(), req.getPassword()));

        // Chỉ ADMIN/STAFF mới được vào trang quản trị (bảng users có thể chứa cả khách hàng)
        boolean staff = auth.getAuthorities().stream()
                .anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN") || g.getAuthority().equals("ROLE_STAFF"));
        if (!staff) throw new org.springframework.security.authentication.BadCredentialsException("Không phải tài khoản quản trị");

        if (request.getSession(false) != null) request.changeSessionId();   // chống session fixation

        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        securityContextRepository.saveContext(ctx, request, response);

        return ResponseEntity.ok(profile(auth.getName()));
    }

    // Trang quản trị gọi khi mở để biết đã đăng nhập chưa và lấy tên/vai trò
    @GetMapping("/me")
    public ResponseEntity<AdminProfileRes> me(Authentication auth) {
        return ResponseEntity.ok(profile(auth.getName()));
    }

    private AdminProfileRes profile(String phone) {
        User u = userRepository.findByPhone(phone)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản."));
        return new AdminProfileRes(u.getFullName(), u.getPhone(), u.getRole().getName());
    }
}