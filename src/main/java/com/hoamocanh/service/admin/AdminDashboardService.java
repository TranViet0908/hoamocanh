package com.hoamocanh.service.admin;

import com.hoamocanh.core.entity.Material;
import com.hoamocanh.core.entity.Order;
import com.hoamocanh.dto.admin.DashboardRes;
import com.hoamocanh.repository.MaterialRepository;
import com.hoamocanh.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final int DEFAULT_LOW_STOCK = 5;

    private final OrderRepository orderRepository;
    private final MaterialRepository materialRepository;

    @Transactional(readOnly = true)
    public DashboardRes getSummary() {
        LocalDate today = LocalDate.now();
        int todayCnt = 0, yesterdayCnt = 0, pending = 0, awaiting = 0, ready = 0, unpaid = 0;
        BigDecimal revenue = BigDecimal.ZERO;
        List<DashboardRes.TodayItem> schedule = new ArrayList<>();

        // 300 đơn gần nhất là đủ cho số liệu trong ngày
        for (Order o : orderRepository.findRecent(PageRequest.of(0, 300))) {
            String st = AdminOrderService.normalize(o.getStatus());
            boolean cancelled = "CANCELLED".equals(st);
            boolean finished = cancelled || "COMPLETED".equals(st);
            LocalDate created = o.getCreatedAt() == null ? null : o.getCreatedAt().toLocalDate();

            if (today.equals(created)) {
                todayCnt++;
                if (!cancelled) revenue = revenue.add(payable(o));
            } else if (today.minusDays(1).equals(created)) {
                yesterdayCnt++;
            }
            switch (st) {
                case "PENDING" -> pending++;
                case "AWAITING_APPROVAL" -> awaiting++;
                case "READY" -> ready++;
                default -> { }
            }
            if (!cancelled && !"PAID".equals(o.getPaymentStatus())) unpaid++;

            // Lịch hôm nay: khách đến tiệm hoặc giao hoa trong ngày, đơn chưa xong
            if (!finished && !"ONLINE".equals(o.getDeliveryType())
                    && o.getDeliveryTime() != null && o.getDeliveryTime().toLocalDate().equals(today)) {
                schedule.add(new DashboardRes.TodayItem(o.getId(), "MA-" + o.getId(), o.getReceiverName(),
                        o.getDeliveryType(), o.getDeliveryTime(), st));
            }
        }
        schedule.sort(Comparator.comparing(DashboardRes.TodayItem::time));

        int low = 0, out = 0;
        List<DashboardRes.StockItem> lowItems = new ArrayList<>();
        for (Material m : materialRepository.findAllWithCategory()) {
            if (Boolean.TRUE.equals(m.getIsDeleted())) continue;
            int threshold = m.getLowStockThreshold() == null ? DEFAULT_LOW_STOCK : m.getLowStockThreshold();
            if (m.getStock() <= 0) out++;
            else if (m.getStock() <= threshold) low++;
            if (m.getStock() <= threshold) {
                lowItems.add(new DashboardRes.StockItem(m.getId(), m.getName(), m.getStock(), m.getUnit(), threshold));
            }
        }
        lowItems.sort(Comparator.comparing(DashboardRes.StockItem::stock));

        return new DashboardRes(todayCnt, yesterdayCnt, revenue, pending, awaiting, ready, unpaid, low, out,
                schedule, lowItems.stream().limit(6).toList());
    }

    private static BigDecimal payable(Order o) {
        BigDecimal d = o.getDiscountAmount() == null ? BigDecimal.ZERO : o.getDiscountAmount();
        return o.getTotalPrice().subtract(d).max(BigDecimal.ZERO);
    }
}
