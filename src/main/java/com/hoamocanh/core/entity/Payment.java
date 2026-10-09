package com.hoamocanh.core.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod; // CASH, BANK_TRANSFER, MOMO

    @Column(name = "payment_type", length = 50)
    private String paymentType; // DEPOSIT (đặt cọc), FULL_PAYMENT (thanh toán đủ)

    @Column(name = "payment_status", length = 20)
    private String paymentStatus; // SUCCESS, PENDING, FAILED

    @Column(name = "transaction_code", length = 100)
    private String transactionCode;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}