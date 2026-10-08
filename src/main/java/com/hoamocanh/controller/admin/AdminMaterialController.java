package com.hoamocanh.controller.admin;

import com.hoamocanh.core.entity.Material;
import com.hoamocanh.core.entity.enums.MaterialStatus;
import com.hoamocanh.dto.admin.MaterialAdminReq;
import com.hoamocanh.service.admin.AdminMaterialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/materials")
@RequiredArgsConstructor
public class AdminMaterialController {

    private final AdminMaterialService adminMaterialService;

    // Lấy toàn bộ danh sách kho
    @GetMapping
    public ResponseEntity<List<Material>> getAllMaterials() {
        return ResponseEntity.ok(adminMaterialService.getAllMaterials());
    }

    // Thêm mới hoa/phụ kiện
    @PostMapping
    public ResponseEntity<Material> createMaterial(@Valid @RequestBody MaterialAdminReq req) {
        return ResponseEntity.ok(adminMaterialService.createMaterial(req));
    }

    // Cập nhật thông tin hoa (VD: Đổi giá, cập nhật số lượng)
    @PutMapping("/{id}")
    public ResponseEntity<?> updateMaterial(@PathVariable Long id, @Valid @RequestBody MaterialAdminReq req) {
        try {
            return ResponseEntity.ok(adminMaterialService.updateMaterial(id, req));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Đổi trạng thái nhanh (AVAILABLE, OUT_OF_STOCK, HIDDEN)
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestParam MaterialStatus status) {
        try {
            adminMaterialService.updateMaterialStatus(id, status);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}