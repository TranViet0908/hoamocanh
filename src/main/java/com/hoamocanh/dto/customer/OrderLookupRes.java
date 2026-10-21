package com.hoamocanh.dto.customer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Kết quả khách tra đơn bằng mã đơn + số điện thoại. giftUrl chỉ có với đơn gửi tặng online. */
public record OrderLookupRes(
        String orderCode,
        String status,
        String deliveryType,
        String paymentStatus,
        String paymentMethod,
        BigDecimal payableAmount,
        BigDecimal shippingFee,
        BigDecimal depositAmount,
        LocalDateTime deliveryTime,
        String giftUrl,
        String finishedImageUrl,
        String bookingCode) {
}