package com.hoamocanh.controller.admin;

import com.hoamocanh.service.admin.AdminVoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Mã giảm giá liên quan tiền nên chỉ ADMIN được xem và sửa (STAFF nhận 403). */
@RestController
@RequestMapping("/admin/vouchers")
@RequiredArgsConstructor
public class AdminVoucherController {

    private final AdminVoucherService service;

    @GetMapping
    public ResponseEntity<List<AdminVoucherService.Res>> list(Authentication a) {
        admin(a);
        return ResponseEntity.ok(service.list());
    }

    @PostMapping
    public ResponseEntity<AdminVoucherService.Res> create(@RequestBody AdminVoucherService.Req r, Authentication a) {
        admin(a);
        return ResponseEntity.ok(service.create(r));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminVoucherService.Res> update(@PathVariable Integer id, @RequestBody AdminVoucherService.Req r, Authentication a) {
        admin(a);
        return ResponseEntity.ok(service.update(id, r));
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<AdminVoucherService.Res> active(@PathVariable Integer id, @RequestBody Map<String, Boolean> body, Authentication a) {
        admin(a);
        return ResponseEntity.ok(service.setActive(id, Boolean.TRUE.equals(body.get("active"))));
    }

    private static void admin(Authentication a) {
        if (a.getAuthorities().stream().noneMatch(g -> "ROLE_ADMIN".equals(g.getAuthority())))
            throw new AccessDeniedException("Chỉ quản trị viên được quản lý mã giảm giá.");
    }
}