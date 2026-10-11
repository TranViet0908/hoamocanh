package com.hoamocanh.service.admin;

import com.hoamocanh.core.entity.User;
import com.hoamocanh.core.exception.ResourceNotFoundException;
import com.hoamocanh.dto.admin.ChangePasswordReq;
import com.hoamocanh.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void changePassword(String phone, ChangePasswordReq req) {
        User u = userRepository.findByPhone(phone)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản."));

        if (!passwordEncoder.matches(req.getCurrentPassword(), u.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu hiện tại không đúng.");
        }
        String np = req.getNewPassword();
        if (!np.matches(".*[A-Za-z].*") || !np.matches(".*\\d.*")) {
            throw new IllegalArgumentException("Mật khẩu mới cần có cả chữ và số.");
        }
        if (np.equals(req.getCurrentPassword()) || np.equals(u.getPhone())) {
            throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu cũ và số điện thoại.");
        }
        u.setPasswordHash(passwordEncoder.encode(np));
        userRepository.save(u);
    }
}
