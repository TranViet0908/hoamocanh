package com.hoamocanh.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailRes(
        Integer id, String code, String status, String paymentStatus, String paymentMethod, String deliveryType,
        String customerName, String phone, String shippingAddress, LocalDateTime deliveryTime, LocalDateTime createdAt,
        String productTypeName, String style, String themeColor, String styleNote,
        BigDecimal totalPrice, BigDecimal discountAmount, BigDecimal payable, BigDecimal paid,
        String giftMessage, String giftImageUrl, String giftVideoUrl, String giftUrl,
        List<Item> items, List<Pay> payments, List<Hist> history, List<String> nextStatuses) {

    public record Item(String name, Integer quantity, BigDecimal unitPrice, String imageUrl) { }

    public record Pay(Integer id, BigDecimal amount, String method, String type, String status,
                      String transactionCode, LocalDateTime createdAt) { }

    public record Hist(String status, String note, String by, LocalDateTime at) { }
}
