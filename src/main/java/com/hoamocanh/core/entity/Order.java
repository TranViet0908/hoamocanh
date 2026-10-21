package com.hoamocanh.core.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_type_id")
    private ProductType productType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id")
    private Voucher voucher;

    @Column(nullable = false, length = 50)
    private String status; // PENDING, PROCESSING, COMPLETED, CANCELLED

    @Column(name = "payment_status", length = 20)
    private String paymentStatus; // UNPAID, DEPOSITED, PAID

    // MỚI: hình thức thanh toán khách chọn: CASH (trực tiếp) | ONLINE
    @Column(name = "payment_method", length = 20)
    private String paymentMethod;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "discount_amount", precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "deposit_amount", precision = 10, scale = 2)
    private BigDecimal depositAmount;

    @Column(length = 50)
    private String style;

    @Column(name = "theme_color", length = 50)
    private String themeColor;

    @Column(name = "style_note", columnDefinition = "TEXT")
    private String styleNote;

    @Column(name = "gift_message", columnDefinition = "TEXT")
    private String giftMessage;

    @Column(name = "gift_image_url")
    private String giftImageUrl;

    @Column(name = "gift_video_url")
    private String giftVideoUrl;

    // Ảnh đóa hoa mô phỏng khách đã chốt, hiển thị ở trang quà tặng
    @Column(name = "bouquet_image_url")
    private String bouquetImageUrl;

    // Ảnh thành phẩm tiệm tải lên để khách duyệt (đơn Mộc Anh làm giúp), đường dẫn /uploads/product/...
    @Column(name = "finished_image_url")
    private String finishedImageUrl;

    @Column(name = "qr_code_url")
    private String qrCodeUrl;

    @Column(name = "delivery_type", length = 50)
    private String deliveryType; // ONLINE, PICKUP, DELIVERY

    @Column(name = "receiver_name", length = 100)
    private String receiverName;

    @Column(name = "receiver_phone", length = 20)
    private String receiverPhone;

    @Column(name = "shipping_address", columnDefinition = "TEXT")
    private String shippingAddress;

    @Column(name = "delivery_time")
    private LocalDateTime deliveryTime;

    // Người đặt hộ (null = người đặt chính là người nhận)
    @Column(name = "buyer_name", length = 100)
    private String buyerName;

    @Column(name = "buyer_phone", length = 20)
    private String buyerPhone;

    // Phí giao hàng (đơn DELIVERY), cộng vào số phải thu
    @Column(name = "shipping_fee", precision = 10, scale = 2)
    private BigDecimal shippingFee;

    // Tiền cọc giữ chỗ yêu cầu (đơn PICKUP)
    @Column(name = "deposit_required", precision = 10, scale = 2)
    private BigDecimal depositRequired;

    // Mã đặt lịch (đơn PICKUP), dạng DL-XXXXXX, để khách đọc cho nhân viên khi đến tiệm
    @Column(name = "booking_code", length = 20, unique = true)
    private String bookingCode;

    // Ảnh các mẫu khách chọn ở Bước 5: mỗi dòng "url<TAB>tên mẫu"
    @Column(name = "sample_images", columnDefinition = "TEXT")
    private String sampleImages;

    @Column(name = "gift_password_hash")
    private String giftPasswordHash;

    @Column(name = "gift_password_hint")
    private String giftPasswordHint;

    @Column(name = "gift_failed_attempts")
    private Integer giftFailedAttempts = 0;

    @Column(name = "gift_lock_until")
    private java.time.LocalDateTime giftLockUntil;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderDetail> orderDetails;
}