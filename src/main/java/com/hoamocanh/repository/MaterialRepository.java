package com.hoamocanh.repository;

import com.hoamocanh.core.entity.Material;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MaterialRepository extends JpaRepository<Material, Integer> {

    // Tìm hoa còn tồn kho (stock > 0), chưa bị xóa và thuộc danh sách Category được phép
    @Query("SELECT m FROM Material m WHERE m.category.id IN :categoryIds AND m.stock > 0 AND m.isDeleted = false")
    List<Material> findAvailableMaterialsByCategories(@Param("categoryIds") List<Integer> categoryIds);
}