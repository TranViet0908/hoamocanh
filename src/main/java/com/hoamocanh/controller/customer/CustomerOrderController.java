package com.hoamocanh.controller.customer;

import com.hoamocanh.core.entity.Order;
import com.hoamocanh.dto.customer.OrderSubmitReq;
import com.hoamocanh.service.customer.CustomerOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customer/orders")
@RequiredArgsConstructor
public class CustomerOrderController {

    private final CustomerOrderService customerOrderService;

    // API: Gửi đơn đặt hàng / Đặt lịch làm hoa
    // @Valid: Tự động bắt lỗi nếu Frontend truyền thiếu SĐT, Tên, hoặc Giỏ hàng trống
    @PostMapping("/submit")
    public ResponseEntity<?> submitOrder(@Valid @RequestBody OrderSubmitReq req) {
        try {
            Order savedOrder = customerOrderService.submitOrder(req);
            return ResponseEntity.ok(savedOrder);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}