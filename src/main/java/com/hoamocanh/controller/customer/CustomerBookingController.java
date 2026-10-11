package com.hoamocanh.controller.customer;

import com.hoamocanh.dto.admin.CalendarDto.PublicSlot;
import com.hoamocanh.service.admin.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Khung giờ đến tiệm còn chỗ, cho luồng "tự tay làm tại tiệm". */
@RestController
@RequestMapping("/customer/booking")
@RequiredArgsConstructor
public class CustomerBookingController {

    private final BookingService bookingService;

    @GetMapping("/slots")
    public ResponseEntity<List<PublicSlot>> slots(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(bookingService.publicSlots(date));
    }
}
