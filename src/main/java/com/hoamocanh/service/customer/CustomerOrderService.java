package com.hoamocanh.service.customer;

import com.hoamocanh.core.entity.Material;
import com.hoamocanh.core.entity.Order;
import com.hoamocanh.core.entity.OrderItem;
import com.hoamocanh.core.entity.enums.OrderStatus;
import com.hoamocanh.dto.customer.CartItemReq;
import com.hoamocanh.dto.customer.OrderSubmitReq;
import com.hoamocanh.repository.MaterialRepository;
import com.hoamocanh.repository.OrderRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerOrderService {

    private final OrderRepository orderRepository;
    private final MaterialRepository materialRepository;
    private final CustomerDesignService customerDesignService;

    @Transactional // Đảm bảo nếu có lỗi ở khúc nào thì rollback (hủy) toàn bộ, không lưu rác vào DB
    public Order submitOrder(OrderSubmitReq req) {

        // 1. Validate lại giỏ hàng (Check số lượng, check giá, check tồn kho)
        customerDesignService.validateDesignCart(req.getProductType(), req.getBudgetLevel(), req.getItems());

        // 2. Khởi tạo thực thể Order
        Order newOrder = Order.builder()
                .orderCode("MA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()) // Sinh mã đơn ngẫu nhiên MA-XXXX
                .customerName(req.getCustomerName())
                .customerPhone(req.getCustomerPhone())
                .productType(req.getProductType())
                .budgetLevel(req.getBudgetLevel())
                .outputType(req.getOutputType())
                .styleNote(req.getStyleNote())
                .recipientName(req.getRecipientName())
                .deliveryAddress(req.getDeliveryAddress())
                .appointmentTime(req.getAppointmentTime())
                .giftMessage(req.getGiftMessage())
                .totalPrice(req.getBudgetLevel()) // Khách thanh toán đúng số tiền BudgetLevel đã chọn
                .status(OrderStatus.PENDING_CONFIRMATION) // Chuyển trạng thái chờ xác nhận
                .build();

        // 3. Xử lý các chi tiết đơn hàng (Order Items)
        for (CartItemReq itemReq : req.getItems()) {
            Material material = materialRepository.findById(itemReq.getMaterialId())
                    .orElseThrow(() -> new RuntimeException("Nguyên liệu không tồn tại"));

            // Trừ số lượng tồn kho (Tùy chọn: Có thể chờ Admin xác nhận mới trừ, nhưng thường trừ tạm để giữ chỗ)
            if (material.getStockQuantity() < itemReq.getQuantity()) {
                throw new RuntimeException("Nguyên liệu " + material.getName() + " không đủ số lượng.");
            }
            material.setStockQuantity(material.getStockQuantity() - itemReq.getQuantity());
            materialRepository.save(material); // Cập nhật lại tồn kho

            // Tạo OrderItem
            OrderItem orderItem = OrderItem.builder()
                    .material(material)
                    .quantity(itemReq.getQuantity())
                    .priceAtTime(material.getPrice()) // Lưu lại giá tại thời điểm chốt, tránh DB đổi giá sau này
                    .build();

            newOrder.addOrderItem(orderItem); // Dùng hàm tiện ích bên Entity đã viết
        }

        // 4. Lưu vào Database (JPA sẽ tự lưu luôn cả OrderItems nhờ CascadeType.ALL)
        Order savedOrder = orderRepository.save(newOrder);

        // 5. Nếu khách chọn GỬI TẶNG ONLINE, tạo QR code/Link (Giai đoạn này return string tạm thời)
        if (req.getOutputType().name().equals("ONLINE_GIFT")) {
            String link = "https://hoamocanh.vn/gift/" + savedOrder.getOrderCode();
            savedOrder.setQrCodeUrl(link);
            orderRepository.save(savedOrder);
        }

        return savedOrder;
    }
}