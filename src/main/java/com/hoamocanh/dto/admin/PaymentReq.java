package com.hoamocanh.dto.admin;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** Ghi nhận một khoản tiền đã nhận (đặt cọc hoặc thanh toán đủ). */
@Data
public class PaymentReq {
    @NotNull(message = "Vui lòng nhập số tiền.")
    @DecimalMin(value = "1", message = "Số tiền phải lớn hơn 0.")
    private BigDecimal amount;

    @NotBlank(message = "Vui lòng chọn hình thức nhận tiền.")
    private String method;          // CASH | BANK_TRANSFER | MOMO

    @NotBlank(message = "Vui lòng chọn loại khoản thu.")
    private String type;            // DEPOSIT | FULL_PAYMENT

    @Size(max = 100, message = "Mã giao dịch tối đa 100 ký tự.")
    private String transactionCode;
}
