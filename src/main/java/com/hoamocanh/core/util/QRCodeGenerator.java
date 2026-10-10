package com.hoamocanh.core.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class QRCodeGenerator {

    // Khi có tên miền thật chỉ cần đổi app.public-base-url trong application.properties
    @Value("${app.public-base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * Sinh đường link trang quà tặng. Người nhận quét QR sẽ mở trang gift.html để xem lời chúc.
     * @param token chuỗi ngẫu nhiên (không dùng id đơn để người lạ không đoán được link)
     */
    public String generateGiftQRCode(String token) {
        return baseUrl.replaceAll("/+$", "") + "/customer/gift.html?code=" + token;
    }
}