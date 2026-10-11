package com.hoamocanh.controller.admin;

import com.hoamocanh.dto.admin.*;
import com.hoamocanh.service.admin.AdminOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService orderService;

    @GetMapping
    public ResponseEntity<List<OrderListRes>> list() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDetailRes> detail(@PathVariable Integer id) {
        return ResponseEntity.ok(orderService.getDetail(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderDetailRes> updateStatus(@PathVariable Integer id,
                                                       @Valid @RequestBody OrderStatusUpdateReq req, Authentication auth) {
        return ResponseEntity.ok(orderService.updateStatus(id, req, auth.getName()));
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<OrderDetailRes> addPayment(@PathVariable Integer id,
                                                     @Valid @RequestBody PaymentReq req, Authentication auth) {
        return ResponseEntity.ok(orderService.addPayment(id, req, auth.getName()));
    }
}
