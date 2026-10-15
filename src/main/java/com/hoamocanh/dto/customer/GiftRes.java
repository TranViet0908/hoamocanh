package com.hoamocanh.dto.customer;

import java.util.List;

public record GiftRes(
        String orderCode,
        String senderName,
        String giftMessage,
        String giftImageUrl,
        String giftVideoUrl,
        String bouquetImageUrl,   // ảnh đóa hoa mô phỏng đã chốt
        String productTypeName,
        String style,
        String themeColor,
        List<Item> items) {

    public record Item(String name, Integer quantity, String imageUrl) {
    }
}