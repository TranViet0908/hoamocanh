package com.hoamocanh.controller.customer;

import com.hoamocanh.dto.customer.CheckoutReq;
import com.hoamocanh.dto.customer.OrderCheckoutRes;
import com.hoamocanh.service.customer.CustomerOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customer/orders")
@RequiredArgsConstructor
public class CustomerOrderController {

    private final CustomerOrderService orderService;

    // Trả DTO thay vì entity Order (tránh lỗi lazy/vòng lặp Order <-> OrderDetail khi serialize)
    @PostMapping("/checkout")
    public ResponseEntity<OrderCheckoutRes> checkout(@RequestBody CheckoutReq checkoutReq) {
        return ResponseEntity.ok(orderService.processCheckout(checkoutReq));
    }
}