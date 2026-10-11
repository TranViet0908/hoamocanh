package com.hoamocanh.controller.admin;

import com.hoamocanh.dto.admin.ShopSettings;
import com.hoamocanh.service.admin.ShopSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Cài đặt thông tin tiệm: chỉ ADMIN (STAFF bị chặn, trả 403). */
@RestController
@RequestMapping("/admin/settings")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final ShopSettingsService shopSettingsService;

    @GetMapping("/shop")
    public ResponseEntity<ShopSettings> getShop(Authentication auth) {
        requireAdmin(auth);
        return ResponseEntity.ok(shopSettingsService.get());
    }

    @PutMapping("/shop")
    public ResponseEntity<ShopSettings> saveShop(@Valid @RequestBody ShopSettings req, Authentication auth) {
        requireAdmin(auth);
        return ResponseEntity.ok(shopSettingsService.save(req));
    }

    private static void requireAdmin(Authentication auth) {
        if (auth.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            throw new AccessDeniedException("Chỉ quản trị viên được thực hiện.");
        }
    }
}
