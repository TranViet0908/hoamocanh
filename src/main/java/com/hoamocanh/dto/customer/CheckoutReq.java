package com.hoamocanh.dto.customer;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CheckoutReq {
    private Integer productTypeId; // 1: Mix thả bình, 6: Kệ hoa...
    private BigDecimal budgetLevel; // Để backend check chéo lại xem có bị hack giá không

    // Yêu cầu thiết kế
    private String style; // Sang trọng, Dịu dàng...
    private String themeColor;
    private String styleNote;

    // Loại đầu ra đơn hàng (ONLINE, PICKUP, DELIVERY)
    private String deliveryType;

    // Thông tin nhận hàng / Đặt lịch đến tiệm
    private String receiverName;
    private String receiverPhone;
    // Người đặt hộ (tùy chọn). Để trống nghĩa là người đặt cũng chính là người nhận.
    private String buyerName;
    private String buyerPhone;
    private String shippingAddress;
    private LocalDateTime deliveryTime; // Dùng chung cho giờ giao hàng hoặc giờ khách đến tiệm (PICKUP)

    // Dành riêng cho luồng GỬI TẶNG ONLINE
    private String giftMessage;
    private String giftImageUrl;
    private String giftVideoUrl;
    // Ảnh đóa hoa mô phỏng khách đã chốt ở Bước 5 (đường dẫn /uploads/gift/...)
    private String bouquetImageUrl;

    // Hình thức thanh toán: CASH (trực tiếp khi nhận/tại tiệm) | ONLINE
    private String paymentMethod;

    // Khuyến mãi
    private String voucherCode;

    // Giỏ hoa khách tự thiết kế
    private List<DesignItemReq> items;
}