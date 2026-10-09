package com.hoamocanh.dto.admin;

import lombok.Data;

@Data
public class OrderStatusUpdateReq {
    private String newStatus;
    private String note; // Lý do hủy hoặc ghi chú tiến độ
}