package com.hoamocanh.controller.customer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Thông tin liên hệ + tài khoản nhận chuyển khoản của tiệm, cấu hình trong application.properties (shop.*). */
@RestController
@RequestMapping("/customer/shop-info")
public class CustomerShopController {

    @Value("${shop.hotline:}") private String hotline;
    @Value("${shop.zalo:}") private String zalo;
    @Value("${shop.bank-code:}") private String bankCode;
    @Value("${shop.bank-account:}") private String bankAccount;
    @Value("${shop.bank-holder:}") private String bankHolder;

    @GetMapping
    public ResponseEntity<Map<String, String>> info() {
        return ResponseEntity.ok(Map.of(
                "hotline", hotline,
                "zalo", zalo,
                "bankCode", bankCode,
                "bankAccount", bankAccount,
                "bankHolder", bankHolder));
    }
}