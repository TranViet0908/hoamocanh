package com.hoamocanh.repository;

import com.hoamocanh.core.entity.Order;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/** "Đơn của tôi": đơn gắn với tài khoản, hoặc đơn có số điện thoại người nhận/người đặt trùng số của tài khoản. */
@Repository
public interface MyOrderRepository extends JpaRepository<Order, Integer> {
    @Query("SELECT o FROM Order o WHERE (o.user IS NOT NULL AND o.user.id = :uid) OR o.receiverPhone = :phone OR o.buyerPhone = :phone ORDER BY o.createdAt DESC")
    List<Order> findMine(@Param("uid") Integer uid, @Param("phone") String phone, Pageable pageable);
}