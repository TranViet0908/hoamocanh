package com.hoamocanh.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Spring tự ghép StaffUserDetailsService + PasswordEncoder ở trên thành bộ xác thực
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                // Phiên đăng nhập dùng cookie HttpOnly + SameSite=Lax (xem application.properties) và CORS
                // không cho gửi cookie chéo site, nên có thể tắt CSRF token cho API JSON.
                .csrf(csrf -> csrf.disable())
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository()))
                .authorizeHttpRequests(auth -> auth
                        // --- Trang quản trị ---
                        .requestMatchers("/admin/auth/login", "/admin/login.html").permitAll()
                        .requestMatchers("/admin/pricing/**", "/admin/staff/**").hasRole("ADMIN")
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "STAFF")
                        // --- Phía khách và tài nguyên tĩnh: giữ nguyên như cũ ---
                        .requestMatchers("/customer/**").permitAll()
                        .requestMatchers("/", "/error", "/css/**", "/js/**", "/images/**", "/uploads/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        // Mở trang .html khi chưa đăng nhập -> chuyển về login; gọi API -> 401 JSON
                        .authenticationEntryPoint((req, res, e) -> {
                            if (req.getRequestURI().endsWith(".html")) {
                                res.sendRedirect("/admin/login.html");
                            } else {
                                json(res, 401, "Unauthorized", "Vui lòng đăng nhập để tiếp tục.");
                            }
                        })
                        .accessDeniedHandler((req, res, e) ->
                                json(res, 403, "Forbidden", "Bạn không có quyền thực hiện thao tác này."))
                )
                .logout(l -> l
                        .logoutUrl("/admin/auth/logout")
                        .logoutSuccessHandler((req, res, a) -> res.setStatus(HttpServletResponse.SC_OK))
                        .deleteCookies("JSESSIONID")
                );
        return http.build();
    }

    private static void json(HttpServletResponse res, int status, String error, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"error\":\"" + error + "\",\"message\":\"" + message + "\"}");
    }
}
