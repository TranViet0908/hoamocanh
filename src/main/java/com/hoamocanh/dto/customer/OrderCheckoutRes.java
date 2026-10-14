package com.hoamocanh.dto.customer;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class OrderCheckoutRes {
    private Integer orderId;
    private String orderCode;         // mã hiển thị cho khách, dạng MA-123
    private String orderStatus;
    private String paymentStatus;
    private String paymentMethod;     // MỚI: CASH | ONLINE
    private BigDecimal payableAmount; // MỚI: số tiền khách cần trả (sau giảm giá)
    private String message;
    private String qrCodeUrl;         // link trang quà tặng nếu chọn GỬI TẶNG ONLINE
    private String giftPassword;      // MỚI: Mã mở quà (plaintext)
}