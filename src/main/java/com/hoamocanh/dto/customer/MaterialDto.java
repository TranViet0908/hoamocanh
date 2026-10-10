package com.hoamocanh.dto.customer;

import com.hoamocanh.core.entity.Material;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class MaterialDto {
    private Integer id;
    private Integer categoryId;
    private String categoryName;   // tên danh mục lấy từ bảng categories
    private String designGroup;    // MỚI: MAIN|SUB|LEAF|PACKAGING|ACCESSORY|OTHER
    private String name;
    private BigDecimal price;
    private String imageUrl;
    private Integer stock;      // MỚI: số lượng còn trong kho

    public static MaterialDto from(Material m) {
        return new MaterialDto(
                m.getId(),
                m.getCategory().getId(),
                m.getCategory().getName(),
                m.getCategory().getDesignGroup() == null ? "OTHER" : m.getCategory().getDesignGroup().name(),
                m.getName(),
                m.getPrice(),
                m.getImageUrl(),
                m.getStock());
    }
}