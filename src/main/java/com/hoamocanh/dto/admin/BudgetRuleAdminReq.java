package com.hoamocanh.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BudgetRuleAdminReq {

    @NotNull(message = "Loại sản phẩm không được để trống")
    private Integer productType;

    @NotNull(message = "Mức giá không được để trống")
    @Min(value = 400000, message = "Mức giá tối thiểu là 400.000đ")
    private BigDecimal budgetLevel;

    @NotNull(message = "Số lượng hoa chính tối đa không được để trống")
    @Min(0)
    private Integer maxMainFlowers;

    @NotNull(message = "Số lượng hoa phụ tối đa không được để trống")
    @Min(0)
    private Integer maxSubFlowers;

    @NotNull(message = "Chi phí nhân sự không được để trống")
    @Min(0)
    private BigDecimal staffCost;

    @NotNull(message = "Biên lợi nhuận tối thiểu không được để trống")
    @Min(0)
    private BigDecimal minProfitMargin;

    private Boolean isActive;
}