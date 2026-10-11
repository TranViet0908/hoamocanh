package com.hoamocanh.dto.admin;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MaterialReq {
    @NotNull(message = "Vui lòng chọn danh mục.")
    private Integer categoryId;

    @NotBlank(message = "Vui lòng nhập tên nguyên liệu.")
    @Size(max = 150, message = "Tên tối đa 150 ký tự.")
    private String name;

    private String description;
    private String imageUrl;

    @NotNull(message = "Vui lòng nhập giá bán.")
    @DecimalMin(value = "0", message = "Giá bán không được âm.")
    private BigDecimal price;

    @Min(value = 0, message = "Tồn kho không được âm.")
    private Integer stock;            // chỉ dùng khi THÊM MỚI; sửa tồn kho đi qua PATCH /{id}/stock

    @Size(max = 50, message = "Màu sắc tối đa 50 ký tự.")
    private String color;

    @Size(max = 20, message = "Đơn vị tính tối đa 20 ký tự.")
    private String unit;

    @Min(value = 0, message = "Ngưỡng sắp hết không được âm.")
    private Integer lowStockThreshold;
}
