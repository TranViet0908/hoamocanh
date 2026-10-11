package com.hoamocanh.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrderStatusUpdateReq {
    @NotBlank(message = "Thiếu trạng thái mới.")
    private String newStatus;

    @Size(max = 255, message = "Ghi chú tối đa 255 ký tự.")
    private String note; // Lý do hủy hoặc ghi chú tiến độ
}
