package com.hoamocanh.controller.admin;

import com.hoamocanh.dto.admin.*;
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

    private final AdminMaterialService materialService;

    @GetMapping
    public ResponseEntity<List<MaterialRes>> getAll() {
        return ResponseEntity.ok(materialService.getAllMaterials());
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryRes>> categories() {
        return ResponseEntity.ok(materialService.getCategories());
    }

    @PostMapping
    public ResponseEntity<MaterialRes> create(@Valid @RequestBody MaterialReq req) {
        return ResponseEntity.ok(materialService.addMaterial(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MaterialRes> update(@PathVariable Integer id, @Valid @RequestBody MaterialReq req) {
        return ResponseEntity.ok(materialService.updateMaterial(id, req));
    }

    // Đặt số lượng tồn kho (0 = đánh dấu hết hàng)
    @PatchMapping("/{id}/stock")
    public ResponseEntity<MaterialRes> updateStock(@PathVariable Integer id, @Valid @RequestBody StockReq req) {
        return ResponseEntity.ok(materialService.updateStock(id, req.stock()));
    }

    // Ẩn / hiện với khách
    @PatchMapping("/{id}/visibility")
    public ResponseEntity<MaterialRes> visibility(@PathVariable Integer id, @Valid @RequestBody VisibilityReq req) {
        return ResponseEntity.ok(materialService.setHidden(id, req.hidden()));
    }
}
