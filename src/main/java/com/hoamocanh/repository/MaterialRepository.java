package com.hoamocanh.repository;

import com.hoamocanh.core.entity.Material;
import com.hoamocanh.core.entity.enums.MaterialStatus;
import com.hoamocanh.core.entity.enums.MaterialType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaterialRepository extends JpaRepository<Material, Long> {

    // Lấy danh sách nguyên liệu theo trạng thái (VD: chỉ lấy AVAILABLE)
    List<Material> findByStatus(MaterialStatus status);

    // Lấy nguyên liệu theo phân loại (Hoa chính, hoa phụ...) và trạng thái
    List<Material> findByTypeAndStatus(MaterialType type, MaterialStatus status);

    // Truy vấn tuỳ chỉnh: Lọc nguyên liệu phù hợp với một loại sản phẩm cụ thể (productType)
    // Ví dụ: productType = "2" sẽ match với chuỗi "1,2,3"
    @Query("SELECT m FROM Material m WHERE m.status = :status AND m.supportedProductTypes LIKE %:productType%")
    List<Material> findSupportedMaterials(
            @Param("productType") String productType,
            @Param("status") MaterialStatus status
    );
}