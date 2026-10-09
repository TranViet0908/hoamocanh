package com.hoamocanh.controller.customer;

import com.hoamocanh.core.entity.ProductType;
import com.hoamocanh.service.customer.CustomerProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/customer/products")
@RequiredArgsConstructor
public class CustomerProductController {

    private final CustomerProductService productService;

    @GetMapping("/types")
    public ResponseEntity<List<ProductType>> getProductTypes() {
        return ResponseEntity.ok(productService.getAllActiveProductTypes());
    }
}