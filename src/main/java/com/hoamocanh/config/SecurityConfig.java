package com.hoamocanh.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Cấp quyền cho toàn bộ API của 2 Actor
                        .requestMatchers("/customer/**", "/admin/**").permitAll()

                        // FIX: Dùng wildcard (*.html) để mở khóa toàn bộ các trang giao diện con vừa chia nhỏ
                        .requestMatchers("/", "/error", "/css/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers("/customer/*.html", "/admin/*.html").permitAll()

                        .anyRequest().authenticated()
                );
        return http.build();
    }
}