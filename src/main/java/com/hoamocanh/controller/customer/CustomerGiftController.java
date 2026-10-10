package com.hoamocanh.controller.customer;

import com.hoamocanh.dto.customer.GiftRes;
import com.hoamocanh.service.customer.CustomerGiftService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customer/gift")
@RequiredArgsConstructor
public class CustomerGiftController {

    private final CustomerGiftService giftService;

    // Người nhận quà mở link: /customer/gift.html?code=<token>  ->  trang gọi API này
    @GetMapping
    public ResponseEntity<GiftRes> getGift(@RequestParam String code) {
        return ResponseEntity.ok(giftService.getGift(code));
    }
}