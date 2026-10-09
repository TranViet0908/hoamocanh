package com.hoamocanh.dto.admin;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class MaterialReq {
    private Integer categoryId; // Thay cho type và supportedProductTypes cũ
    private String name;
    private String description;
    private String imageUrl;
    private BigDecimal price;
    private Integer stock; // Đổi từ stockQuantity
}