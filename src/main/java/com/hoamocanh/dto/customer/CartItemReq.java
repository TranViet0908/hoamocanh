package com.hoamocanh.dto.customer;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CartItemReq {
    @NotNull(message = "ID nguyên liệu không được để trống")
    private Long materialId;

    @NotNull(message = "Số lượng không được để trống")
    @Min(value = 1, message = "Số lượng phải lớn hơn hoặc bằng 1")
    private Integer quantity;
}