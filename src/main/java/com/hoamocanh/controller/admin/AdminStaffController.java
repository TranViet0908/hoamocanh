package com.hoamocanh.controller.admin;

import com.hoamocanh.dto.admin.StaffDto.*;
import com.hoamocanh.service.admin.AdminStaffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** /admin/staff/** chỉ ADMIN (đã khai báo trong SecurityConfig). */
@RestController
@RequestMapping("/admin/staff")
@RequiredArgsConstructor
public class AdminStaffController {

    private final AdminStaffService staffService;

    @GetMapping
    public ResponseEntity<List<StaffRes>> list(Authentication auth) {
        return ResponseEntity.ok(staffService.list(auth.getName()));
    }

    @PostMapping
    public ResponseEntity<StaffRes> create(@Valid @RequestBody CreateReq req, Authentication auth) {
        return ResponseEntity.ok(staffService.create(req, auth.getName()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<StaffRes> update(@PathVariable Integer id, @Valid @RequestBody UpdateReq req, Authentication auth) {
        return ResponseEntity.ok(staffService.update(id, req, auth.getName()));
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<StaffRes> setActive(@PathVariable Integer id, @Valid @RequestBody ActiveReq req, Authentication auth) {
        return ResponseEntity.ok(staffService.setActive(id, req.active(), auth.getName()));
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Map<String, String>> resetPassword(@PathVariable Integer id, @Valid @RequestBody PasswordReq req) {
        staffService.resetPassword(id, req.newPassword());
        return ResponseEntity.ok(Map.of("message", "Đã đặt lại mật khẩu."));
    }
}
