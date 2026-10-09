package com.hoamocanh.dto.customer;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderCheckoutRes {
    private Integer orderId;
    private String orderStatus;
    private String paymentStatus;
    private String message;
    private String qrCodeUrl; // Trả về link QR nếu khách chọn luồng GỬI TẶNG ONLINE
}