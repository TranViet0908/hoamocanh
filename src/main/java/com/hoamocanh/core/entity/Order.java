package com.hoamocanh.core.entity;

import com.hoamocanh.core.entity.enums.OrderStatus;
import com.hoamocanh.core.entity.enums.OutputType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private Long id;

    @Column(name = "order_code", nullable = false, unique = true, length = 50)
    private String orderCode;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_phone", nullable = false, length = 20)
    private String customerPhone;

    @Column(name = "product_type", nullable = false)
    private Integer productType;

    @Column(name = "budget_level", nullable = false, precision = 10, scale = 2)
    private BigDecimal budgetLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "output_type", nullable = false, length = 50)
    private OutputType outputType;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    @Builder.Default
    private OrderStatus status = OrderStatus.DRAFT;

    @Column(name = "delivery_address", columnDefinition = "TEXT")
    private String deliveryAddress;

    @Column(name = "appointment_time")
    private LocalDateTime appointmentTime;

    @Column(name = "gift_message", columnDefinition = "TEXT")
    private String giftMessage;

    @Column(name = "qr_code_url")
    private String qrCodeUrl;

    @Column(name = "style_note")
    private String styleNote;

    @Column(name = "recipient_name")
    private String recipientName;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Quan hệ 1-N với OrderItem
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> orderItems = new ArrayList<>();

    public void addOrderItem(OrderItem item) {
        orderItems.add(item);
        item.setOrder(this);
    }

    public void removeOrderItem(OrderItem item) {
        orderItems.remove(item);
        item.setOrder(null);
    }
}