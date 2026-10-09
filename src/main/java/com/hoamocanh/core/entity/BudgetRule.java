package com.hoamocanh.core.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "budget_rules")
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
    private Integer productType; // 1: Mix thả bình, 2: Bó, 3: Giỏ...

    @Column(name = "budget_level", nullable = false, precision = 10, scale = 2)
    private BigDecimal budgetLevel; // 400000, 500000...

    @Column(name = "max_main_flowers", nullable = false)
    private Integer maxMainFlowers;

    @Column(name = "max_sub_flowers", nullable = false)
    private Integer maxSubFlowers;

    @Column(name = "staff_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal staffCost;

    @Column(name = "min_profit_margin", nullable = false, precision = 5, scale = 2)
    private BigDecimal minProfitMargin;
}