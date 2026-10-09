package com.hoamocanh.repository;

import com.hoamocanh.core.entity.Material;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaterialRepository extends JpaRepository<Material, Long> {

    // Tìm hoa đang có sẵn dựa theo ID loại sản phẩm (1, 2, 3...)
    // Dùng FIND_IN_SET vì supported_product_types lưu chuỗi dạng "1,2,3,4"
    @Query(value = "SELECT * FROM materials m WHERE m.status = 'AVAILABLE' AND FIND_IN_SET(:productType, m.supported_product_types) > 0 AND m.stock_quantity > 0", nativeQuery = true)
    List<Material> findAvailableMaterialsByProductType(@Param("productType") String productType);
}