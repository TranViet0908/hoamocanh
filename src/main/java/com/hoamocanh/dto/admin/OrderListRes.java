package com.hoamocanh.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderListRes(
        Integer id, String code, String customerName, String phone, String productTypeName,
        String deliveryType, String status, String paymentStatus,
        BigDecimal payable, LocalDateTime deliveryTime, LocalDateTime createdAt) {
}
