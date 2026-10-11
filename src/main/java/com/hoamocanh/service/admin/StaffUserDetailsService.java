package com.hoamocanh.service.admin;

import com.hoamocanh.core.entity.User;
import com.hoamocanh.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;

/** Đăng nhập trang quản trị bằng số điện thoại. Chỉ nhận tài khoản ADMIN / STAFF chưa bị xóa. */
@Service
@RequiredArgsConstructor
public class StaffUserDetailsService implements UserDetailsService {

    private static final Set<String> STAFF_ROLES = Set.of("ADMIN", "STAFF");

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String phone) throws UsernameNotFoundException {
        User u = userRepository.findByPhone(phone == null ? "" : phone.trim())
                .filter(x -> !Boolean.TRUE.equals(x.getIsDeleted()))
                .filter(x -> x.getPasswordHash() != null && STAFF_ROLES.contains(x.getRole().getName()))
                .orElseThrow(() -> new UsernameNotFoundException("Không phải tài khoản nhân viên"));

        return org.springframework.security.core.userdetails.User
                .withUsername(u.getPhone())
                .password(u.getPasswordHash())
                .roles(u.getRole().getName())   // -> ROLE_ADMIN / ROLE_STAFF
                .build();
    }
}
