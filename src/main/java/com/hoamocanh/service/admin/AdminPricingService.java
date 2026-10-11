package com.hoamocanh.service.admin;

import com.hoamocanh.core.entity.BudgetRule;
import com.hoamocanh.core.entity.Category;
import com.hoamocanh.core.entity.DesignGroup;
import com.hoamocanh.core.entity.ProductType;
import com.hoamocanh.core.exception.ResourceNotFoundException;
import com.hoamocanh.dto.admin.PricingDto.*;
import com.hoamocanh.repository.BudgetRuleRepository;
import com.hoamocanh.repository.CategoryRepository;
import com.hoamocanh.repository.ProductTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminPricingService {

    private final BudgetRuleRepository ruleRepository;
    private final ProductTypeRepository typeRepository;
    private final CategoryRepository categoryRepository;

    // ---------- Mức giá & định mức ----------
    @Transactional(readOnly = true)
    public List<RuleRes> rules() {
        return ruleRepository.findAllByOrderByBudgetLevelAsc().stream().map(AdminPricingService::toRes).toList();
    }

    @Transactional
    public RuleRes createRule(RuleReq q) {
        if (ruleRepository.existsByBudgetLevel(q.budgetLevel())) throw new IllegalArgumentException("Mức giá này đã tồn tại.");
        BudgetRule r = new BudgetRule();
        r.setBudgetLevel(q.budgetLevel());
        r.setActive(true);
        copyCounts(r, q);
        return toRes(ruleRepository.save(r));
    }

    @Transactional
    public RuleRes updateRule(Integer id, RuleReq q) {
        BudgetRule r = findRule(id);
        if (r.getBudgetLevel().compareTo(q.budgetLevel()) != 0) {
            throw new IllegalArgumentException("Không đổi được số tiền của mức giá. Hãy tạo mức mới và khóa mức cũ.");
        }
        copyCounts(r, q);
        return toRes(ruleRepository.save(r));
    }

    @Transactional
    public RuleRes setRuleActive(Integer id, boolean active) {
        BudgetRule r = findRule(id);
        r.setActive(active);
        return toRes(ruleRepository.save(r));
    }

    private BudgetRule findRule(Integer id) {
        return ruleRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mức giá."));
    }

    private static void copyCounts(BudgetRule r, RuleReq q) {
        r.setMaxMainFlowers(q.maxMainFlowers());
        r.setMaxSubFlowers(q.maxSubFlowers());
        r.setMaxLeaves(q.maxLeaves());
        r.setMaxAccessories(q.maxAccessories());
        r.setMaxPackaging(q.maxPackaging());
    }

    private static RuleRes toRes(BudgetRule r) {
        return new RuleRes(r.getId(), r.getBudgetLevel(), r.getMaxMainFlowers(), r.getMaxSubFlowers(), r.getMaxLeaves(),
                r.getMaxAccessories(), r.getMaxPackaging(), !Boolean.FALSE.equals(r.getActive()));
    }

    // ---------- Loại sản phẩm (6 kiểu dáng cố định: chỉ sửa tên, giá tối thiểu, danh mục được dùng) ----------
    @Transactional(readOnly = true)
    public List<TypeRes> types() {
        return typeRepository.findAll().stream().sorted(Comparator.comparing(ProductType::getId)).map(AdminPricingService::toRes).toList();
    }

    @Transactional
    public TypeRes updateType(Integer id, TypeReq q) {
        ProductType t = typeRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại sản phẩm."));
        Set<Integer> ids = new TreeSet<>(q.categoryIds());
        long valid = categoryRepository.findAllById(ids).stream().filter(c -> !Boolean.TRUE.equals(c.getIsDeleted())).count();
        if (valid != ids.size()) throw new IllegalArgumentException("Có danh mục không tồn tại, vui lòng tải lại trang.");

        t.setName(q.name().trim());
        t.setMinBudget(q.minBudget());
        t.setAllowedCategoryIds(ids.stream().map(String::valueOf).collect(Collectors.joining(",")));
        return toRes(typeRepository.save(t));
    }

    private static TypeRes toRes(ProductType t) {
        List<Integer> ids = Arrays.stream(t.getAllowedCategoryIds().split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).map(Integer::parseInt).toList();
        return new TypeRes(t.getId(), t.getName(), t.getMinBudget(), ids);
    }

    // ---------- Danh mục + nhóm định mức ----------
    @Transactional(readOnly = true)
    public List<CatItem> categories() {
        return categoryRepository.findAllByOrderByIdAsc().stream()
                .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted())).map(AdminPricingService::toItem).toList();
    }

    @Transactional
    public CatItem createCategory(CatReq q) {
        Category c = Category.builder().isDeleted(false).build();
        applyCategory(c, q);
        return toItem(categoryRepository.save(c));
    }

    @Transactional
    public CatItem updateCategory(Integer id, CatReq q) {
        Category c = categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục."));
        applyCategory(c, q);
        return toItem(categoryRepository.save(c));
    }

    private static void applyCategory(Category c, CatReq q) {
        DesignGroup g;
        try { g = DesignGroup.valueOf(q.designGroup()); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Nhóm định mức không hợp lệ."); }
        c.setName(q.name().trim());
        c.setDescription(q.description() == null || q.description().isBlank() ? null : q.description().trim());
        c.setDesignGroup(g);
    }

    private static CatItem toItem(Category c) {
        return new CatItem(c.getId(), c.getName(), c.getDescription(),
                c.getDesignGroup() == null ? "OTHER" : c.getDesignGroup().name());
    }
}
