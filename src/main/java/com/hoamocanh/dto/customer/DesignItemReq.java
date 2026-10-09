package com.hoamocanh.dto.customer;

import lombok.Data;

@Data
public class DesignItemReq {
    private Integer materialId; // Đổi thành Integer cho khớp DB
    private Integer quantity;
}