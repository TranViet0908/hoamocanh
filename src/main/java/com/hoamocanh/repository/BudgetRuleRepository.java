package com.hoamocanh.repository;

import com.hoamocanh.core.entity.BudgetRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRuleRepository extends JpaRepository<BudgetRule, Integer> {

    // Phía khách: giữ nguyên tên hàm nhưng chỉ trả mức giá đang MỞ, nên khóa một mức ở trang quản trị là có hiệu lực ngay
    @Query("SELECT r FROM BudgetRule r WHERE r.budgetLevel = :level AND (r.active IS NULL OR r.active = true)")
    Optional<BudgetRule> findByBudgetLevel(@Param("level") BigDecimal level);

    // Admin: tất cả mức, kể cả đang khóa
    List<BudgetRule> findAllByOrderByBudgetLevelAsc();

    boolean existsByBudgetLevel(BigDecimal level);
}
