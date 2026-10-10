package com.hoamocanh.core.entity;

/**
 * Nhóm nguyên liệu dùng cho định mức (BudgetRule) ở bước thiết kế.
 * Mỗi Category được gán MỘT nhóm trong cột categories.design_group.
 * Thêm danh mục mới: chỉ cần chọn nhóm cho nó, không phải sửa code.
 */
public enum DesignGroup {
    MAIN,       // hoa chính      -> BudgetRule.maxMainFlowers
    SUB,        // hoa phụ        -> BudgetRule.maxSubFlowers
    LEAF,       // lá trang trí   -> BudgetRule.maxLeaves
    PACKAGING,  // giấy gói/hộp   -> BudgetRule.maxPackaging
    ACCESSORY,  // phụ kiện       -> BudgetRule.maxAccessories
    OTHER       // không giới hạn
}