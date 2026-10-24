package com.hoamocanh.dto.customer;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Đặt hàng từ giỏ "mẫu có sẵn". deliveryType: DELIVERY (giao tận nơi) | STUDIO_PICKUP (đến tiệm nhận). */
@Data
public class ShopCheckoutReq {
    private List<Line> items;
    private String receiverName;
    private String receiverPhone;
    private String shippingAddress;
    private LocalDateTime deliveryTime;
    private String deliveryType;
    private String paymentMethod;   // CASH | ONLINE
    private String cardMessage;
    private String note;
    private String voucherCode;

    @Data
    public static class Line {
        private String sampleId;
        private BigDecimal price;
        private Integer quantity;
    }
}