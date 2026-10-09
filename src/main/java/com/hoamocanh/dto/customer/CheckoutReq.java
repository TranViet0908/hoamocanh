package com.hoamocanh.dto.customer;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CheckoutReq {
    private String customerName;
    private String customerPhone;
    private Integer productType; // 1: Mix thả bình, 2: Bó, 3: Giỏ...
    private BigDecimal budgetLevel; // 400000, 500000...
    private String outputType; // ONLINE, AT_STORE, BY_MOCANH

    // Thêm các thông tin tùy chọn theo luồng
    private String deliveryAddress;
    private LocalDateTime appointmentTime; // Dành cho luồng AT_STORE (Tự làm tại tiệm)
    private String giftMessage; // Dành cho luồng ONLINE

    private List<DesignItemReq> items;
}