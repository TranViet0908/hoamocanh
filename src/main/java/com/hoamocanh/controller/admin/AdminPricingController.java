package com.hoamocanh.controller.admin;

import com.hoamocanh.dto.admin.PricingDto.*;
import com.hoamocanh.service.admin.AdminPricingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** /admin/pricing/** chỉ ADMIN (đã khai báo trong SecurityConfig). */
@RestController
@RequestMapping("/admin/pricing")
@RequiredArgsConstructor
public class AdminPricingController {

    private final AdminPricingService pricingService;

    @GetMapping("/budget-rules")
    public ResponseEntity<List<RuleRes>> rules() { return ResponseEntity.ok(pricingService.rules()); }

    @PostMapping("/budget-rules")
    public ResponseEntity<RuleRes> createRule(@Valid @RequestBody RuleReq req) { return ResponseEntity.ok(pricingService.createRule(req)); }

    @PutMapping("/budget-rules/{id}")
    public ResponseEntity<RuleRes> updateRule(@PathVariable Integer id, @Valid @RequestBody RuleReq req) {
        return ResponseEntity.ok(pricingService.updateRule(id, req));
    }

    @PatchMapping("/budget-rules/{id}/active")
    public ResponseEntity<RuleRes> setActive(@PathVariable Integer id, @Valid @RequestBody ActiveReq req) {
        return ResponseEntity.ok(pricingService.setRuleActive(id, req.active()));
    }

    @GetMapping("/product-types")
    public ResponseEntity<List<TypeRes>> types() { return ResponseEntity.ok(pricingService.types()); }

    @PutMapping("/product-types/{id}")
    public ResponseEntity<TypeRes> updateType(@PathVariable Integer id, @Valid @RequestBody TypeReq req) {
        return ResponseEntity.ok(pricingService.updateType(id, req));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CatItem>> categories() { return ResponseEntity.ok(pricingService.categories()); }

    @PostMapping("/categories")
    public ResponseEntity<CatItem> createCategory(@Valid @RequestBody CatReq req) { return ResponseEntity.ok(pricingService.createCategory(req)); }

    @PutMapping("/categories/{id}")
    public ResponseEntity<CatItem> updateCategory(@PathVariable Integer id, @Valid @RequestBody CatReq req) {
        return ResponseEntity.ok(pricingService.updateCategory(id, req));
    }
}
