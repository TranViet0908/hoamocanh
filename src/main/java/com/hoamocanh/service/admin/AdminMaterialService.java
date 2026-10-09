package com.hoamocanh.service.admin;

import com.hoamocanh.core.entity.Category;
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

    // ĐÃ FIX LỖI: Bổ sung lại hàm addMaterial bị thiếu để Controller gọi
    @Transactional
    public Material addMaterial(MaterialReq req) {
        // Dùng Category có ID truyền vào thay cho String type cũ
        Category category = Category.builder().id(req.getCategoryId()).build();

        Material material = Material.builder()
                .category(category)
                .name(req.getName())
                .description(req.getDescription())
                .imageUrl(req.getImageUrl())
                .price(req.getPrice())
                .stock(req.getStock())
                .isDeleted(false)
                .build();

        return materialRepository.save(material);
    }

    @Transactional
    public Material updateStock(Integer id, Integer newStock) {
        Material material = materialRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nguyên liệu"));

        material.setStock(newStock);
        return materialRepository.save(material);
    }
}