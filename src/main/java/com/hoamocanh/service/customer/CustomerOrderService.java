package com.hoamocanh.service.customer;

import com.hoamocanh.core.entity.Material;
import com.hoamocanh.core.entity.Order;
import com.hoamocanh.core.entity.OrderItem;
import com.hoamocanh.core.exception.OutOfStockException;
import com.hoamocanh.core.util.QRCodeGenerator;
import com.hoamocanh.dto.customer.CheckoutReq;
import com.hoamocanh.dto.customer.DesignItemReq;
import com.hoamocanh.repository.MaterialRepository;
import com.hoamocanh.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerOrderService {

    private final OrderRepository orderRepository;
    private final MaterialRepository materialRepository;
    private final CustomerDesignService designService;
    // QRCodeGenerator là class ở core/util (Sẽ code implement ZXing sau)
    private final QRCodeGenerator qrCodeGenerator;

    @Transactional
    public Order processCheckout(CheckoutReq req) {
        // 1. Chuyển đổi list item thành Map để validate
        Map<Long, Integer> itemMap = req.getItems().stream()
                .collect(Collectors.toMap(DesignItemReq::getMaterialId, DesignItemReq::getQuantity));

        // 2. Chạy lại validate để chắc chắn khách không bypass (hack) qua giao diện
        designService.validateDesignCart(req.getProductType(), req.getBudgetLevel(), itemMap);

        // 3. Khởi tạo đơn hàng
        Order order = Order.builder()
                .orderCode("HMA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .customerName(req.getCustomerName())
                .customerPhone(req.getCustomerPhone())
                .productType(req.getProductType())
                .budgetLevel(req.getBudgetLevel())
                .outputType(req.getOutputType())
                // Lưu tổng tiền theo ngân sách khách chọn (Ví dụ: 400.000)
                .totalPrice(req.getBudgetLevel())
                .status("CONFIRMED") // Hoặc DRAFT tùy quy trình thanh toán
                .deliveryAddress(req.getDeliveryAddress())
                .appointmentTime(req.getAppointmentTime())
                .giftMessage(req.getGiftMessage())
                .build();

        // 4. Xử lý Trừ tồn kho & Sinh OrderItem
        List<OrderItem> orderItems = new ArrayList<>();
        for (DesignItemReq itemReq : req.getItems()) {
            Material material = materialRepository.findById(itemReq.getMaterialId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nguyên liệu"));

            if (material.getStockQuantity() < itemReq.getQuantity()) {
                throw new OutOfStockException("Nguyên liệu " + material.getName() + " không đủ số lượng. Vui lòng chọn lại!");
            }

            // Trừ tồn kho (Sẽ trigger cơ chế Optimistic Locking @Version ở đây)
            material.setStockQuantity(material.getStockQuantity() - itemReq.getQuantity());
            if (material.getStockQuantity() == 0) {
                material.setStatus("OUT_OF_STOCK");
            }
            materialRepository.save(material);

            // Lưu giá nguyên liệu ngay tại thời điểm chốt đơn để đối soát sau này
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .material(material)
                    .quantity(itemReq.getQuantity())
                    .priceAtTime(material.getPrice())
                    .build();
            orderItems.add(orderItem);
        }
        order.setItems(orderItems);

        // 5. Nếu là luồng ONLINE, sinh ngay QR Code để chia sẻ
        if ("ONLINE".equalsIgnoreCase(req.getOutputType())) {
            String qrUrl = qrCodeGenerator.generateGiftQRCode(order.getOrderCode());
            order.setQrCodeUrl(qrUrl);
        }

        return orderRepository.save(order);
    }
}