package com.hoamocanh.dto.customer;

import com.hoamocanh.core.entity.enums.MaterialType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialCatalogResp {
    private Long id;
    private String name;
    private MaterialType type;
    private BigDecimal price;
    private String imageUrl;
    private String color;
    // Cố tình không trả về stockQuantity, status để bảo mật thông tin nội bộ
}