package com.hoamocanh.service.admin;

import com.hoamocanh.core.entity.Order;
import com.hoamocanh.core.entity.SystemConfig;
import com.hoamocanh.dto.admin.CalendarDto.*;
import com.hoamocanh.repository.OrderRepository;
import com.hoamocanh.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Đặt lịch đến tiệm (đơn PICKUP: khách tự làm tại tiệm). Khung giờ cấu hình trong system_configs,
 * mặc định 09:00 - 18:00, mỗi khung 120 phút, tối đa 3 khách mỗi khung.
 */
@Service
@RequiredArgsConstructor
public class BookingService {

    private static final String K_OPEN = "booking.open", K_CLOSE = "booking.close",
            K_SLOT = "booking.slot-minutes", K_CAP = "booking.capacity", K_LOCK = "booking.lock";
    private static final int MAX_DAYS_AHEAD = 30;
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final SystemConfigRepository configRepository;
    private final OrderRepository orderRepository;

    // ---------- Cấu hình ----------
    @Transactional(readOnly = true)
    public BookingConfig getConfig() {
        Map<String, String> db = configRepository.findAllById(List.of(K_OPEN, K_CLOSE, K_SLOT, K_CAP)).stream()
                .collect(Collectors.toMap(SystemConfig::getConfigKey, SystemConfig::getConfigValue));
        return new BookingConfig(db.getOrDefault(K_OPEN, "09:00"), db.getOrDefault(K_CLOSE, "18:00"),
                toInt(db.get(K_SLOT), 120), toInt(db.get(K_CAP), 3));
    }

    @Transactional
    public BookingConfig saveConfig(BookingConfig c) {
        int open = LocalTime.parse(c.open()).toSecondOfDay() / 60, close = LocalTime.parse(c.close()).toSecondOfDay() / 60;
        if (open >= close) throw new IllegalArgumentException("Giờ đóng cửa phải sau giờ mở cửa.");
        // Phần giờ lẻ cuối ngày (VD 17:00-18:00 khi khung 120 phút) đơn giản là không mở thêm khung
        if (close - open < c.slotMinutes()) {
            throw new IllegalArgumentException("Khoảng giờ mở cửa phải dài hơn một khung giờ.");
        }
        put(K_OPEN, c.open(), "Giờ mở cửa nhận khách đặt lịch");
        put(K_CLOSE, c.close(), "Giờ đóng cửa nhận khách đặt lịch");
        put(K_SLOT, String.valueOf(c.slotMinutes()), "Độ dài mỗi khung giờ (phút)");
        put(K_CAP, String.valueOf(c.capacity()), "Số khách tối đa mỗi khung giờ");
        return getConfig();
    }

    private void put(String key, String value, String desc) {
        SystemConfig s = configRepository.findById(key)
                .orElseGet(() -> SystemConfig.builder().configKey(key).description(desc).build());
        s.setConfigValue(value);
        configRepository.save(s);
    }

    private static int toInt(String s, int def) {
        try { return s == null ? def : Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return def; }
    }

    private static List<LocalTime> starts(BookingConfig c) {
        int open = LocalTime.parse(c.open()).toSecondOfDay() / 60, close = LocalTime.parse(c.close()).toSecondOfDay() / 60;
        List<LocalTime> out = new ArrayList<>();
        for (int m = open; m + c.slotMinutes() <= close; m += c.slotMinutes()) out.add(LocalTime.of(m / 60, m % 60));
        return out;
    }

    private static boolean inSlot(Order o, LocalDateTime s, LocalDateTime e) {
        return !o.getDeliveryTime().isBefore(s) && o.getDeliveryTime().isBefore(e);
    }

