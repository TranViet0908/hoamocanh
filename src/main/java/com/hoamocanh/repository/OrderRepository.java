package com.hoamocanh.repository;

import com.hoamocanh.core.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {
    // Tạm thời dùng các hàm save(), findById() mặc định của JpaRepository
}