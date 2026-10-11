package com.hoamocanh.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record DashboardRes(
        int ordersToday, int ordersYesterday, BigDecimal revenueToday,
        int pending, int awaitingApproval, int ready, int unpaid,
        int lowStock, int outOfStock,
        List<TodayItem> today, List<StockItem> lowItems) {

    public record TodayItem(Integer id, String code, String customerName, String deliveryType, LocalDateTime time, String status) { }

    public record StockItem(Integer id, String name, Integer stock, String unit, Integer threshold) { }
}
