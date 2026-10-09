package com.hoamocanh.dto.admin;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class OrderRes {
    private Integer id;
    private String receiverName;
    private String receiverPhone;
    private String productTypeName;
    private BigDecimal totalPrice;
    private String deliveryType; // ONLINE, PICKUP, DELIVERY
    private String status; // PENDING, PROCESSING...
    private String paymentStatus;
    private LocalDateTime deliveryTime;
}