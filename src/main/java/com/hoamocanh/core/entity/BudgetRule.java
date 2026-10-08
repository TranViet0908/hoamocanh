package com.hoamocanh.core.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "budget_rules", uniqueConstraints = {
        @UniqueConstraint(name = "unique_product_budget", columnNames = {"product_type", "budget_level"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_type", nullable = false)
    private Integer productType;

    @Column(name = "budget_level", nullable = false, precision = 10, scale = 2)
    private BigDecimal budgetLevel;

    @Column(name = "max_main_flowers", nullable = false)
    private Integer maxMainFlowers;

    @Column(name = "max_sub_flowers", nullable = false)
    private Integer maxSubFlowers;

    @Column(name = "staff_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal staffCost;

    @Column(name = "min_profit_margin", nullable = false, precision = 5, scale = 2)
    private BigDecimal minProfitMargin;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
}