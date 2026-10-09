package com.hoamocanh.service.customer;

import com.hoamocanh.core.entity.*;
import com.hoamocanh.dto.customer.CheckoutReq;
import com.hoamocanh.dto.customer.DesignItemReq;
import com.hoamocanh.repository.MaterialRepository;
import com.hoamocanh.repository.OrderRepository;
import com.hoamocanh.repository.ProductTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerOrderService {

    private final OrderRepository orderRepository;
    private final MaterialRepository materialRepository;
    private final ProductTypeRepository productTypeRepository;
    private final CustomerDesignService designService;
    private final CustomerVoucherService voucherService; // Tích hợp service xử lý khuyến mãi

    @Transactional
    public Order processCheckout(CheckoutReq req) {
        // 1. Chuyển list items thành Map để validate[cite: 18]
        Map<Integer, Integer> itemMap = req.getItems().stream()
                .collect(Collectors.toMap(DesignItemReq::getMaterialId, DesignItemReq::getQuantity));

        // 2. Chặn hack giao diện: Kiểm tra lại toàn bộ định mức ngân sách (400k, 500k...)[cite: 18, 19]
        designService.validateDesignCart(req.getBudgetLevel(), itemMap);

        // 3. Lấy thông tin Form dáng sản phẩm (Mix thả bình, bó, giỏ...)[cite: 19, 23]
        ProductType productType = productTypeRepository.findById(req.getProductTypeId())
                .orElseThrow(() -> new IllegalArgumentException("Loại sản phẩm không tồn tại."));

        // 4. Khởi tạo Order[cite: 18, 23]
        Order order = Order.builder()
                .receiverName(req.getReceiverName())
                .receiverPhone(req.getReceiverPhone())
                .productType(productType)
                .totalPrice(req.getBudgetLevel())
                .deliveryType(req.getDeliveryType()) // ONLINE, PICKUP, DELIVERY[cite: 18, 19]
                .shippingAddress(req.getShippingAddress())
                .deliveryTime(req.getDeliveryTime())
                .style(req.getStyle())
                .themeColor(req.getThemeColor())
                .styleNote(req.getStyleNote())
                .giftMessage(req.getGiftMessage())
                .giftImageUrl(req.getGiftImageUrl())
                .status("PENDING")
                .paymentStatus("UNPAID")
                .build();

        // 5. Xử lý Voucher (Mã giảm giá) nếu khách có nhập
        if (req.getVoucherCode() != null && !req.getVoucherCode().isEmpty()) {
            Voucher validVoucher = voucherService.validateAndGetVoucher(req.getVoucherCode(), req.getBudgetLevel());

            BigDecimal discountAmount = BigDecimal.ZERO;
            if ("PERCENT".equals(validVoucher.getDiscountType())) {
                discountAmount = req.getBudgetLevel().multiply(validVoucher.getDiscountValue()).divide(BigDecimal.valueOf(100));
                if (validVoucher.getMaxDiscount() != null && discountAmount.compareTo(validVoucher.getMaxDiscount()) > 0) {
                    discountAmount = validVoucher.getMaxDiscount();
                }
            } else {
                discountAmount = validVoucher.getDiscountValue();
            }

            order.setDiscountAmount(discountAmount);
            order.setVoucher(validVoucher);

            // Tăng lượt sử dụng voucher (kích hoạt Optimistic Locking chống lố mã)
            voucherService.incrementVoucherUsage(validVoucher);
        }

        // 6. Xử lý trừ tồn kho nguyên liệu an toàn tuyệt đối[cite: 18]
        List<OrderDetail> orderDetails = new ArrayList<>();
        for (DesignItemReq itemReq : req.getItems()) {
            Material material = materialRepository.findById(itemReq.getMaterialId())
                    .orElseThrow(() -> new IllegalArgumentException("Nguyên liệu không tồn tại."));

            if (material.getStock() < itemReq.getQuantity()) {
                throw new RuntimeException("Rất tiếc, " + material.getName() + " vừa hết hàng do có khách khác đặt. Vui lòng đổi hoa khác!");
            }

            // Trừ tồn kho (Cơ chế @Version trong Material sẽ tự động block nếu có người thứ 2 tranh mua lúc này)[cite: 18, 23]
            material.setStock(material.getStock() - itemReq.getQuantity());
            materialRepository.save(material);

            OrderDetail detail = OrderDetail.builder()
                    .order(order)
                    .material(material)
                    .quantity(itemReq.getQuantity())
                    .unitPrice(material.getPrice())
                    .build();
            orderDetails.add(detail);
        }

        order.setOrderDetails(orderDetails);

        // 7. Sinh QR Code chia sẻ nếu khách chọn luồng GỬI TẶNG ONLINE[cite: 18, 19]
        if ("ONLINE".equals(req.getDeliveryType())) {
            order.setQrCodeUrl("https://hoamocanh.com/qr/" + System.currentTimeMillis());
        }

        return orderRepository.save(order);
    }
}