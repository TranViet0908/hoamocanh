package com.hoamocanh.dto.admin;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;

/** DTO của Lịch trải nghiệm (đặt lịch đến tiệm). */
public final class CalendarDto {
    private CalendarDto() { }

    public record BookingConfig(
            @NotBlank(message = "Vui lòng nhập giờ mở cửa.") @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Giờ mở cửa không đúng định dạng HH:mm.") String open,
            @NotBlank(message = "Vui lòng nhập giờ đóng cửa.") @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Giờ đóng cửa không đúng định dạng HH:mm.") String close,
            @NotNull(message = "Vui lòng nhập độ dài mỗi khung.") @Min(value = 30, message = "Mỗi khung tối thiểu 30 phút.") @Max(value = 480, message = "Mỗi khung tối đa 8 tiếng.") Integer slotMinutes,
            @NotNull(message = "Vui lòng nhập số chỗ mỗi khung.") @Min(value = 1, message = "Mỗi khung có ít nhất 1 chỗ.") @Max(value = 50, message = "Mỗi khung tối đa 50 chỗ.") Integer capacity) { }

    /** Khung giờ khách thấy khi đặt lịch. */
    public record PublicSlot(String start, String label, int remaining, boolean available) { }

    public record Booking(Integer orderId, String code, String customerName, String phone,
                          String status, String paymentStatus, LocalDateTime time) { }

    public record AdminSlot(String start, String end, int capacity, List<Booking> bookings) { }

    public record DayRes(String date, List<AdminSlot> slots, List<Booking> outside, BookingConfig config) { }

    public record DaySummary(String date, int booked, int capacity) { }
}
