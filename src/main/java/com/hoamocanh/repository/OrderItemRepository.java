package com.hoamocanh.repository;

import com.hoamocanh.core.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    // Lấy toàn bộ chi tiết nguyên liệu của một đơn hàng cụ thể
    List<OrderItem> findByOrderId(Long orderId);
}