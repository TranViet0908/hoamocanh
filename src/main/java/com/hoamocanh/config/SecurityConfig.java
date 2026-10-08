package com.hoamocanh.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // Tắt CSRF vì chúng ta xây dựng REST API
                .cors(withDefaults()) // Kích hoạt CORS (Sẽ dùng cấu hình từ WebConfig ở trên)
                .authorizeHttpRequests(auth -> auth
                        // Mở cửa tự do cho nhánh Khách hàng (Xem hoa, thiết kế, đặt đơn)
                        .requestMatchers("/customer/**").permitAll()

                        // Nhánh Admin bắt buộc phải có quyền ADMIN mới được truy cập
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        // Các request khác (nếu có) cũng cần xác thực
                        .anyRequest().authenticated()
                )
                // Sử dụng Basic Auth để test API trên Postman dễ dàng (nhập Username/Password ở tab Authorization)
                .httpBasic(withDefaults());

        return http.build();
    }

    // Khởi tạo tài khoản Admin mặc định để test MVP
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails admin = User.builder()
                .username("admin")
                .password(passwordEncoder.encode("MocAnh@2026")) // Mật khẩu test
                .roles("ADMIN")
                .build();

        return new InMemoryUserDetailsManager(admin);
    }

    // Thuật toán mã hóa mật khẩu chuẩn của Spring Security
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}