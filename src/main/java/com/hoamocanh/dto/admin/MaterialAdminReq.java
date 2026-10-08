package com.hoamocanh.dto.admin;

import com.hoamocanh.core.entity.enums.MaterialStatus;
import com.hoamocanh.core.entity.enums.MaterialType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MaterialAdminReq {

    @NotBlank(message = "Tên nguyên liệu không được để trống")
    private String name;

    @NotNull(message = "Loại nguyên liệu không được để trống")
    private MaterialType type;

    @NotBlank(message = "Các loại sản phẩm hỗ trợ không được để trống (VD: '1,2,3')")
    private String supportedProductTypes;

    @NotNull(message = "Giá bán không được để trống")
    @Min(value = 0, message = "Giá bán phải lớn hơn hoặc bằng 0")
    private BigDecimal price;

    @NotNull(message = "Số lượng tồn kho không được để trống")
    @Min(value = 0, message = "Số lượng không được âm")
    private Integer stockQuantity;

    private MaterialStatus status; // Trạng thái: AVAILABLE, OUT_OF_STOCK, HIDDEN
    private String imageUrl;
    private String color;
}