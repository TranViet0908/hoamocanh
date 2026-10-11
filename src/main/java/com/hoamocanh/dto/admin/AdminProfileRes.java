package com.hoamocanh.dto.admin;

/** Thông tin người đang đăng nhập trang quản trị. role: ADMIN | STAFF */
public record AdminProfileRes(String fullName, String phone, String role) {
}
