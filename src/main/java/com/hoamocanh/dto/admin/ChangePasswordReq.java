package com.hoamocanh.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordReq {
    @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại.")
    private String currentPassword;

    @NotBlank(message = "Vui lòng nhập mật khẩu mới.")
    @Size(min = 8, max = 72, message = "Mật khẩu mới phải từ 8 đến 72 ký tự.")
    private String newPassword;
}
