package com.hoamocanh.service.customer;

import com.hoamocanh.core.entity.BudgetRule;
import com.hoamocanh.core.entity.DesignGroup;
import com.hoamocanh.core.entity.Material;
import com.hoamocanh.core.entity.ProductType;
import com.hoamocanh.core.exception.BudgetExceededException;
import com.hoamocanh.dto.customer.DesignLimitsRes;
import com.hoamocanh.dto.customer.MaterialDto;
import com.hoamocanh.repository.BudgetRuleRepository;
import com.hoamocanh.repository.MaterialRepository;
import com.hoamocanh.repository.ProductTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerDesignService {

    private final BudgetRuleRepository budgetRuleRepository;
    private final MaterialRepository materialRepository;
    private final ProductTypeRepository productTypeRepository;

    private static List<Integer> parseCategoryIds(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .collect(Collectors.toList());
    }

    // Bước 4: nguyên liệu còn hàng, đúng loại sản phẩm -> trả DTO (có ảnh + tồn kho)
    public List<MaterialDto> getAvailableMaterialsForDesign(Integer productTypeId, BigDecimal budgetLevel) {
        ProductType type = productTypeRepository.findById(productTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Loại sản phẩm không tồn tại"));

        if (budgetLevel.compareTo(type.getMinBudget()) < 0) {
            throw new IllegalArgumentException("Ngân sách tối thiểu cho " + type.getName() + " là " + type.getMinBudget());
        }

        // Mức giá phải đang mở (nếu admin vừa khóa thì báo ngay, không đợi tới bước kiểm tra cuối)
        budgetRuleRepository.findByBudgetLevel(budgetLevel)
                .orElseThrow(() -> new IllegalArgumentException("Mức giá này hiện không còn áp dụng, vui lòng chọn mức khác."));

        List<Integer> allowedCategoryIds = parseCategoryIds(type.getAllowedCategoryIds());

        return materialRepository.findAvailableMaterialsByCategories(allowedCategoryIds)
                .stream()
                .map(MaterialDto::from)
                .collect(Collectors.toList());
    }

    // MỚI: các mức giá đang mở, để giao diện chỉ cho khách chọn trong danh sách này
    public List<BigDecimal> getBudgetLevels() {
        return budgetRuleRepository.findActiveLevels();
    }

    // MỚI: định mức theo mức giá, để giao diện báo "còn được chọn bao nhiêu"
    public DesignLimitsRes getLimits(BigDecimal budgetLevel) {
        BudgetRule r = budgetRuleRepository.findByBudgetLevel(budgetLevel)
                .orElseThrow(() -> new IllegalArgumentException("Mức giá không hợp lệ hoặc đã bị khóa."));
        return new DesignLimitsRes(
                r.getMaxMainFlowers(), r.getMaxSubFlowers(), r.getMaxLeaves(),
                r.getMaxAccessories(), r.getMaxPackaging());
    }

    // MỚI: bản 3 tham số, có kiểm tra nguyên liệu thuộc loại sản phẩm.
    // Giữ nguyên bản 2 tham số bên dưới cho code cũ (checkout).
    public void validateDesignCart(Integer productTypeId, BigDecimal budgetLevel, Map<Integer, Integer> selectedMaterials) {
        ProductType type = productTypeRepository.findById(productTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Loại sản phẩm không tồn tại"));
        Set<Integer> allowed = Set.copyOf(parseCategoryIds(type.getAllowedCategoryIds()));

        for (Material m : materialRepository.findAllById(selectedMaterials.keySet())) {
            if (!allowed.contains(m.getCategory().getId())) {
                throw new IllegalArgumentException("\"" + m.getName() + "\" không dùng được cho " + type.getName() + ".");
            }
        }
        validateDesignCart(budgetLevel, selectedMaterials);
    }

    public void validateDesignCart(BigDecimal budgetLevel, Map<Integer, Integer> selectedMaterials) {
        BudgetRule rule = budgetRuleRepository.findByBudgetLevel(budgetLevel)
                .orElseThrow(() -> new IllegalArgumentException("Mức giá không hợp lệ hoặc đã bị khóa."));

        List<Material> materials = materialRepository.findAllById(selectedMaterials.keySet());
        if (materials.size() != selectedMaterials.size()) {
            throw new IllegalArgumentException("Có nguyên liệu không còn tồn tại, vui lòng tải lại trang.");
        }

        Map<DesignGroup, Integer> counts = new EnumMap<>(DesignGroup.class);
        BigDecimal total = BigDecimal.ZERO;

        for (Material m : materials) {
            int qty = selectedMaterials.getOrDefault(m.getId(), 0);

            // Kiểm tra còn hàng
            if (Boolean.TRUE.equals(m.getIsDeleted())) {
                throw new IllegalArgumentException("\"" + m.getName() + "\" hiện không còn bán.");
            }
            if (qty > m.getStock()) {
                throw new IllegalArgumentException("\"" + m.getName() + "\" chỉ còn " + m.getStock() + " trong kho.");
            }
            total = total.add(m.getPrice().multiply(BigDecimal.valueOf(qty)));

            // Nhóm lấy từ categories.design_group (chưa gán -> OTHER, không bị giới hạn)
            DesignGroup g = m.getCategory().getDesignGroup() == null ? DesignGroup.OTHER : m.getCategory().getDesignGroup();
            counts.merge(g, qty, Integer::sum);
        }

        int countMainFlowers = counts.getOrDefault(DesignGroup.MAIN, 0);
        int countSubFlowers = counts.getOrDefault(DesignGroup.SUB, 0);
        int countLeaves = counts.getOrDefault(DesignGroup.LEAF, 0);
        int countPackaging = counts.getOrDefault(DesignGroup.PACKAGING, 0);
        int countAccessories = counts.getOrDefault(DesignGroup.ACCESSORY, 0);

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

        // MỚI: tổng tiền nguyên liệu không được vượt mức giá đã chọn
        if (total.compareTo(budgetLevel) > 0) {
            NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));
            throw new BudgetExceededException("Tổng nguyên liệu " + nf.format(total) + "đ đang vượt ngân sách "
                    + nf.format(budgetLevel) + "đ. Hãy bỏ bớt hoặc nâng lên mức giá tiếp theo.");
        }
    }
}