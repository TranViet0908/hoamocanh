package com.hoamocanh.service.admin;

import com.hoamocanh.core.entity.Role;
import com.hoamocanh.core.entity.User;
import com.hoamocanh.core.exception.ResourceNotFoundException;
import com.hoamocanh.dto.admin.StaffDto.*;
import com.hoamocanh.repository.RoleRepository;
import com.hoamocanh.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminStaffService {

    private static final List<String> STAFF_ROLES = List.of("ADMIN", "STAFF");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<StaffRes> list(String me) {
        return userRepository.findByRoleNameIn(STAFF_ROLES).stream()
                .sorted(Comparator.comparing(User::getId)).map(u -> toRes(u, me)).toList();
    }

    @Transactional
    public StaffRes create(CreateReq q, String me) {
        String phone = q.phone().trim();
        if (userRepository.findByPhone(phone).isPresent()) throw new IllegalArgumentException("Số điện thoại này đã có tài khoản.");
        checkPassword(q.password(), phone);
        User u = User.builder()
                .role(role(q.role())).phone(phone).fullName(q.fullName().trim())
                .email(blankToNull(q.email())).passwordHash(passwordEncoder.encode(q.password()))
                .isDeleted(false).build();
        return toRes(userRepository.save(u), me);
    }

    @Transactional
    public StaffRes update(Integer id, UpdateReq q, String me) {
        User u = findStaff(id);
        boolean self = u.getPhone().equals(me);
        String oldRole = u.getRole().getName();
        if (self && !q.role().equals(oldRole)) throw new IllegalArgumentException("Bạn không thể tự đổi vai trò của chính mình.");
        if ("ADMIN".equals(oldRole) && !"ADMIN".equals(q.role())) requireOtherActiveAdmin(u);

        u.setFullName(q.fullName().trim());
        u.setEmail(blankToNull(q.email()));
        u.setRole(role(q.role()));
        return toRes(userRepository.save(u), me);
    }

    @Transactional
    public StaffRes setActive(Integer id, boolean active, String me) {
        User u = findStaff(id);
        if (!active) {
            if (u.getPhone().equals(me)) throw new IllegalArgumentException("Bạn không thể tự khóa tài khoản của mình.");
            if ("ADMIN".equals(u.getRole().getName())) requireOtherActiveAdmin(u);
        }
        u.setIsDeleted(!active);
        return toRes(userRepository.save(u), me);
    }

    @Transactional
    public void resetPassword(Integer id, String newPassword) {
        User u = findStaff(id);
        checkPassword(newPassword, u.getPhone());
        u.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(u);
    }

    // ---------- Tiện ích ----------
    private User findStaff(Integer id) {
        return userRepository.findById(id)
                .filter(u -> STAFF_ROLES.contains(u.getRole().getName()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên."));
    }

    private Role role(String name) {
        if (!STAFF_ROLES.contains(name)) throw new IllegalArgumentException("Vai trò không hợp lệ.");
        return roleRepository.findByName(name).orElseThrow(() -> new IllegalArgumentException("Vai trò không tồn tại trong hệ thống."));
    }

    // Luôn phải còn ít nhất một quản trị viên đang hoạt động (ngoài tài khoản đang bị đổi/khóa)
    private void requireOtherActiveAdmin(User target) {
        boolean other = userRepository.findByRoleNameIn(List.of("ADMIN")).stream()
                .anyMatch(a -> !a.getId().equals(target.getId()) && !Boolean.TRUE.equals(a.getIsDeleted()));
        if (!other) throw new IllegalArgumentException("Phải còn ít nhất một quản trị viên đang hoạt động.");
    }

    private static void checkPassword(String pw, String phone) {
        if (!pw.matches(".*[A-Za-z].*") || !pw.matches(".*\\d.*")) throw new IllegalArgumentException("Mật khẩu cần có cả chữ và số.");
        if (pw.equals(phone)) throw new IllegalArgumentException("Mật khẩu không được trùng số điện thoại.");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static StaffRes toRes(User u, String me) {
        return new StaffRes(u.getId(), u.getFullName(), u.getPhone(), u.getEmail(), u.getRole().getName(),
                !Boolean.TRUE.equals(u.getIsDeleted()), u.getCreatedAt(), u.getPhone().equals(me));
    }
}