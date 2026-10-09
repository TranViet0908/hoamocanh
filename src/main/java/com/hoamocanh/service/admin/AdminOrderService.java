package com.hoamocanh.service.admin;

import com.hoamocanh.core.entity.Order;
import com.hoamocanh.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private final OrderRepository orderRepository;

    public List<Order> getAllOrders() {
        // Lấy danh sách đơn hàng, ưu tiên đơn mới nhất lên đầu
        return orderRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Transactional
    public Order updateOrderStatus(Long orderId, String newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng"));

        // Trạng thái: CONFIRMED (Chờ duyệt) -> PROCESSING (Đang làm/Chuẩn bị nguyên liệu) -> COMPLETED (Hoàn tất)
        order.setStatus(newStatus);
        return orderRepository.save(order);
    }
}