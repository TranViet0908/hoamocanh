package com.hoamocanh.dto.admin;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Thông tin tiệm hiển thị cho khách (hotline, Zalo, tài khoản nhận chuyển khoản). Chuỗi rỗng = chưa cấu hình. */
public record ShopSettings(
        @Pattern(regexp = "^[0-9+ .-]{0,20}$", message = "Hotline chỉ gồm số, dấu +, khoảng trắng, chấm, gạch ngang.") String hotline,
        @Pattern(regexp = "^[0-9+ .-]{0,20}$", message = "Số Zalo chỉ gồm số, dấu +, khoảng trắng, chấm, gạch ngang.") String zalo,
        @Pattern(regexp = "^[A-Za-z0-9]{0,20}$", message = "Mã ngân hàng chỉ gồm chữ và số (VD: VCB, MB, 970436).") String bankCode,
        @Pattern(regexp = "^[0-9]{0,25}$", message = "Số tài khoản chỉ gồm chữ số.") String bankAccount,
        @Size(max = 60, message = "Tên chủ tài khoản tối đa 60 ký tự.") String bankHolder) {
}
