package com.hoamocanh.controller.customer;

import com.hoamocanh.dto.customer.CheckoutReq;
import com.hoamocanh.dto.customer.OrderCheckoutRes;
import com.hoamocanh.dto.customer.OrderLookupRes;
import com.hoamocanh.dto.customer.OrderReviewReq;
import com.hoamocanh.service.customer.OrderFeeService;
import com.hoamocanh.service.customer.CustomerOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customer/orders")
@RequiredArgsConstructor
public class CustomerOrderController {

    private final CustomerOrderService orderService;
    private final OrderFeeService feeService;

    // Trả DTO thay vì entity Order (tránh lỗi lazy/vòng lặp Order <-> OrderDetail khi serialize)
    @PostMapping("/checkout")
    public ResponseEntity<OrderCheckoutRes> checkout(@RequestBody CheckoutReq checkoutReq) {
        return ResponseEntity.ok(orderService.processCheckout(checkoutReq));
    }

    // Phí giao hàng, % cọc, hạn giữ chỗ: để trang chốt đơn báo trước cho khách (server vẫn tự tính lại khi chốt)
    @GetMapping("/fees")
    public ResponseEntity<OrderFeeService.Fees> fees() {
        return ResponseEntity.ok(feeService.fees());
    }

    // Khách tra lại đơn và lấy lại link/QR quà tặng bằng mã đơn + số điện thoại
    @GetMapping("/lookup")
    public ResponseEntity<OrderLookupRes> lookup(@RequestParam String orderCode, @RequestParam String phone) {
        return ResponseEntity.ok(orderService.lookup(orderCode, phone));
    }

    // Khách duyệt ảnh thành phẩm hoặc yêu cầu chỉnh lại
    @PostMapping("/review")
    public ResponseEntity<OrderLookupRes> review(@RequestBody OrderReviewReq req) {
        return ResponseEntity.ok(orderService.review(req));
    }
}