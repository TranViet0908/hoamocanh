package com.hoamocanh.dto.customer;

/** Định mức tối đa theo từng nhóm nguyên liệu của một mức giá (lấy từ BudgetRule). */
public record DesignLimitsRes(
        Integer maxMainFlowers,
        Integer maxSubFlowers,
        Integer maxLeaves,
        Integer maxAccessories,
        Integer maxPackaging) {
}