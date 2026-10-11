package com.hoamocanh.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StockReq(@NotNull(message = "Thiếu số lượng.") @Min(value = 0, message = "Tồn kho không được âm.") Integer stock) {
}
