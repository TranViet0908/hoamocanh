package com.hoamocanh.repository;

import com.hoamocanh.core.entity.Material;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MaterialRepository extends JpaRepository<Material, Integer> {

    // Hoa còn tồn kho, chưa xóa, thuộc Category được phép; sắp theo nhóm rồi tên cho giao diện ổn định
    @Query("SELECT m FROM Material m WHERE m.category.id IN :categoryIds AND m.stock > 0 AND m.isDeleted = false ORDER BY m.category.id, m.name")
    List<Material> findAvailableMaterialsByCategories(@Param("categoryIds") List<Integer> categoryIds);
}