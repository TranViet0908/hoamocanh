package com.hoamocanh.controller.customer;

import com.hoamocanh.core.exception.GiftUnlockException;
import com.hoamocanh.dto.customer.GiftRes;
import com.hoamocanh.service.customer.CustomerGiftService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/customer/gift")
@RequiredArgsConstructor
public class CustomerGiftController {

    private final CustomerGiftService giftService;

    // Gửi kèm HttpSession để kiểm tra xem trình duyệt ẩn danh đã nhập đúng mã chưa
    @GetMapping
    public ResponseEntity<GiftRes> getGift(@RequestParam String code, HttpSession session) {
        return ResponseEntity.ok(giftService.getGift(code, session));
    }

    // MỚI: API mở khóa quà
    @PostMapping("/unlock")
    public ResponseEntity<?> unlockGift(@RequestBody Map<String, String> payload, HttpSession session) {
        String code = payload.get("code");
        String password = payload.get("password");
        try {
            giftService.unlockGift(code, password, session);
            return ResponseEntity.ok(Map.of("message", "Mở khóa thành công"));
        } catch (GiftUnlockException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
        // Lỗi khác (link sai, quà không tồn tại...) để GlobalExceptionHandler trả 400/404 kèm message, không lộ lỗi nội bộ
    }
}