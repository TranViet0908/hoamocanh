// File: src/main/java/com/hoamocanh/service/customer/CustomerDesignService.java
package com.hoamocanh.service.customer;

import com.hoamocanh.core.entity.BudgetRule;
import com.hoamocanh.core.entity.Material;
import com.hoamocanh.core.exception.BudgetExceededException;
import com.hoamocanh.repository.BudgetRuleRepository;
import com.hoamocanh.repository.MaterialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerDesignService {

    private final MaterialRepository materialRepository;
    private final BudgetRuleRepository budgetRuleRepository;

    /**
     * API Load hoa: Đã fix - Chỉ trả về nguyên liệu phù hợp với ProductType VÀ Mức giá
     */
    public List<Material> getAvailableMaterialsForDesign(Integer productType, BigDecimal budgetLevel) {
        // 1. Lấy Rule ngân sách
        BudgetRule rule = budgetRuleRepository.findByProductTypeAndBudgetLevel(productType, budgetLevel)
                .orElseThrow(() -> new IllegalArgumentException("Tiệm đang tạm ngưng cấu hình mức giá này."));

        // 2. Tính số tiền tối đa được phép mua 1 bông hoa (để lọc bỏ hoa quá đắt)
        BigDecimal maxAllowedMaterialCost = calculateMaxAllowedMaterialCost(budgetLevel, rule);

        // Lấy danh sách nguyên liệu theo ProductType
        List<Material> allMaterials = materialRepository.findAvailableMaterialsByProductType(String.valueOf(productType));

        // 3. Filter bỏ đi các loại hoa mà chỉ 1 bông đã vượt (hoặc chiếm > 50%) ngân sách cho phép
        BigDecimal priceThreshold = maxAllowedMaterialCost.multiply(BigDecimal.valueOf(0.5)); // 1 bông hoa không được chiếm quá 50% tổng vốn

        return allMaterials.stream()
                .filter(m -> m.getPrice().compareTo(priceThreshold) <= 0 || !m.getType().equals("MAIN_FLOWER"))
                .collect(Collectors.toList());
    }

    /**
     * THUẬT TOÁN LÕI: Validate giỏ thiết kế của khách hàng
     */
    public void validateDesignCart(Integer productType, BigDecimal budgetLevel, Map<Long, Integer> selectedMaterials) {
        BudgetRule rule = budgetRuleRepository.findByProductTypeAndBudgetLevel(productType, budgetLevel)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy quy tắc ngân sách."));

        List<Material> materials = materialRepository.findAllById(selectedMaterials.keySet());

        int totalMainFlowers = 0;
        int totalSubFlowers = 0;
        BigDecimal totalMaterialCost = BigDecimal.ZERO;

        for (Material material : materials) {
            int quantity = selectedMaterials.getOrDefault(material.getId(), 0);

            if ("MAIN_FLOWER".equals(material.getType())) {
                totalMainFlowers += quantity;
            } else if ("SUB_FLOWER".equals(material.getType())) {
                totalSubFlowers += quantity;
            }

            BigDecimal itemCost = material.getPrice().multiply(BigDecimal.valueOf(quantity));
            totalMaterialCost = totalMaterialCost.add(itemCost);
        }

        // Validate Rule Số lượng
        if (totalMainFlowers > rule.getMaxMainFlowers()) {
            throw new BudgetExceededException("Số lượng hoa chính vượt mức (" + rule.getMaxMainFlowers() + " bông). Hãy giảm bớt hoặc nâng ngân sách!");
        }
        if (totalSubFlowers > rule.getMaxSubFlowers()) {
            throw new BudgetExceededException("Số lượng hoa phụ vượt mức (" + rule.getMaxSubFlowers() + " bông).");
        }

        // Validate Rule Lợi nhuận
        BigDecimal maxAllowedMaterialCost = calculateMaxAllowedMaterialCost(budgetLevel, rule);

        // Sanity Check: Tránh lỗi Admin điền chi phí nhân sự quá cao gây âm tiền
        if (maxAllowedMaterialCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new BudgetExceededException("Hệ thống cấu hình giá đang lỗi (Tiền vốn bị âm). Vui lòng báo nhân viên kiểm tra lại!");
        }

        if (totalMaterialCost.compareTo(maxAllowedMaterialCost) > 0) {
            BigDecimal exceededAmount = totalMaterialCost.subtract(maxAllowedMaterialCost);
            throw new BudgetExceededException("Thiết kế vượt ngân sách " + exceededAmount + "đ. Vui lòng giảm bớt hoa hoặc đổi mức giá cao hơn!");
        }
    }

    // Tách riêng hàm tính công thức để tái sử dụng
    private BigDecimal calculateMaxAllowedMaterialCost(BigDecimal budgetLevel, BudgetRule rule) {
        BigDecimal one = BigDecimal.ONE;
        return budgetLevel
                .multiply(one.subtract(rule.getMinProfitMargin()))
                .subtract(rule.getStaffCost());
    }
}