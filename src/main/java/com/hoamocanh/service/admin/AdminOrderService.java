package com.hoamocanh.service.admin;

import com.hoamocanh.core.entity.Order;
import com.hoamocanh.core.entity.enums.OrderStatus;
import com.hoamocanh.repository.OrderRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private final OrderRepository orderRepository;

    // Lấy toàn bộ đơn hàng (Thường sẽ phân trang ở thực tế, nhưng dùng list cho MVP)
    public List<Order> getAllOrders() {
        // Có thể sort theo createdAt giảm dần (Mới nhất lên đầu)
        return orderRepository.findAll();
    }

    // Lọc đơn hàng theo trạng thái (Ví dụ: Chỉ xem các đơn đang CHỜ XÁC NHẬN)
    public List<Order> getOrdersByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status);
    }

    // Xem chi tiết một đơn hàng (Bao gồm cả danh sách hoa khách đã mix trong OrderItem)
    public Order getOrderDetails(String orderCode) {
        return orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy mã đơn hàng: " + orderCode));
    }

    // Cập nhật trạng thái đơn hàng (Tiệm chốt đơn -> Đang làm -> Hoàn tất)
    @Transactional
    public Order updateOrderStatus(String orderCode, OrderStatus newStatus) {
        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy mã đơn hàng: " + orderCode));

        // Logic bổ sung: Nếu đơn bị HỦY (CANCELLED), có thể hoàn lại số lượng (stock_quantity) vào kho
        // Phụ thuộc vào quy trình vận hành thực tế của Mộc Anh Flower Studio

        order.setStatus(newStatus);
        return orderRepository.save(order);
    }
}