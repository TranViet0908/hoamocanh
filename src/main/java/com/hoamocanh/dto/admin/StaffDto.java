package com.hoamocanh.dto.admin;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

/** DTO của trang Nhân viên. */
public final class StaffDto {
    private StaffDto() { }

    public record StaffRes(Integer id, String fullName, String phone, String email, String role,
                           boolean active, LocalDateTime createdAt, boolean self) { }

    public record CreateReq(
            @NotBlank(message = "Vui lòng nhập họ tên.") @Size(max = 100, message = "Họ tên tối đa 100 ký tự.") String fullName,
            @NotBlank(message = "Vui lòng nhập số điện thoại.") @Pattern(regexp = "^0\\d{9,10}$", message = "Số điện thoại không đúng định dạng (VD: 0901234567).") String phone,
            @Size(max = 100, message = "Email tối đa 100 ký tự.") @Email(message = "Email không đúng định dạng.") String email,
            @NotBlank(message = "Vui lòng chọn vai trò.") @Pattern(regexp = "ADMIN|STAFF", message = "Vai trò không hợp lệ.") String role,
            @NotBlank(message = "Vui lòng nhập mật khẩu.") @Size(min = 8, max = 72, message = "Mật khẩu phải từ 8 đến 72 ký tự.") String password) { }

    public record UpdateReq(
            @NotBlank(message = "Vui lòng nhập họ tên.") @Size(max = 100, message = "Họ tên tối đa 100 ký tự.") String fullName,
            @Size(max = 100, message = "Email tối đa 100 ký tự.") @Email(message = "Email không đúng định dạng.") String email,
            @NotBlank(message = "Vui lòng chọn vai trò.") @Pattern(regexp = "ADMIN|STAFF", message = "Vai trò không hợp lệ.") String role) { }

    public record PasswordReq(
            @NotBlank(message = "Vui lòng nhập mật khẩu mới.") @Size(min = 8, max = 72, message = "Mật khẩu phải từ 8 đến 72 ký tự.") String newPassword) { }

    public record ActiveReq(@NotNull(message = "Thiếu trạng thái khóa/mở.") Boolean active) { }
}
