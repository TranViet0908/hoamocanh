package com.hoamocanh.config;

import com.hoamocanh.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Với mỗi request /admin/**: tài khoản trong phiên đăng nhập phải còn tồn tại, chưa bị khóa và vai trò không đổi.
 * Nhờ đó khóa nhân viên hoặc đổi vai trò có hiệu lực ngay, không phải chờ phiên hết hạn. (Không đánh dấu @Component:
 * filter được đăng ký trong SecurityConfig.)
 */
public class ActiveStaffFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public ActiveStaffFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.isAuthenticated() && !(a instanceof AnonymousAuthenticationToken)
                && req.getRequestURI().startsWith("/admin/")) {
            boolean ok = userRepository.findByPhone(a.getName())
                    .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                    .filter(u -> a.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_" + u.getRole().getName())))
                    .isPresent();
            if (!ok) {
                SecurityContextHolder.clearContext();
                HttpSession s = req.getSession(false);
                if (s != null) s.invalidate();
                if (req.getRequestURI().endsWith(".html")) {
                    res.sendRedirect("/admin/login.html");
                } else {
                    res.setStatus(401);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Tài khoản đã bị thay đổi hoặc khóa. Vui lòng đăng nhập lại.\"}");
                }
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
