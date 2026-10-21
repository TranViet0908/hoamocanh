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
    private BigDecimal shippingFee;   // phí giao hàng (đơn DELIVERY), đã nằm trong payableAmount
    private BigDecimal depositAmount; // tiền cọc giữ chỗ (đơn PICKUP), null nếu không cần cọc
    private BigDecimal payNowAmount;  // số tiền cần trả ngay: tiền cọc nếu có, không thì toàn bộ
    private String bookingCode;   // mã đặt lịch (đơn PICKUP), null với hình thức khác
    private java.time.LocalDateTime holdUntil; // hạn giữ chỗ nếu chưa nhận được cọc (đơn PICKUP)
}