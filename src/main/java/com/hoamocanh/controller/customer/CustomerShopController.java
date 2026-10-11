package com.hoamocanh.controller.customer;

import com.hoamocanh.dto.admin.ShopSettings;
import com.hoamocanh.service.admin.ShopSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Thông tin liên hệ + tài khoản nhận chuyển khoản của tiệm. Sửa trong trang quản trị > Cài đặt (gốc: shop.* trong application.properties). */
@RestController
@RequestMapping("/customer/shop-info")
@RequiredArgsConstructor
public class CustomerShopController {

    private final ShopSettingsService shopSettingsService;

    @GetMapping
    public ResponseEntity<Map<String, String>> info() {
        ShopSettings s = shopSettingsService.get();
        return ResponseEntity.ok(Map.of(
                "hotline", s.hotline(),
                "zalo", s.zalo(),
                "bankCode", s.bankCode(),
                "bankAccount", s.bankAccount(),
                "bankHolder", s.bankHolder()));
    }
}
