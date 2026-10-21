package com.hoamocanh.controller.admin;

import com.hoamocanh.dto.admin.FeeConfigRes;
import com.hoamocanh.service.admin.FeeConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** /admin/pricing/** chỉ ADMIN (đã khai báo trong SecurityConfig), nên đường dẫn này cũng chỉ ADMIN. */
@RestController
@RequestMapping("/admin/pricing/fees")
@RequiredArgsConstructor
public class AdminFeeConfigController {

    private final FeeConfigService service;

    @GetMapping
    public ResponseEntity<FeeConfigRes> get() { return ResponseEntity.ok(service.get()); }

    @PutMapping
    public ResponseEntity<FeeConfigRes> save(@RequestBody FeeConfigRes req) { return ResponseEntity.ok(service.save(req)); }
}