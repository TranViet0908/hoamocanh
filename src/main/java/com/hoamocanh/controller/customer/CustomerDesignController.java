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

    @GetMapping("/budget-levels")
    public ResponseEntity<List<BigDecimal>> getBudgetLevels() {
        return ResponseEntity.ok(designService.getBudgetLevels());
    }

    @GetMapping("/limits")
    public ResponseEntity<DesignLimitsRes> getDesignLimits(@RequestParam BigDecimal budgetLevel) {
        return ResponseEntity.ok(designService.getLimits(budgetLevel));
    }

    @GetMapping("/materials")
    public ResponseEntity<List<MaterialDto>> getAvailableMaterials(
            @RequestParam Integer productType,
            @RequestParam BigDecimal budgetLevel) {
        return ResponseEntity.ok(designService.getAvailableMaterialsForDesign(productType, budgetLevel));
    }

    @PostMapping("/validate")
    public ResponseEntity<Map<String, String>> validateDesign(
            @RequestParam Integer productType,
            @RequestParam BigDecimal budgetLevel,
            @RequestBody List<DesignItemReq> items) {

        // Chuyển List<DesignItemReq> thành Map<Integer, Integer> (materialId -> quantity)
        Map<Integer, Integer> selectedMaterials = items.stream()
                .collect(Collectors.toMap(DesignItemReq::getMaterialId, DesignItemReq::getQuantity, Integer::sum));

        designService.validateDesignCart(productType, budgetLevel, selectedMaterials);
        return ResponseEntity.ok(Map.of("message", "Thiết kế hợp lệ."));
    }
}