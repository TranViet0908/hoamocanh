package com.hoamocanh.controller.admin;

import com.hoamocanh.dto.admin.CalendarDto.*;
import com.hoamocanh.service.admin.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/admin/calendar")
@RequiredArgsConstructor
public class AdminCalendarController {

    private final BookingService bookingService;

    @GetMapping("/day")
    public ResponseEntity<DayRes> day(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(bookingService.day(date));
    }

    @GetMapping("/summary")
    public ResponseEntity<List<DaySummary>> summary(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                    @RequestParam(defaultValue = "14") int days) {
        return ResponseEntity.ok(bookingService.summary(from, days));
    }

    @GetMapping("/config")
    public ResponseEntity<BookingConfig> config() {
        return ResponseEntity.ok(bookingService.getConfig());
    }

    // Chỉ ADMIN được đổi cấu hình khung giờ
    @PutMapping("/config")
    public ResponseEntity<BookingConfig> saveConfig(@Valid @RequestBody BookingConfig req, Authentication auth) {
        if (auth.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            throw new AccessDeniedException("Chỉ quản trị viên được thực hiện.");
        }
        return ResponseEntity.ok(bookingService.saveConfig(req));
    }
}
