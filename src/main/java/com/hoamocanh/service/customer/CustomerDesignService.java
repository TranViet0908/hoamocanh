package com.hoamocanh.service.customer;

import com.hoamocanh.core.entity.BudgetRule;
import com.hoamocanh.core.entity.Material;
import com.hoamocanh.core.entity.enums.MaterialStatus;
import com.hoamocanh.core.entity.enums.MaterialType;
import com.hoamocanh.dto.customer.CartItemReq;
import com.hoamocanh.dto.customer.MaterialCatalogResp;
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

    // 1. Lấy danh sách nguyên liệu khách được phép chọn theo loại sản phẩm
    public List<MaterialCatalogResp> getAvailableMaterialsForProduct(Integer productType) {
        List<Material> materials = materialRepository.findSupportedMaterials(
                String.valueOf(productType),
                MaterialStatus.AVAILABLE
        );

        return materials.stream().map(m -> MaterialCatalogResp.builder()
                .id(m.getId())
                .name(m.getName())
                .type(m.getType())
                .price(m.getPrice())
                .imageUrl(m.getImageUrl())
                .color(m.getColor())
                .build()).collect(Collectors.toList());
    }

    // 2. Lõi thuật toán: Validate Giỏ hàng (Design) của khách
    public void validateDesignCart(Integer productType, BigDecimal budgetLevel, List<CartItemReq> cart) {
        // 2.1. Lấy quy tắc ngân sách (Budget Rule)
        BudgetRule rule = budgetRuleRepository.findByProductTypeAndBudgetLevelAndIsActiveTrue(productType, budgetLevel)
                .orElseThrow(() -> new RuntimeException("Mức giá này hiện không khả dụng cho loại sản phẩm đã chọn."));

        // 2.2. Khởi tạo bộ đếm
        int mainFlowerCount = 0;
        int subFlowerCount = 0;
        BigDecimal totalMaterialCost = BigDecimal.ZERO;

        // Lấy danh sách ID nguyên liệu trong giỏ để query 1 lần cho tối ưu
        List<Long> materialIds = cart.stream().map(CartItemReq::getMaterialId).collect(Collectors.toList());
        Map<Long, Material> materialMap = materialRepository.findAllById(materialIds).stream()
                .collect(Collectors.toMap(Material::getId, m -> m));

        // 2.3. Duyệt qua giỏ hàng và cộng dồn
        for (CartItemReq item : cart) {
            Material material = materialMap.get(item.getMaterialId());
            if (material == null || !material.getStatus().equals(MaterialStatus.AVAILABLE)) {
                throw new RuntimeException("Có nguyên liệu đã hết hàng hoặc không tồn tại, vui lòng chọn lại.");
            }

            // Cộng tiền
            BigDecimal costForThisItem = material.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            totalMaterialCost = totalMaterialCost.add(costForThisItem);

            // Đếm số lượng hoa chính / phụ
            if (material.getType() == MaterialType.MAIN_FLOWER) {
                mainFlowerCount += item.getQuantity();
            } else if (material.getType() == MaterialType.SUB_FLOWER) {
                subFlowerCount += item.getQuantity();
            }
        }

        // 2.4. Kiểm tra số lượng hoa tối đa
        if (mainFlowerCount > rule.getMaxMainFlowers()) {
            throw new RuntimeException("Bạn đã chọn vượt quá " + rule.getMaxMainFlowers() + " bông hoa chính cho mức giá này.");
        }
        if (subFlowerCount > rule.getMaxSubFlowers()) {
            throw new RuntimeException("Bạn đã chọn vượt quá " + rule.getMaxSubFlowers() + " hoa phụ cho mức giá này.");
        }

        // 2.5. Kiểm tra ngân sách (Tiệm phải có lãi tối thiểu và bù đắp được chi phí nhân sự)
        BigDecimal minimumProfit = budgetLevel.multiply(rule.getMinProfitMargin());
        BigDecimal maxAllowedMaterialCost = budgetLevel.subtract(rule.getStaffCost()).subtract(minimumProfit);

        if (totalMaterialCost.compareTo(maxAllowedMaterialCost) > 0) {
            throw new RuntimeException("Thiết kế của bạn đã vượt quá ngân sách cho phép. Xin vui lòng giảm bớt nguyên liệu hoặc nâng lên mức giá cao hơn.");
        }
    }
}