package com.hoamocanh.repository;

import com.hoamocanh.core.entity.Order;
import com.hoamocanh.core.entity.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    // Tìm kiếm chi tiết đơn hàng theo Mã đơn (Order Code)
    Optional<Order> findByOrderCode(String orderCode);

    // Lọc danh sách đơn hàng theo trạng thái (VD: Lọc các đơn đang PENDING_CONFIRMATION)
    List<Order> findByStatus(OrderStatus status);

    // Lịch sử đặt hàng của khách hàng qua số điện thoại, sắp xếp mới nhất lên đầu
    List<Order> findByCustomerPhoneOrderByCreatedAtDesc(String customerPhone);
}