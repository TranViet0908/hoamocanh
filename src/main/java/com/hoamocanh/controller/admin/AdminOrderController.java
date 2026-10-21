package com.hoamocanh.controller.admin;

import com.hoamocanh.dto.admin.*;
import com.hoamocanh.service.admin.AdminOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    // Tải ảnh lên bằng /admin/uploads/finished-photo trước, rồi gọi đây với {"url": "..."}; url rỗng = gỡ ảnh
    @PutMapping("/{id}/finished-photo")
    public ResponseEntity<OrderDetailRes> finishedPhoto(@PathVariable Integer id,
                                                        @RequestBody Map<String, String> body, Authentication auth) {
        return ResponseEntity.ok(orderService.setFinishedPhoto(id, body.get("url"), auth.getName()));
    }

    // Gỡ lời chúc/ảnh/video/hình mô phỏng khỏi quà tặng: part = message | image | video | bouquet
    @DeleteMapping("/{id}/gift-content")
    public ResponseEntity<OrderDetailRes> removeGiftContent(@PathVariable Integer id, @RequestParam String part, Authentication auth) {
        return ResponseEntity.ok(orderService.removeGiftContent(id, part, auth.getName()));
    }

    // Tạo lại link và mã QR quà tặng (link cũ hết hiệu lực)
    @PostMapping("/{id}/gift-link/regenerate")
    public ResponseEntity<OrderDetailRes> regenerateGiftLink(@PathVariable Integer id, Authentication auth) {
        return ResponseEntity.ok(orderService.regenerateGiftLink(id, auth.getName()));
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<OrderDetailRes> addPayment(@PathVariable Integer id,
                                                     @Valid @RequestBody PaymentReq req, Authentication auth) {
        return ResponseEntity.ok(orderService.addPayment(id, req, auth.getName()));
    }
}