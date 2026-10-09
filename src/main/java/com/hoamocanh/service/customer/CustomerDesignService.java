package com.hoamocanh.service.customer;

import com.hoamocanh.core.entity.BudgetRule;
import com.hoamocanh.core.entity.Material;
import com.hoamocanh.core.entity.ProductType;
import com.hoamocanh.core.exception.BudgetExceededException;
import com.hoamocanh.repository.BudgetRuleRepository;
import com.hoamocanh.repository.MaterialRepository;
import com.hoamocanh.repository.ProductTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerDesignService {

    private final BudgetRuleRepository budgetRuleRepository;
    private final MaterialRepository materialRepository;
    private final ProductTypeRepository productTypeRepository; // Bổ sung Repository này

    // ĐÃ FIX LỖI: Bổ sung lại hàm lấy nguyên liệu cho Bước thiết kế
    public List<Material> getAvailableMaterialsForDesign(Integer productTypeId, BigDecimal budgetLevel) {
        // 1. Kiểm tra Form dáng sản phẩm (VD: Mix thả bình, Kệ hoa...)
        ProductType type = productTypeRepository.findById(productTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Loại sản phẩm không tồn tại"));

        // 2. Rủi ro nghiệp vụ: Khách chọn Kệ hoa nhưng truyền ngân sách 400k -> Chặn ngay
        if (budgetLevel.compareTo(type.getMinBudget()) < 0) {
            throw new IllegalArgumentException("Ngân sách tối thiểu cho " + type.getName() + " là " + type.getMinBudget());
        }

        // 3. Tách chuỗi danh mục được phép sử dụng (VD: "1,2,3,5")
        List<Integer> allowedCategoryIds = Arrays.stream(type.getAllowedCategoryIds().split(","))
                .map(Integer::parseInt)
                .collect(Collectors.toList());

        // 4. Lấy hoa tồn kho và đúng form dáng
        return materialRepository.findAvailableMaterialsByCategories(allowedCategoryIds);
    }

    // Hàm Validate giỏ hàng (Giữ nguyên như đã code)
    public void validateDesignCart(BigDecimal budgetLevel, Map<Integer, Integer> selectedMaterials) {
        BudgetRule rule = budgetRuleRepository.findByBudgetLevel(budgetLevel)
                .orElseThrow(() -> new IllegalArgumentException("Mức giá không hợp lệ hoặc đã bị khóa."));

        List<Material> materials = materialRepository.findAllById(selectedMaterials.keySet());

        int countMainFlowers = 0;
        int countSubFlowers = 0;
        int countLeaves = 0;
        int countPackaging = 0;
        int countAccessories = 0;

        for (Material m : materials) {
            int qty = selectedMaterials.getOrDefault(m.getId(), 0);
            Integer catId = m.getCategory().getId();

            if (catId == 1) countMainFlowers += qty;
            else if (catId == 2) countSubFlowers += qty;
            else if (catId == 3) countLeaves += qty;
            else if (catId == 4 || catId == 6) countPackaging += qty;
            else if (catId == 5) countAccessories += qty;
        }

        if (countMainFlowers > rule.getMaxMainFlowers()) {
            throw new BudgetExceededException("Bạn chỉ được chọn tối đa " + rule.getMaxMainFlowers() + " hoa chính.");
        }
        if (countSubFlowers > rule.getMaxSubFlowers()) {
            throw new BudgetExceededException("Bạn chỉ được chọn tối đa " + rule.getMaxSubFlowers() + " hoa phụ.");
        }
        if (countLeaves > rule.getMaxLeaves()) {
            throw new BudgetExceededException("Số lượng lá trang trí vượt quá giới hạn.");
        }
        if (countAccessories > rule.getMaxAccessories()) {
            throw new BudgetExceededException("Số lượng phụ kiện vượt quá giới hạn.");
        }
        if (countPackaging > rule.getMaxPackaging()) {
            throw new BudgetExceededException("Số lượng giấy gói/hộp vượt quá giới hạn.");
        }
    }
}