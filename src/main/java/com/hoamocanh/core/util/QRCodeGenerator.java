package com.hoamocanh.core.util;

import org.springframework.stereotype.Component;

@Component
public class QRCodeGenerator {

    /**
     * Sinh ra đường link chứa thông điệp hoặc đơn hàng.
     * Người nhận quét QR sẽ được dẫn thẳng tới web app để xem (Gửi tặng online).
     */
    public String generateGiftQRCode(String orderCode) {
        // Trả về link Frontend. VD: https://hoamocanh.com/gift/HMA-123456
        return "https://hoamocanh.com/gift/" + orderCode;
    }
}