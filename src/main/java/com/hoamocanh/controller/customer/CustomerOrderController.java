package com.hoamocanh.controller.customer;

import com.hoamocanh.core.entity.Order;
import com.hoamocanh.dto.customer.CheckoutReq;
import com.hoamocanh.service.customer.CustomerOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customer/orders")
@RequiredArgsConstructor
public class CustomerOrderController {

    private final CustomerOrderService orderService;

    /**
     * API: Khách hàng chốt đơn
     * POST /api/customer/orders/checkout
     */
    @PostMapping("/checkout")
    public ResponseEntity<Order> checkout(@RequestBody CheckoutReq checkoutReq) {
        Order savedOrder = orderService.processCheckout(checkoutReq);
        return ResponseEntity.ok(savedOrder);
    }
}