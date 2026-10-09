package com.hoamocanh.dto.admin;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class MaterialReq {
    private String name;
    private String type; // MAIN_FLOWER, SUB_FLOWER, LEAF, PACKAGING, ACCESSORY
    private String supportedProductTypes; // Ví dụ: "1,2,3,4,5,6"
    private BigDecimal price;
    private Integer stockQuantity;
    private String status; // AVAILABLE, OUT_OF_STOCK, HIDDEN
}