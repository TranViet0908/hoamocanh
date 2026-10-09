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
    private Integer id;

    @Column(name = "budget_level", nullable = false, precision = 10, scale = 2, unique = true)
    private BigDecimal budgetLevel;

    @Column(name = "max_main_flowers", nullable = false)
    private Integer maxMainFlowers;

    @Column(name = "max_sub_flowers", nullable = false)
    private Integer maxSubFlowers;

    @Column(name = "max_leaves", nullable = false)
    private Integer maxLeaves;

    @Column(name = "max_accessories", nullable = false)
    private Integer maxAccessories;

    @Column(name = "max_packaging", nullable = false)
    private Integer maxPackaging;
}