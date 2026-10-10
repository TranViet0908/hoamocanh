package com.hoamocanh.controller.customer;

import com.hoamocanh.dto.customer.DesignItemReq;
import com.hoamocanh.dto.customer.DesignLimitsRes;
import com.hoamocanh.dto.customer.MaterialDto;
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

    // Trả DTO thay vì entity: tránh lỗi serialize LAZY category và không lộ version/isDeleted
    @GetMapping("/materials")
    public ResponseEntity<List<MaterialDto>> getMaterialsForDesign(
            @RequestParam Integer productType,
            @RequestParam BigDecimal budgetLevel) {
        return ResponseEntity.ok(designService.getAvailableMaterialsForDesign(productType, budgetLevel));
    }

    // MỚI
    @GetMapping("/limits")
    public ResponseEntity<DesignLimitsRes> getLimits(@RequestParam BigDecimal budgetLevel) {
        return ResponseEntity.ok(designService.getLimits(budgetLevel));
    }

    @PostMapping("/validate")
    public ResponseEntity<Map<String, String>> validateCart(
            @RequestParam Integer productType,
            @RequestParam BigDecimal budgetLevel,
            @RequestBody List<DesignItemReq> items) {

        // Gộp dòng trùng materialId (toMap cũ sẽ ném lỗi nếu trùng), bỏ số lượng <= 0
        Map<Integer, Integer> itemMap = items.stream()
                .filter(i -> i.getQuantity() != null && i.getQuantity() > 0)
                .collect(Collectors.toMap(DesignItemReq::getMaterialId, DesignItemReq::getQuantity, Integer::sum));

        designService.validateDesignCart(productType, budgetLevel, itemMap);

        return ResponseEntity.ok(Map.of("status", "success", "message", "Thiết kế hợp lệ với ngân sách"));
    }
}