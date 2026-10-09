package com.hoamocanh.core.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "vouchers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Voucher {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 50, unique = true)
    private String code;

    @Column(name = "discount_type", length = 20)
    private String discountType; // FIXED, PERCENT

    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "max_discount", precision = 10, scale = 2)
    private BigDecimal maxDiscount;

    @Column(name = "min_order_value", precision = 10, scale = 2)
    private BigDecimal minOrderValue;

    @Column(name = "usage_limit")
    private Integer usageLimit; // Giới hạn số lượt dùng

    @Column(name = "used_count")
    private Integer usedCount; // Số lượt đã dùng

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(name = "is_active", columnDefinition = "tinyint(1) default 1")
    private Boolean isActive;

    // THÊM VERSION ĐỂ CHỐNG RACE CONDITION
    @Version
    @Column(columnDefinition = "int default 1")
    private Integer version;
}