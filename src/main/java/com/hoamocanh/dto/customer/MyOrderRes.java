package com.hoamocanh.dto.customer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MyOrderRes(String orderCode, String status, String deliveryType, String paymentStatus,
                         BigDecimal payable, LocalDateTime createdAt) { }