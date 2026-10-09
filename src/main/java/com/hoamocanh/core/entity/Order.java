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

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderDetail> orderDetails;
}