package com.hoamocanh.dto.customer;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class MaterialDto {
    private Integer id;
    private Integer categoryId;
    private String name;
    private BigDecimal price;
    private String imageUrl;
}