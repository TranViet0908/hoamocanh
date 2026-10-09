package com.hoamocanh.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Điều hướng request gốc "/" vào đúng file index của khách hàng[cite: 30]
        registry.addViewController("/").setViewName("forward:/customer/index.html");
    }

    // BỔ SUNG: Phân luồng tài nguyên tĩnh để không xung đột với REST API
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Trỏ toàn bộ các request tìm file tĩnh vào thư mục resources/static/
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(3600);
    }
}