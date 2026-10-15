package com.hoamocanh.dto.customer;

/** Khách duyệt ảnh thành phẩm: approve = true là đồng ý, false là yêu cầu chỉnh lại (bắt buộc có note). */
public record OrderReviewReq(String orderCode, String phone, boolean approve, String note) {
}