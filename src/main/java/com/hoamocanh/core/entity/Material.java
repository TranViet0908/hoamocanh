package com.hoamocanh.core.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "materials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Material {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 50)
    private String type; // MAIN_FLOWER, SUB_FLOWER, LEAF, PACKAGING, ACCESSORY

    @Column(name = "supported_product_types", nullable = false, length = 50)
    private String supportedProductTypes; // Ví dụ: "1,2,3,4,5,6"

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;

    @Column(length = 20)
    private String status; // AVAILABLE, OUT_OF_STOCK, HIDDEN

    // GIẢI QUYẾT RỦI RO "CHÁY HÀNG" KHI NHIỀU KHÁCH CÙNG ĐẶT:
    // Sử dụng Optimistic Locking. Hibernate sẽ tự động tăng giá trị này.
    // Nếu có 2 luồng cùng update 1 bản ghi với cùng 1 version, luồng sau sẽ bị văng OptimisticLockException.
    @Version
    @Column(columnDefinition = "int default 0")
    private Integer version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}