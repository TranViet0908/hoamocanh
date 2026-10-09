package com.hoamocanh.repository;

import com.hoamocanh.core.entity.BudgetRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface BudgetRuleRepository extends JpaRepository<BudgetRule, Integer> {

    // Tìm định mức theo mức giá (VD: 400.000, 500.000)
    Optional<BudgetRule> findByBudgetLevel(BigDecimal budgetLevel);
}