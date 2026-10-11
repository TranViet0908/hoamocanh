package com.hoamocanh.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginReq {
    @NotBlank(message = "Vui lòng nhập số điện thoại.")
    private String phone;

    @NotBlank(message = "Vui lòng nhập mật khẩu.")
    private String password;
}
