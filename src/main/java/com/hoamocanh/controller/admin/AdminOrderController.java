package com.hoamocanh.controller.admin;

import com.hoamocanh.core.entity.Order;
import com.hoamocanh.service.admin.AdminOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService orderService;

    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    // FIX: Đổi Long id thành Integer id[cite: 20, 27]
    @PatchMapping("/{id}/status")
    public ResponseEntity<Order> updateStatus(@PathVariable Integer id, @RequestParam String status) {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, status));
    }
}