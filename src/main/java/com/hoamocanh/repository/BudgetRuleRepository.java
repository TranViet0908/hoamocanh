package com.hoamocanh.repository;

import com.hoamocanh.core.entity.BudgetRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface BudgetRuleRepository extends JpaRepository<BudgetRule, Long> {

    // Tìm định mức theo Loại sản phẩm và Mức ngân sách (ví dụ: Loại 2, Mức 400.000)
    Optional<BudgetRule> findByProductTypeAndBudgetLevel(Integer productType, BigDecimal budgetLevel);
}