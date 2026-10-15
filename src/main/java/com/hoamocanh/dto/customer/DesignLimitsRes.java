package com.hoamocanh.dto.customer;

import java.math.BigDecimal;

/**
 * Định mức của một mức giá, trả cho giao diện bước 4 (chọn nguyên liệu).
 * materialBudget: tiền nguyên liệu tối đa của gói (đã trừ phần nhân sự, hướng dẫn, lợi nhuận).
 * materialRatio: phần trăm mức giá dành cho nguyên liệu (VD 0.80); giao diện dùng để ước tính khi gợi ý nâng gói.
 */
public record DesignLimitsRes(
        Integer maxMainFlowers, Integer maxSubFlowers, Integer maxLeaves,
        Integer maxAccessories, Integer maxPackaging,
        BigDecimal materialBudget, BigDecimal materialRatio) {

    /** Giữ tương thích với nơi gọi cũ (chỉ truyền 5 giới hạn). */
    public DesignLimitsRes(Integer maxMainFlowers, Integer maxSubFlowers, Integer maxLeaves,
                           Integer maxAccessories, Integer maxPackaging) {
        this(maxMainFlowers, maxSubFlowers, maxLeaves, maxAccessories, maxPackaging, null, null);
    }
}