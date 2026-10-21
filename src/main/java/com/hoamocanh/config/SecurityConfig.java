package com.hoamocanh.config;

import com.hoamocanh.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;

import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, UserRepository userRepository) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                // Cho phép nhúng trang cùng origin vào <iframe> (bước 5 của design.html)
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository()))
                .addFilterAfter(new ActiveStaffFilter(userRepository), SecurityContextHolderFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // --- Trang quản trị ---
                        .requestMatchers("/admin/auth/login", "/admin/login.html").permitAll()
                        .requestMatchers("/admin/pricing/**", "/admin/staff/**", "/admin/pricing.html", "/admin/staff.html",
                                "/admin/settings/**").hasRole("ADMIN")
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "STAFF")
                        // --- Phía khách, tài nguyên tĩnh và toàn bộ module mô phỏng vn.mocanh.studio ---
                        .requestMatchers("/customer/**").permitAll()
                        .requestMatchers("/static/**", "/api/v1/**", "/gift/**", "/openapi.json", "/docs", "/studio").permitAll()
                        .requestMatchers("/", "/error", "/favicon.ico", "/css/**", "/js/**", "/images/**", "/uploads/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            String uri = req.getRequestURI();
                            if (uri.startsWith("/admin/")) {
                                if (uri.endsWith(".html")) {
                                    res.sendRedirect("/admin/login.html");
                                } else {
                                    json(res, 401, "Unauthorized", "Vui lòng đăng nhập để tiếp tục.");
                                }
                            } else if (wantsHtml(req)) {
                                // Đường dẫn không thuộc phần nào của web: hiện trang 404 (static/error/404.html), không bắt đăng nhập
                                res.sendError(HttpServletResponse.SC_NOT_FOUND);
                            } else {
                                json(res, 401, "Unauthorized", "Vui lòng đăng nhập để tiếp tục.");
                            }
                        })
                        .accessDeniedHandler((req, res, e) -> {
                            if (req.getRequestURI().endsWith(".html")) {
                                res.sendRedirect("/admin/dashboard.html");
                            } else {
                                json(res, 403, "Forbidden", "Bạn không có quyền thực hiện thao tác này.");
                            }
                        })
                )
                .logout(l -> l
                        .logoutUrl("/admin/auth/logout")
                        .logoutSuccessHandler((req, res, a) -> res.setStatus(HttpServletResponse.SC_OK))
                        .deleteCookies("JSESSIONID")
                );
        return http.build();
    }

    private static boolean wantsHtml(HttpServletRequest req) {
        String accept = req.getHeader("Accept");
        return accept != null && accept.contains("text/html");
    }

    private static void json(HttpServletResponse res, int status, String error, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"error\":\"" + error + "\",\"message\":\"" + message + "\"}");
    }
}