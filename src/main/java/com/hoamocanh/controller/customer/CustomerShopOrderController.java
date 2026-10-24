package com.hoamocanh.controller.customer;

import com.hoamocanh.dto.customer.OrderCheckoutRes;
import com.hoamocanh.dto.customer.ShopCheckoutReq;
import com.hoamocanh.service.customer.CustomerShopOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customer/orders")
@RequiredArgsConstructor
public class CustomerShopOrderController {

    private final CustomerShopOrderService service;

    // Khách đã đăng nhập thì đơn được gắn vào tài khoản; khách vãng lai vẫn đặt được
    @PostMapping("/shop-checkout")
    public ResponseEntity<OrderCheckoutRes> checkout(@RequestBody ShopCheckoutReq req, Authentication auth) {
        return ResponseEntity.ok(service.checkout(req, service.findCustomer(auth)));
    }
}