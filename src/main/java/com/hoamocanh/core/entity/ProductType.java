package com.hoamocanh.core.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "product_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "min_budget", nullable = false, precision = 10, scale = 2)
    private BigDecimal minBudget;

    // Lưu chuỗi danh mục được phép, VD: "1,2,3,5" (dùng Varchar để giảm tải join bảng)
    @Column(name = "allowed_category_ids", nullable = false)
    private String allowedCategoryIds;
}