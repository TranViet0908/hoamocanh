package com.hoamocanh.dto.admin;

import java.math.BigDecimal;

/** Cấu hình phí và tỷ lệ trong system_configs. Tỷ lệ dạng thập phân: 0.20 = 20%. */
public record FeeConfigRes(
        BigDecimal laborRatio,    // LABOR_AND_MARGIN_RATIO: phần giá dành cho nhân sự + lợi nhuận, còn lại là tiền nguyên liệu tối đa
        BigDecimal depositRatio,  // WORKSHOP_DEPOSIT_RATIO: tỷ lệ cọc đơn tự làm tại tiệm
        BigDecimal shipFee,       // DEFAULT_SHIPPING_FEE: phí giao hàng (đồng)
        Integer holdMinutes) {    // booking.hold-minutes: số phút giữ chỗ khi chưa nhận cọc
}