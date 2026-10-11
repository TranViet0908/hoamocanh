package com.hoamocanh.controller.admin;

import com.hoamocanh.dto.admin.ChangePasswordReq;
import com.hoamocanh.service.admin.AdminAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/account")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AdminAccountService accountService;

    @PutMapping("/password")
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordReq req, Authentication auth) {
        accountService.changePassword(auth.getName(), req);
        return ResponseEntity.ok(Map.of("message", "Đã đổi mật khẩu."));
    }
}
