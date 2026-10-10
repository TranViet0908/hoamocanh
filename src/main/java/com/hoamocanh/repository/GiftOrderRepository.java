package com.hoamocanh.repository;

import com.hoamocanh.core.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// Repository riêng cho trang quà tặng để không phải sửa OrderRepository; có thể gộp vào OrderRepository sau
@Repository
public interface GiftOrderRepository extends JpaRepository<Order, Integer> {
    Optional<Order> findByQrCodeUrlEndingWith(String suffix);
}