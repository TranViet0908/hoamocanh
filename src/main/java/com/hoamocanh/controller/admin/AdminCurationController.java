package com.hoamocanh.controller.admin;

import com.hoamocanh.service.customer.ShopCurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Mẫu nổi bật theo mục (ADMIN và STAFF) và cấu hình Flash sale (chỉ ADMIN, vì liên quan mức giảm giá). */
@RestController
@RequestMapping("/admin/curation")
@RequiredArgsConstructor
public class AdminCurationController {

    private final ShopCurationService service;

    @GetMapping
    public ResponseEntity<ShopCurationService.State> get() {
        return ResponseEntity.ok(service.get());
    }

    @PutMapping("/lists")
    public ResponseEntity<ShopCurationService.State> saveLists(@RequestBody ShopCurationService.Lists body) {
        return ResponseEntity.ok(service.saveLists(body));
    }

    @PutMapping("/flash")
    public ResponseEntity<ShopCurationService.State> saveFlash(@RequestBody ShopCurationService.Flash body, Authentication auth) {
        if (auth.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())))
            throw new AccessDeniedException("Chỉ quản trị viên được chỉnh Flash sale.");
        return ResponseEntity.ok(service.saveFlash(body));
    }
}