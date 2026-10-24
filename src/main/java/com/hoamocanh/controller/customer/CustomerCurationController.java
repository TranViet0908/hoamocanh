package com.hoamocanh.controller.customer;

import com.hoamocanh.service.customer.ShopCurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/customer/shop/curation")
@RequiredArgsConstructor
public class CustomerCurationController {

    private final ShopCurationService service;

    @GetMapping
    public ResponseEntity<Map<String, Object>> get() {
        return ResponseEntity.ok(service.publicView());
    }
}