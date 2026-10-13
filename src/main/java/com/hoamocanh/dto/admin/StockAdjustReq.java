package com.hoamocanh.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Cộng/trừ tồn kho theo mức thay đổi (VD +1, -1, +20) thay vì đặt số tuyệt đối, tránh ghi đè khi khách vừa đặt hàng. */
public record StockAdjustReq(
        @NotNull(message = "Thiếu mức thay đổi.")
        @Min(value = -10000, message = "Mức thay đổi quá lớn.")
        @Max(value = 10000, message = "Mức thay đổi quá lớn.") Integer delta) {
}