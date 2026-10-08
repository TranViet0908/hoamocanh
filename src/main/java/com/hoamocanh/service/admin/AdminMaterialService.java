package com.hoamocanh.service.admin;

import com.hoamocanh.core.entity.Material;
import com.hoamocanh.core.entity.enums.MaterialStatus;
import com.hoamocanh.dto.admin.MaterialAdminReq;
import com.hoamocanh.repository.MaterialRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminMaterialService {

    private final MaterialRepository materialRepository;

    // Lấy toàn bộ nguyên liệu (Bao gồm cả các món đã bị ẩn - HIDDEN) để nhân viên quản lý
    public List<Material> getAllMaterials() {
        return materialRepository.findAll();
    }

    // Thêm mới một nguyên liệu vào hệ thống
    @Transactional
    public Material createMaterial(MaterialAdminReq req) {
        Material newMaterial = Material.builder()
                .name(req.getName())
                .type(req.getType())
                .supportedProductTypes(req.getSupportedProductTypes())
                .price(req.getPrice())
                .stockQuantity(req.getStockQuantity())
                .status(req.getStatus() != null ? req.getStatus() : MaterialStatus.AVAILABLE)
                .imageUrl(req.getImageUrl())
                .color(req.getColor())
                .build();

        return materialRepository.save(newMaterial);
    }

    // Cập nhật thông tin nguyên liệu (Giá, tồn kho, hình ảnh...)
    @Transactional
    public Material updateMaterial(Long id, MaterialAdminReq req) {
        Material existingMaterial = materialRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nguyên liệu với ID: " + id));

        existingMaterial.setName(req.getName());
        existingMaterial.setType(req.getType());
        existingMaterial.setSupportedProductTypes(req.getSupportedProductTypes());
        existingMaterial.setPrice(req.getPrice());
        existingMaterial.setStockQuantity(req.getStockQuantity());
        existingMaterial.setImageUrl(req.getImageUrl());
        existingMaterial.setColor(req.getColor());

        if (req.getStatus() != null) {
            existingMaterial.setStatus(req.getStatus());
        }

        return materialRepository.save(existingMaterial);
    }

    // Đổi trạng thái nhanh (Ví dụ: Đánh dấu Hết hàng hoặc Ẩn khỏi Web App)
    @Transactional
    public void updateMaterialStatus(Long id, MaterialStatus newStatus) {
        Material material = materialRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nguyên liệu với ID: " + id));

        material.setStatus(newStatus);
        materialRepository.save(material);
    }
}