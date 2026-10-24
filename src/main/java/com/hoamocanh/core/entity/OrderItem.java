package com.hoamocanh.core.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Một dòng "mẫu có sẵn" trong đơn mua từ giỏ hàng. Đơn tự thiết kế vẫn dùng order_details (nguyên liệu). */
@Entity
@Table(name = "order_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "sample_id", nullable = false, length = 60)
    private String sampleId;

    @Column(name = "sample_name", nullable = false, length = 160)
    private String sampleName;

    @Column(name = "image_url")
    private String imageUrl;

    // Mức giá khách chọn cho một mẫu (400.000đ đến 1.500.000đ, bước 100.000đ)
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private Integer quantity;
}