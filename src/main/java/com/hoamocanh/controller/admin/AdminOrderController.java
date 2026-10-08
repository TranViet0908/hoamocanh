package com.hoamocanh.controller.admin;

import com.hoamocanh.core.entity.Order;
import com.hoamocanh.core.entity.enums.OrderStatus;
import com.hoamocanh.service.admin.AdminOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    // Lấy toàn bộ danh sách đơn hàng
    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        return ResponseEntity.ok(adminOrderService.getAllOrders());
    }

    // Lọc đơn hàng theo trạng thái (Dùng cho các tab: Đơn mới, Đang làm, Hoàn tất)
    @GetMapping("/filter")
    public ResponseEntity<List<Order>> getOrdersByStatus(@RequestParam OrderStatus status) {
        return ResponseEntity.ok(adminOrderService.getOrdersByStatus(status));
    }

    // Xem chi tiết đơn hàng (Cần để Florist nhìn vào gói hoa/phụ kiện mà cắm)
    @GetMapping("/{orderCode}")
    public ResponseEntity<?> getOrderDetails(@PathVariable String orderCode) {
        try {
            return ResponseEntity.ok(adminOrderService.getOrderDetails(orderCode));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Nhân viên chuyển trạng thái đơn hàng
    @PutMapping("/{orderCode}/status")
    public ResponseEntity<?> updateOrderStatus(@PathVariable String orderCode, @RequestParam OrderStatus status) {
        try {
            return ResponseEntity.ok(adminOrderService.updateOrderStatus(orderCode, status));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}