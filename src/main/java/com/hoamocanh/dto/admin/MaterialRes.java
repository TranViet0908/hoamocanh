package com.hoamocanh.dto.admin;

import com.hoamocanh.core.entity.Material;

import java.math.BigDecimal;

public record MaterialRes(
        Integer id, Integer categoryId, String categoryName, String designGroup,
        String name, String description, String imageUrl, BigDecimal price,
        Integer stock, String color, String unit, Integer lowStockThreshold, boolean hidden) {

    public static final int DEFAULT_LOW_STOCK = 5;

    public static MaterialRes from(Material m) {
        return new MaterialRes(
                m.getId(), m.getCategory().getId(), m.getCategory().getName(),
                m.getCategory().getDesignGroup() == null ? "OTHER" : m.getCategory().getDesignGroup().name(),
                m.getName(), m.getDescription(), m.getImageUrl(), m.getPrice(),
                m.getStock(), m.getColor(), m.getUnit() == null ? "" : m.getUnit(),
                m.getLowStockThreshold() == null ? DEFAULT_LOW_STOCK : m.getLowStockThreshold(),
                Boolean.TRUE.equals(m.getIsDeleted()));   // "ẩn" = isDeleted (khách không còn thấy)
    }
}
