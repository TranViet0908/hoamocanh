package com.hoamocanh.controller.admin;

import com.hoamocanh.dto.admin.DashboardRes;
import com.hoamocanh.service.admin.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    @GetMapping
    public ResponseEntity<DashboardRes> summary() {
        return ResponseEntity.ok(dashboardService.getSummary());
    }
}