    // ---------- Phía khách ----------
    @Transactional(readOnly = true)
    public List<PublicSlot> publicSlots(LocalDate date) {
        checkDateRange(date);
        BookingConfig c = getConfig();
        List<Order> orders = orderRepository.findPickupBetween(date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        LocalDateTime now = LocalDateTime.now();
        List<PublicSlot> out = new ArrayList<>();
        for (LocalTime st : starts(c)) {
            LocalDateTime s = date.atTime(st), e = s.plusMinutes(c.slotMinutes());
            int remaining = (int) Math.max(0, c.capacity() - orders.stream().filter(o -> inSlot(o, s, e)).count());
            out.add(new PublicSlot(st.format(HHMM), st.format(HHMM) + " – " + e.toLocalTime().format(HHMM),
                    remaining, remaining > 0 && s.isAfter(now)));
        }
        return out;
    }

    /** Gọi trong transaction của checkout: khóa dòng cấu hình rồi kiểm tra khung giờ còn chỗ. */
    @Transactional
    public void assertSlotAvailable(LocalDateTime t) {
        if (t == null) throw new IllegalArgumentException("Vui lòng chọn khung giờ đến tiệm.");
        checkDateRange(t.toLocalDate());
        BookingConfig c = getConfig();
        LocalTime time = t.toLocalTime().withSecond(0).withNano(0);
        if (!starts(c).contains(time)) throw new IllegalArgumentException("Vui lòng chọn một khung giờ có sẵn.");
        LocalDateTime s = t.toLocalDate().atTime(time), e = s.plusMinutes(c.slotMinutes());
        if (!s.isAfter(LocalDateTime.now())) throw new IllegalArgumentException("Khung giờ này đã qua, vui lòng chọn khung khác.");

        configRepository.lockByKey(K_LOCK).orElseGet(() -> configRepository.save(
                SystemConfig.builder().configKey(K_LOCK).configValue("1").description("Khóa nội bộ khi đặt lịch").build()));
        if (orderRepository.findPickupBetween(s, e).size() >= c.capacity()) {
            throw new IllegalArgumentException("Khung giờ này vừa hết chỗ, vui lòng chọn khung khác.");
        }
    }

    private static void checkDateRange(LocalDate d) {
        LocalDate today = LocalDate.now();
        if (d.isBefore(today) || d.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw new IllegalArgumentException("Chỉ đặt lịch trong vòng " + MAX_DAYS_AHEAD + " ngày tới.");
        }
    }

    // ---------- Phía admin ----------
    @Transactional(readOnly = true)
    public DayRes day(LocalDate date) {
        BookingConfig c = getConfig();
        List<Order> orders = orderRepository.findPickupBetween(date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        Set<Integer> placed = new HashSet<>();
        List<AdminSlot> slots = new ArrayList<>();
        for (LocalTime st : starts(c)) {
            LocalDateTime s = date.atTime(st), e = s.plusMinutes(c.slotMinutes());
            List<Booking> in = orders.stream().filter(o -> inSlot(o, s, e)).peek(o -> placed.add(o.getId())).map(BookingService::toBooking).toList();
            slots.add(new AdminSlot(st.format(HHMM), e.toLocalTime().format(HHMM), c.capacity(), in));
        }
        // Đơn đặt trước khi có lịch (khách tự nhập giờ) có thể nằm ngoài khung
        List<Booking> outside = orders.stream().filter(o -> !placed.contains(o.getId())).map(BookingService::toBooking).toList();
        return new DayRes(date.toString(), slots, outside, c);
    }

    @Transactional(readOnly = true)
    public List<DaySummary> summary(LocalDate from, int days) {
        int n = Math.max(1, Math.min(days, 60));
        BookingConfig c = getConfig();
        int cap = c.capacity() * starts(c).size();
        Map<LocalDate, Long> cnt = orderRepository.findPickupBetween(from.atStartOfDay(), from.plusDays(n).atStartOfDay()).stream()
                .collect(Collectors.groupingBy(o -> o.getDeliveryTime().toLocalDate(), Collectors.counting()));
        List<DaySummary> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            LocalDate d = from.plusDays(i);
            out.add(new DaySummary(d.toString(), cnt.getOrDefault(d, 0L).intValue(), cap));
        }
        return out;
    }

    private static Booking toBooking(Order o) {
        return new Booking(o.getId(), "MA-" + o.getId(), o.getReceiverName(), o.getReceiverPhone(),
                AdminOrderService.normalize(o.getStatus()),
                o.getPaymentStatus() == null ? "UNPAID" : o.getPaymentStatus(), o.getDeliveryTime());
    }
}