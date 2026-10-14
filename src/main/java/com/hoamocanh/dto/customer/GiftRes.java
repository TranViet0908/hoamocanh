package com.hoamocanh.dto.customer;

import java.util.List;

public record GiftRes(
        boolean isLocked,         // MỚI: Cờ kiểm tra quà có bị khóa không
        String passwordHint,      // MỚI: Gợi ý mật khẩu
        String orderCode,
        String senderName,
        String giftMessage,
        String giftImageUrl,
        String giftVideoUrl,
        String productTypeName,
        String style,
        String themeColor,
        List<Item> items) {

    public record Item(String name, Integer quantity, String imageUrl) {
    }
}