package com.hoamocanh.controller.customer;

import com.hoamocanh.core.entity.Material;
import com.hoamocanh.dto.customer.DesignItemReq;
import com.hoamocanh.service.customer.CustomerDesignService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/customer/design")
@RequiredArgsConstructor
public class CustomerDesignController {

    private final CustomerDesignService designService;

    @GetMapping("/materials")
    public ResponseEntity<List<Material>> getMaterialsForDesign(
            @RequestParam Integer productType,
            @RequestParam BigDecimal budgetLevel) {
        List<Material> materials = designService.getAvailableMaterialsForDesign(productType, budgetLevel);
        return ResponseEntity.ok(materials);
    }

    @PostMapping("/validate")
    public ResponseEntity<Map<String, String>> validateCart(
            @RequestParam Integer productType, // Vẫn giữ tham số này để không làm hỏng request từ Frontend
            @RequestParam BigDecimal budgetLevel,
            @RequestBody List<DesignItemReq> items) {

        Map<Integer, Integer> itemMap = items.stream()
                .collect(Collectors.toMap(DesignItemReq::getMaterialId, DesignItemReq::getQuantity));

        // ĐÃ FIX: Chỉ truyền 2 tham số đúng với Service (Bỏ productType)
        designService.validateDesignCart(budgetLevel, itemMap);

        return ResponseEntity.ok(Map.of("status", "success", "message", "Thiết kế hợp lệ với ngân sách"));
    }
}