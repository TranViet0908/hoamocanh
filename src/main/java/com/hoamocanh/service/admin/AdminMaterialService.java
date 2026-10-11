package com.hoamocanh.service.admin;

import com.hoamocanh.core.entity.Category;
import com.hoamocanh.core.entity.Material;
import com.hoamocanh.core.exception.ResourceNotFoundException;
import com.hoamocanh.dto.admin.CategoryRes;
import com.hoamocanh.dto.admin.MaterialReq;
import com.hoamocanh.dto.admin.MaterialRes;
import com.hoamocanh.repository.CategoryRepository;
import com.hoamocanh.repository.MaterialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminMaterialService {

    private final MaterialRepository materialRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<MaterialRes> getAllMaterials() {
        return materialRepository.findAllWithCategory().stream().map(MaterialRes::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryRes> getCategories() {
        return categoryRepository.findAllByOrderByIdAsc().stream()
                .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
                .map(c -> new CategoryRes(c.getId(), c.getName(),
                        c.getDesignGroup() == null ? "OTHER" : c.getDesignGroup().name()))
                .toList();
    }

    @Transactional
    public MaterialRes addMaterial(MaterialReq req) {
        Material m = new Material();
        apply(m, req);
        m.setStock(req.getStock() == null ? 0 : req.getStock());
        m.setIsDeleted(false);
        return MaterialRes.from(materialRepository.save(m));
    }

    // Sửa thông tin (KHÔNG đụng tồn kho: tồn kho đổi liên tục khi khách đặt hàng, chỉnh qua updateStock)
    @Transactional
    public MaterialRes updateMaterial(Integer id, MaterialReq req) {
        Material m = find(id);
        apply(m, req);
        return MaterialRes.from(materialRepository.save(m));
    }

    @Transactional
    public MaterialRes updateStock(Integer id, Integer stock) {
        Material m = find(id);
        m.setStock(stock);
        return MaterialRes.from(materialRepository.save(m));
    }

    @Transactional
    public MaterialRes setHidden(Integer id, boolean hidden) {
        Material m = find(id);
        m.setIsDeleted(hidden);
        return MaterialRes.from(materialRepository.save(m));
    }

    private Material find(Integer id) {
        return materialRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nguyên liệu."));
    }

    private void apply(Material m, MaterialReq req) {
        Category c = categoryRepository.findById(req.getCategoryId())
                .filter(x -> !Boolean.TRUE.equals(x.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("Danh mục không tồn tại."));
        m.setCategory(c);
        m.setName(req.getName().trim());
        m.setDescription(blankToNull(req.getDescription()));
        m.setImageUrl(checkImage(req.getImageUrl()));
        m.setPrice(req.getPrice());
        m.setColor(blankToNull(req.getColor()));
        m.setUnit(blankToNull(req.getUnit()));
        m.setLowStockThreshold(req.getLowStockThreshold());
    }

    private static String blankToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    // Chỉ nhận ảnh do hệ thống upload, ảnh trong /images hoặc link https
    private static String checkImage(String url) {
        String u = blankToNull(url);
        if (u == null) return null;
        boolean ok = !u.contains("..") && (u.startsWith("/uploads/") || u.startsWith("/images/")
                || u.startsWith("images/") || u.startsWith("https://"));
        if (!ok) throw new IllegalArgumentException("Đường dẫn ảnh không hợp lệ.");
        return u;
    }
}
