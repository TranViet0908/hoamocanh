package com.hoamocanh.dto.admin;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

/** Các DTO của trang Mức giá & định mức. */
public final class PricingDto {
    private PricingDto() { }

    public record RuleReq(
            @NotNull(message = "Vui lòng nhập mức giá.") @DecimalMin(value = "1000", message = "Mức giá tối thiểu là 1.000đ.") BigDecimal budgetLevel,
            @NotNull(message = "Thiếu số hoa chính.") @Min(value = 0, message = "Số lượng không được âm.") @Max(value = 99, message = "Số lượng tối đa 99.") Integer maxMainFlowers,
            @NotNull(message = "Thiếu số hoa phụ.") @Min(value = 0, message = "Số lượng không được âm.") @Max(value = 99, message = "Số lượng tối đa 99.") Integer maxSubFlowers,
            @NotNull(message = "Thiếu số lá.") @Min(value = 0, message = "Số lượng không được âm.") @Max(value = 99, message = "Số lượng tối đa 99.") Integer maxLeaves,
            @NotNull(message = "Thiếu số phụ kiện.") @Min(value = 0, message = "Số lượng không được âm.") @Max(value = 99, message = "Số lượng tối đa 99.") Integer maxAccessories,
            @NotNull(message = "Thiếu số bao bì.") @Min(value = 0, message = "Số lượng không được âm.") @Max(value = 99, message = "Số lượng tối đa 99.") Integer maxPackaging) { }

    public record RuleRes(Integer id, BigDecimal budgetLevel, Integer maxMainFlowers, Integer maxSubFlowers,
                          Integer maxLeaves, Integer maxAccessories, Integer maxPackaging, boolean active) { }

    public record ActiveReq(@NotNull(message = "Thiếu trạng thái khóa/mở.") Boolean active) { }

    public record TypeReq(
            @NotBlank(message = "Vui lòng nhập tên loại sản phẩm.") @Size(max = 100, message = "Tên tối đa 100 ký tự.") String name,
            @NotNull(message = "Vui lòng nhập mức giá tối thiểu.") @DecimalMin(value = "0", message = "Mức giá tối thiểu không được âm.") BigDecimal minBudget,
            @NotEmpty(message = "Chọn ít nhất một danh mục được phép dùng.") List<Integer> categoryIds) { }

    public record TypeRes(Integer id, String name, BigDecimal minBudget, List<Integer> categoryIds) { }

    public record CatReq(
            @NotBlank(message = "Vui lòng nhập tên danh mục.") @Size(max = 100, message = "Tên tối đa 100 ký tự.") String name,
            String description,
            @NotBlank(message = "Vui lòng chọn nhóm định mức.") String designGroup) { }

    public record CatItem(Integer id, String name, String description, String designGroup) { }
}
