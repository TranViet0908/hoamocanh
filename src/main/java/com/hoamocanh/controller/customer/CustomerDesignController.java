package com.hoamocanh.controller.customer;

import com.hoamocanh.dto.customer.CartItemReq;
import com.hoamocanh.dto.customer.MaterialCatalogResp;
import com.hoamocanh.service.customer.CustomerDesignService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/customer/design")
@RequiredArgsConstructor
public class CustomerDesignController {

    private final CustomerDesignService customerDesignService;

    // API 1: Lấy danh sách nguyên liệu khả dụng theo Loại sản phẩm (productType: 1-6)
    @GetMapping("/materials")
    public ResponseEntity<List<MaterialCatalogResp>> getMaterials(@RequestParam Integer productType) {
        return ResponseEntity.ok(customerDesignService.getAvailableMaterialsForProduct(productType));
    }

    // API 2: Validate giỏ hàng trực tiếp (Dùng khi khách bấm chọn/bỏ chọn hoa trên màn hình)
    @PostMapping("/validate")
    public ResponseEntity<?> validateCart(
            @RequestParam Integer productType,
            @RequestParam BigDecimal budgetLevel,
            @RequestBody List<CartItemReq> cart) {
        try {
            // Nếu validate thành công (không ném ra Exception), trả về 200 OK
            customerDesignService.validateDesignCart(productType, budgetLevel, cart);
            return ResponseEntity.ok().body("Giỏ hàng hợp lệ");
        } catch (RuntimeException e) {
            // Nếu vượt budget hoặc quá số lượng hoa, trả về lỗi 400 Bad Request kèm message
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}