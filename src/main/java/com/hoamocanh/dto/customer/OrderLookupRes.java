package com.hoamocanh.dto.customer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
        String bookingCode,
        List<Item> items) {

    /** Dòng mẫu có sẵn của đơn mua từ giỏ hàng (đơn tự thiết kế để trống). */
    public record Item(String name, Integer quantity, BigDecimal unitPrice, String imageUrl) { }
}