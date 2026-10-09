package com.hoamocanh.controller.admin;

import com.hoamocanh.core.entity.Material;
import com.hoamocanh.dto.admin.MaterialReq;
import com.hoamocanh.service.admin.AdminMaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/materials") // Chuẩn hóa thêm /api
@RequiredArgsConstructor
public class AdminMaterialController {

    private final AdminMaterialService materialService;

    @GetMapping
    public ResponseEntity<List<Material>> getAll() {
        return ResponseEntity.ok(materialService.getAllMaterials());
    }

    @PostMapping
    public ResponseEntity<Material> create(@RequestBody MaterialReq req) {
        return ResponseEntity.ok(materialService.addMaterial(req));
    }

    // FIX: Đổi Long id thành Integer id cho khớp DB[cite: 19, 27]
    @PatchMapping("/{id}/stock")
    public ResponseEntity<Material> updateStock(@PathVariable Integer id, @RequestParam Integer quantity) {
        return ResponseEntity.ok(materialService.updateStock(id, quantity));
    }
}