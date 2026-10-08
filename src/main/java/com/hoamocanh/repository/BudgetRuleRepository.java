package com.hoamocanh.repository;

import com.hoamocanh.core.entity.BudgetRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRuleRepository extends JpaRepository<BudgetRule, Long> {

    // Tìm chính xác một quy tắc dựa vào Loại sản phẩm và Mức giá (phải đang Active)
    Optional<BudgetRule> findByProductTypeAndBudgetLevelAndIsActiveTrue(Integer productType, BigDecimal budgetLevel);

    // Lấy danh sách các mức giá cho một loại sản phẩm để hiển thị cho khách chọn
    // Kết quả tự động sắp xếp mức giá từ thấp lên cao
    List<BudgetRule> findByProductTypeAndIsActiveTrueOrderByBudgetLevelAsc(Integer productType);
}