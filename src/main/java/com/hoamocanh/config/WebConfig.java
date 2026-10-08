package com.hoamocanh.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // Áp dụng cho toàn bộ endpoint
                .allowedOriginPatterns("*") // Cho phép tất cả các domain gọi tới (Có thể thay bằng domain thật khi lên Production)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // Cho phép các phương thức HTTP
                .allowedHeaders("*") // Cho phép tất cả các header
                .allowCredentials(true) // Cho phép gửi cookie/token
                .maxAge(3600); // Thời gian cache kết quả pre-flight request
    }
}