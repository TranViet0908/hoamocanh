package com.hoamocanh.service.admin;

import com.hoamocanh.core.entity.Material;
import com.hoamocanh.dto.admin.MaterialReq;
import com.hoamocanh.repository.MaterialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminMaterialService {

    private final MaterialRepository materialRepository;

    public List<Material> getAllMaterials() {
        return materialRepository.findAll();
    }

    @Transactional
    public Material addMaterial(MaterialReq req) {
        Material material = Material.builder()
                .name(req.getName())
                .type(req.getType())
                .supportedProductTypes(req.getSupportedProductTypes())
                .price(req.getPrice())
                .stockQuantity(req.getStockQuantity())
                // Tự động gán trạng thái dựa trên số lượng nhập kho
                .status(req.getStockQuantity() > 0 ? "AVAILABLE" : "OUT_OF_STOCK")
                .build();
        return materialRepository.save(material);
    }

    @Transactional
    public Material updateStock(Long id, Integer newStockQuantity) {
        Material material = materialRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nguyên liệu"));

        material.setStockQuantity(newStockQuantity);
        if (newStockQuantity <= 0) {
            material.setStatus("OUT_OF_STOCK");
        } else if ("OUT_OF_STOCK".equals(material.getStatus())) {
            material.setStatus("AVAILABLE");
        }
        return materialRepository.save(material);
    }
}