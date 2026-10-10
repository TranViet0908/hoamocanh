package com.hoamocanh.dto.customer;

import java.util.List;

/** Dữ liệu công khai của trang quà tặng. KHÔNG chứa số điện thoại, địa chỉ hay giá. */
public record GiftRes(
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