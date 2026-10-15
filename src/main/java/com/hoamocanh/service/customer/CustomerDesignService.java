package com.hoamocanh.service.customer;

import com.hoamocanh.core.entity.BudgetRule;
import com.hoamocanh.core.entity.DesignGroup;
import com.hoamocanh.core.entity.Material;
import com.hoamocanh.core.entity.ProductType;
import com.hoamocanh.core.exception.BudgetExceededException;
import com.hoamocanh.core.exception.InvalidBudgetException;
import com.hoamocanh.core.exception.OutOfStockException;
import com.hoamocanh.core.exception.ResourceNotFoundException;
import com.hoamocanh.dto.customer.DesignItemReq;
import com.hoamocanh.dto.customer.DesignLimitsRes;
import com.hoamocanh.dto.customer.MaterialDto;
import com.hoamocanh.repository.BudgetRuleRepository;
import com.hoamocanh.repository.MaterialRepository;
import com.hoamocanh.repository.ProductTypeRepository;
import com.hoamocanh.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerDesignService {

    private final BudgetRuleRepository budgetRuleRepository;
    private final MaterialRepository materialRepository;
    private final ProductTypeRepository productTypeRepository;
    private final SystemConfigRepository configRepository;

    private static final String K_LABOR_RATIO = "LABOR_AND_MARGIN_RATIO";
    private static final BigDecimal DEFAULT_LABOR_RATIO = new BigDecimal("0.20");
    private static final BigDecimal MAX_LABOR_RATIO = new BigDecimal("0.90");

    /** Tỷ lệ dành cho nhân sự, hướng dẫn tại tiệm và lợi nhuận tối thiểu (Mục 5 tài liệu yêu cầu). Admin sửa trong system_configs. */
    public BigDecimal getLaborRatio() {
        try {
            BigDecimal r = configRepository.findById(K_LABOR_RATIO)
                    .map(c -> new BigDecimal(c.getConfigValue().trim()))
                    .orElse(DEFAULT_LABOR_RATIO);
            return (r.signum() < 0 || r.compareTo(MAX_LABOR_RATIO) > 0) ? DEFAULT_LABOR_RATIO : r;
        } catch (RuntimeException e) {
            return DEFAULT_LABOR_RATIO;
        }
    }

    /** Hạn mức tiền nguyên liệu của một gói = mức giá x (1 - tỷ lệ nhân sự/lợi nhuận), làm tròn xuống 1.000đ. */
    public BigDecimal getMaterialBudget(BigDecimal budgetLevel) {
        BigDecimal cap = budgetLevel.multiply(BigDecimal.ONE.subtract(getLaborRatio()));
        return cap.divide(BigDecimal.valueOf(1000), 0, RoundingMode.DOWN).multiply(BigDecimal.valueOf(1000));
    }

    private static List<Integer> parseCategoryIds(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .collect(Collectors.toList());
    }

    private BudgetRule getActiveBudgetRule(BigDecimal budgetLevel) {
        return budgetRuleRepository.findByBudgetLevel(budgetLevel)
                .filter(r -> !Boolean.FALSE.equals(r.getActive()))
                .orElseThrow(() -> new InvalidBudgetException("Mức giá này hiện không còn áp dụng hoặc đã bị khóa, vui lòng chọn mức khác."));
    }

    // Bước 4: nguyên liệu còn hàng, đúng loại sản phẩm -> trả DTO (có ảnh + tồn kho)
    public List<MaterialDto> getAvailableMaterialsForDesign(Integer productTypeId, BigDecimal budgetLevel) {
        ProductType type = productTypeRepository.findById(productTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Loại sản phẩm không tồn tại."));

        if (budgetLevel.compareTo(type.getMinBudget()) < 0) {
            NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));
            throw new InvalidBudgetException("Ngân sách tối thiểu cho " + type.getName() + " là " + nf.format(type.getMinBudget()) + "đ.");
        }

        getActiveBudgetRule(budgetLevel);

        List<Integer> allowedCategoryIds = parseCategoryIds(type.getAllowedCategoryIds());

        return materialRepository.findAvailableMaterialsByCategories(allowedCategoryIds)
                .stream()
                .map(MaterialDto::from)
                .collect(Collectors.toList());
    }

    // Các mức giá đang mở, để giao diện chỉ cho khách chọn trong danh sách này
    public List<BigDecimal> getBudgetLevels() {
        return budgetRuleRepository.findActiveLevels();
    }

    // Định mức theo mức giá, để giao diện báo "còn được chọn bao nhiêu"
    public DesignLimitsRes getLimits(BigDecimal budgetLevel) {
        BudgetRule r = getActiveBudgetRule(budgetLevel);
        return new DesignLimitsRes(
                r.getMaxMainFlowers(), r.getMaxSubFlowers(), r.getMaxLeaves(),
                r.getMaxAccessories(), r.getMaxPackaging(),
                getMaterialBudget(budgetLevel), BigDecimal.ONE.subtract(getLaborRatio()));
    }

    // Bản 3 tham số: kiểm tra loại sản phẩm + mức giá tối thiểu + danh mục hợp lệ
    public void validateDesignCart(Integer productTypeId, BigDecimal budgetLevel, Map<Integer, Integer> selectedMaterials) {
        ProductType type = productTypeRepository.findById(productTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Loại sản phẩm không tồn tại."));

        if (budgetLevel.compareTo(type.getMinBudget()) < 0) {
            NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));
            throw new InvalidBudgetException("Ngân sách tối thiểu cho " + type.getName() + " là " + nf.format(type.getMinBudget()) + "đ.");
        }

        Set<Integer> allowed = Set.copyOf(parseCategoryIds(type.getAllowedCategoryIds()));

        for (Material m : materialRepository.findAllById(selectedMaterials.keySet())) {
            if (!allowed.contains(m.getCategory().getId())) {
                throw new BudgetExceededException("\"" + m.getName() + "\" không dùng được cho " + type.getName() + ".");
            }
        }
        validateDesignCart(budgetLevel, selectedMaterials);
    }

    // Bản 2 tham số: dùng chung cho cả bước thiết kế và bước checkout
    public void validateDesignCart(BigDecimal budgetLevel, Map<Integer, Integer> selectedMaterials) {
        if (selectedMaterials == null || selectedMaterials.isEmpty()) {
            throw new IllegalArgumentException("Bạn chưa chọn nguyên liệu nào.");
        }

        BudgetRule rule = getActiveBudgetRule(budgetLevel);

        List<Material> materials = materialRepository.findAllById(selectedMaterials.keySet());
        if (materials.size() != selectedMaterials.size()) {
            throw new ResourceNotFoundException("Có nguyên liệu không còn tồn tại, vui lòng tải lại trang.");
        }

        Map<DesignGroup, Integer> counts = new EnumMap<>(DesignGroup.class);
        BigDecimal total = BigDecimal.ZERO;

        for (Material m : materials) {
            int qty = selectedMaterials.getOrDefault(m.getId(), 0);
            if (qty <= 0) continue;

            if (Boolean.TRUE.equals(m.getIsDeleted())) {
                throw new OutOfStockException("\"" + m.getName() + "\" hiện không còn bán.");
            }
            if (qty > m.getStock()) {
                throw new OutOfStockException("\"" + m.getName() + "\" chỉ còn " + m.getStock() + " trong kho.");
            }
            total = total.add(m.getPrice().multiply(BigDecimal.valueOf(qty)));

            DesignGroup g = m.getCategory().getDesignGroup() == null ? DesignGroup.OTHER : m.getCategory().getDesignGroup();
            counts.merge(g, qty, Integer::sum);
        }

        int countMainFlowers = counts.getOrDefault(DesignGroup.MAIN, 0);
        int countSubFlowers = counts.getOrDefault(DesignGroup.SUB, 0);
        int countLeaves = counts.getOrDefault(DesignGroup.LEAF, 0);
        int countPackaging = counts.getOrDefault(DesignGroup.PACKAGING, 0);
        int countAccessories = counts.getOrDefault(DesignGroup.ACCESSORY, 0);

        // Bắt buộc phải có ít nhất 1 loại hoa chính (hoặc hoa phụ) trong thiết kế
        if (countMainFlowers + countSubFlowers <= 0) {
            throw new BudgetExceededException("Hãy chọn ít nhất một loại hoa cho thiết kế của bạn.");
        }

        if (countMainFlowers > rule.getMaxMainFlowers()) {
            throw new BudgetExceededException("Bạn chỉ được chọn tối đa " + rule.getMaxMainFlowers() + " hoa chính.");
        }
        if (countSubFlowers > rule.getMaxSubFlowers()) {
            throw new BudgetExceededException("Bạn chỉ được chọn tối đa " + rule.getMaxSubFlowers() + " hoa phụ.");
        }
        if (countLeaves > rule.getMaxLeaves()) {
            throw new BudgetExceededException("Số lượng lá trang trí vượt quá giới hạn (" + rule.getMaxLeaves() + ").");
        }
        if (countAccessories > rule.getMaxAccessories()) {
            throw new BudgetExceededException("Số lượng phụ kiện vượt quá giới hạn (" + rule.getMaxAccessories() + ").");
        }
        if (countPackaging > rule.getMaxPackaging()) {
            throw new BudgetExceededException("Số lượng giấy gói/hộp vượt quá giới hạn (" + rule.getMaxPackaging() + ").");
        }

        // Một phần giá gói dành cho công thợ, hướng dẫn và lợi nhuận nên tiền nguyên liệu không được chạm 100% mức giá
        BigDecimal materialBudget = getMaterialBudget(budgetLevel);
        if (total.compareTo(materialBudget) > 0) {
            NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));
            throw new BudgetExceededException("Tổng nguyên liệu " + nf.format(total) + "đ đang vượt định mức nguyên liệu "
                    + nf.format(materialBudget) + "đ của gói " + nf.format(budgetLevel)
                    + "đ. Hãy bỏ bớt hoặc nâng lên mức giá tiếp theo.");
        }
    }
    // Tự động gợi ý bộ nguyên liệu nằm trong định mức + ngân sách để khách không phải chọn từ trang trắng.
    // seed == null: ngẫu nhiên mỗi lần; truyền seed để lặp lại được kết quả.
    public List<DesignItemReq> autoFillDesign(Integer productTypeId, BigDecimal budgetLevel, String themeColor, Long seed) {
        ProductType type = productTypeRepository.findById(productTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Loại sản phẩm không tồn tại."));

        if (budgetLevel.compareTo(type.getMinBudget()) < 0) {
            NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));
            throw new InvalidBudgetException("Ngân sách tối thiểu cho " + type.getName() + " là " + nf.format(type.getMinBudget()) + "đ.");
        }

        BudgetRule rule = getActiveBudgetRule(budgetLevel);
        List<Integer> allowedCategoryIds = parseCategoryIds(type.getAllowedCategoryIds());
        List<Material> available = materialRepository.findAvailableMaterialsByCategories(allowedCategoryIds);

        Random rnd = seed == null ? new Random() : new Random(seed);
        String color = themeColor == null ? "" : themeColor.trim().toLowerCase(Locale.ROOT);

        // Mỗi nhóm: nguyên liệu khớp tone màu đứng trước, trong từng phần xáo trộn ngẫu nhiên để mỗi lần một khác
        Map<DesignGroup, List<Material>> byGroup = new EnumMap<>(DesignGroup.class);
        for (Material m : available) {
            DesignGroup g = m.getCategory().getDesignGroup() == null ? DesignGroup.OTHER : m.getCategory().getDesignGroup();
            byGroup.computeIfAbsent(g, k -> new ArrayList<>()).add(m);
        }
        byGroup.replaceAll((g, list) -> {
            List<Material> matched = new ArrayList<>(), others = new ArrayList<>();
            for (Material m : list) {
                (!color.isEmpty() && matchesColor(m, color) ? matched : others).add(m);
            }
            Collections.shuffle(matched, rnd);
            Collections.shuffle(others, rnd);
            matched.addAll(others);
            return matched;
        });

        Map<Integer, Integer> picked = new LinkedHashMap<>();
        BigDecimal remaining = getMaterialBudget(budgetLevel);

        // 1-2. Bao bì và phụ kiện: mỗi loại 1 món nếu định mức cho phép
        remaining = pickGroupItems(byGroup.get(DesignGroup.PACKAGING), Math.min(1, rule.getMaxPackaging()), 1, remaining, picked);
        remaining = pickGroupItems(byGroup.get(DesignGroup.ACCESSORY), Math.min(1, rule.getMaxAccessories()), 1, remaining, picked);

        // 3. Hoa chính: dùng tối đa ~65% ngân sách còn lại
        BigDecimal mainPool = remaining.multiply(BigDecimal.valueOf(0.65));
        BigDecimal mainLeft = pickGroupItems(byGroup.get(DesignGroup.MAIN), rule.getMaxMainFlowers(), 3, mainPool, picked);
        remaining = remaining.subtract(mainPool.subtract(mainLeft));

        // 4. Hoa phụ, lá trang trí với phần còn lại
        remaining = pickGroupItems(byGroup.get(DesignGroup.SUB), rule.getMaxSubFlowers(), 2, remaining, picked);
        remaining = pickGroupItems(byGroup.get(DesignGroup.LEAF), rule.getMaxLeaves(), 2, remaining, picked);

        // 5. Còn dư và chưa chạm trần hoa chính -> bù thêm hoa chính
        int currentMain = countPickedInGroup(byGroup.get(DesignGroup.MAIN), picked);
        if (currentMain < rule.getMaxMainFlowers()) {
            pickGroupItems(byGroup.get(DesignGroup.MAIN), rule.getMaxMainFlowers() - currentMain, 3, remaining, picked);
        }

        List<DesignItemReq> result = new ArrayList<>();
        picked.forEach((id, qty) -> {
            if (qty > 0) {
                DesignItemReq item = new DesignItemReq();
                item.setMaterialId(id);
                item.setQuantity(qty);
                result.add(item);
            }
        });
        return result;
    }

    private boolean matchesColor(Material m, String keyword) {
        String color = m.getColor() != null ? m.getColor().toLowerCase(Locale.ROOT) : "";
        String name = m.getName() != null ? m.getName().toLowerCase(Locale.ROOT) : "";
        String desc = m.getDescription() != null ? m.getDescription().toLowerCase(Locale.ROOT) : "";
        return color.contains(keyword) || name.contains(keyword) || desc.contains(keyword);
    }

    private int countPickedInGroup(List<Material> groupMaterials, Map<Integer, Integer> picked) {
        if (groupMaterials == null) return 0;
        return groupMaterials.stream().mapToInt(m -> picked.getOrDefault(m.getId(), 0)).sum();
    }

    // Chọn tối đa maxGroupQty đơn vị, trải đều trên tối đa maxDistinct loại (xoay vòng từng đơn vị) trong ngân sách budgetPool.
    // Trả về phần ngân sách chưa dùng.
    private BigDecimal pickGroupItems(List<Material> candidates, int maxGroupQty, int maxDistinct,
                                      BigDecimal budgetPool, Map<Integer, Integer> picked) {
        if (candidates == null || candidates.isEmpty() || maxGroupQty <= 0 || budgetPool.signum() <= 0) {
            return budgetPool;
        }
        BigDecimal remaining = budgetPool;

        // Chọn các loại mua được (ít nhất 1 đơn vị) theo thứ tự ưu tiên, tối đa maxDistinct loại
        List<Material> chosen = new ArrayList<>();
        for (Material m : candidates) {
            if (chosen.size() >= maxDistinct) break;
            if (m.getPrice().signum() <= 0) continue;
            if (m.getStock() - picked.getOrDefault(m.getId(), 0) <= 0) continue;
            if (m.getPrice().compareTo(remaining) > 0) continue;
            chosen.add(m);
        }

        int added = 0;
        boolean progress = true;
        while (added < maxGroupQty && progress) {
            progress = false;
            for (Material m : chosen) {
                if (added >= maxGroupQty) break;
                if (m.getStock() - picked.getOrDefault(m.getId(), 0) <= 0) continue;
                if (m.getPrice().compareTo(remaining) > 0) continue;
                picked.merge(m.getId(), 1, Integer::sum);
                remaining = remaining.subtract(m.getPrice());
                added++;
                progress = true;
            }
        }
        return remaining;
    }
}