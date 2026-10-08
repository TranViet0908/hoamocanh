package com.hoamocanh.dto.customer;

import com.hoamocanh.core.entity.enums.OutputType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderSubmitReq {

    @NotBlank(message = "Tên khách hàng không được để trống")
    private String customerName;

    @NotBlank(message = "Số điện thoại không được để trống")
    private String customerPhone;

    @NotNull(message = "Loại sản phẩm (product type) không được để trống")
    private Integer productType;

    @NotNull(message = "Mức ngân sách không được để trống")
    private BigDecimal budgetLevel;

    @NotNull(message = "Hình thức đầu ra không được để trống")
    private OutputType outputType; // ONLINE_GIFT, DIY_AT_SHOP, SHOP_MADE

    private String styleNote; // Tone màu, phong cách khách chọn

    private String recipientName; // Tên người nhận (nếu có)
    private String deliveryAddress; // Địa chỉ giao (nếu chọn SHOP_MADE giao đi)
    private LocalDateTime appointmentTime; // Ngày giờ đến tiệm (nếu chọn DIY_AT_SHOP)
    private String giftMessage; // Lời nhắn/Thiệp

    @NotEmpty(message = "Danh sách thiết kế (giỏ hàng) không được để trống")
    @Valid // Kích hoạt validate cho từng phần tử bên trong list
    private List<CartItemReq> items;
}