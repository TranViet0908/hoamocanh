package com.hoamocanh.config;

import com.hoamocanh.core.entity.User;
import com.hoamocanh.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chạy khi khởi động: mật khẩu nào chưa phải BCrypt (dữ liệu mẫu dạng "hash_admin123") thì băm lại.
 * Với dữ liệu mẫu, mật khẩu thật là phần sau "hash_": admin123, staff123...
 * Sau khi đăng nhập được, hãy đổi mật khẩu các tài khoản này.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordMigrationRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        int n = 0;
        for (User u : userRepository.findAll()) {
            String h = u.getPasswordHash();
            if (h == null || h.startsWith("$2")) continue;
            String raw = h.startsWith("hash_") ? h.substring(5) : h;
            u.setPasswordHash(passwordEncoder.encode(raw));
            userRepository.save(u);
            n++;
        }
        if (n > 0) log.warn("Đã băm BCrypt lại mật khẩu cho {} tài khoản.", n);
    }
}
