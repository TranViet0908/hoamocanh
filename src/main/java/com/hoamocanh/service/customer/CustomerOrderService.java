package com.hoamocanh.service.customer;

import com.hoamocanh.core.entity.*;
import com.hoamocanh.core.util.QRCodeGenerator;
import com.hoamocanh.dto.customer.CheckoutReq;
import com.hoamocanh.dto.customer.DesignItemReq;
import com.hoamocanh.dto.customer.OrderCheckoutRes;
import com.hoamocanh.repository.MaterialRepository;
import com.hoamocanh.repository.OrderRepository;
import com.hoamocanh.repository.ProductTypeRepository;
import com.hoamocanh.service.admin.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerOrderService {

    private static final Set<String> DELIVERY_TYPES = Set.of("ONLINE", "PICKUP", "DELIVERY");
    private static final Set<String> PAYMENT_METHODS = Set.of("CASH", "ONLINE");
    private static final String GIFT_UPLOAD_PREFIX = "/uploads/gift/";

    private final OrderRepository orderRepository;
    private final MaterialRepository materialRepository;
    private final ProductTypeRepository productTypeRepository;
    private final CustomerDesignService designService;
    private final CustomerVoucherService voucherService;
    private final QRCodeGenerator qrCodeGenerator;
    private final BookingService bookingService;

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static boolean tooLong(String s, int max) {
        return s != null && s.length() > max;
    }

    // MỚI: kiểm tra dữ liệu đầu vào theo từng hình thức đầu ra
    private void validateRequest(CheckoutReq req) {
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new IllegalArgumentException("Giỏ hoa đang trống.");
        }
        if (req.getProductTypeId() == null || req.getBudgetLevel() == null) {
            throw new IllegalArgumentException("Thiếu loại sản phẩm hoặc mức giá.");
        }
        if (blank(req.getReceiverName()) || blank(req.getReceiverPhone())) {
            throw new IllegalArgumentException("Vui lòng nhập họ tên và số điện thoại.");
        }
        // Giới hạn theo độ dài cột trong DB và định dạng số điện thoại (không chỉ tin vào kiểm tra phía trình duyệt)
        if (req.getReceiverName().trim().length() > 100) {
            throw new IllegalArgumentException("Họ tên tối đa 100 ký tự.");
        }
        if (!req.getReceiverPhone().trim().replaceAll("[\\s.-]", "").matches("^(0|\\+84)\\d{9,10}$")) {
            throw new IllegalArgumentException("Số điện thoại chưa đúng định dạng.");
        }
        if (tooLong(req.getStyle(), 50) || tooLong(req.getThemeColor(), 50)) {
            throw new IllegalArgumentException("Thông tin phong cách không hợp lệ.");
        }
        if (tooLong(req.getStyleNote(), 1000) || tooLong(req.getGiftMessage(), 1000)) {
            throw new IllegalArgumentException("Ghi chú hoặc lời chúc tối đa 1000 ký tự.");
        }
        if (tooLong(req.getVoucherCode(), 50)) {
            throw new IllegalArgumentException("Mã giảm giá không hợp lệ.");
        }
        if (req.getDeliveryType() == null || !DELIVERY_TYPES.contains(req.getDeliveryType())) {
            throw new IllegalArgumentException("Hình thức đầu ra không hợp lệ.");
        }
        if (req.getPaymentMethod() == null || !PAYMENT_METHODS.contains(req.getPaymentMethod())) {
            throw new IllegalArgumentException("Vui lòng chọn hình thức thanh toán.");
        }
        // Ảnh/video quà tặng chỉ được lấy từ thư mục upload của hệ thống (không cho link ngoài)
        for (String url : new String[]{req.getGiftImageUrl(), req.getGiftVideoUrl()}) {
            if (!blank(url) && (!url.startsWith(GIFT_UPLOAD_PREFIX) || url.contains(".."))) {
                throw new IllegalArgumentException("Ảnh/video đính kèm không hợp lệ.");
            }
        }
        if ("DELIVERY".equals(req.getDeliveryType()) && blank(req.getShippingAddress())) {
            throw new IllegalArgumentException("Vui lòng nhập địa chỉ nhận hàng.");
        }
        if (!"ONLINE".equals(req.getDeliveryType())) {
            if (req.getDeliveryTime() == null) {
                throw new IllegalArgumentException("Vui lòng chọn thời gian.");
            }
            if (req.getDeliveryTime().isBefore(LocalDateTime.now())) {
                throw new IllegalArgumentException("Thời gian đã chọn nằm trong quá khứ.");
            }
        }
    }

    @Transactional
    public OrderCheckoutRes processCheckout(CheckoutReq req) {
        validateRequest(req);
        // Khách tự làm tại tiệm: phải đúng khung giờ còn chỗ (khóa dòng cấu hình để không bị đặt trùng chỗ cuối)
        if ("PICKUP".equals(req.getDeliveryType())) bookingService.assertSlotAvailable(req.getDeliveryTime());

        // 1. Gộp dòng trùng materialId (toMap cũ sẽ ném lỗi nếu trùng)
        Map<Integer, Integer> itemMap = req.getItems().stream()
                .filter(i -> i.getQuantity() != null && i.getQuantity() > 0)
                .collect(Collectors.toMap(DesignItemReq::getMaterialId, DesignItemReq::getQuantity, Integer::sum));
        if (itemMap.isEmpty()) {
            throw new IllegalArgumentException("Giỏ hoa đang trống.");
        }

        // 2. Loại sản phẩm + mức giá tối thiểu
        ProductType productType = productTypeRepository.findById(req.getProductTypeId())
                .orElseThrow(() -> new IllegalArgumentException("Loại sản phẩm không tồn tại."));
        if (req.getBudgetLevel().compareTo(productType.getMinBudget()) < 0) {
            throw new IllegalArgumentException("Ngân sách tối thiểu cho " + productType.getName()
                    + " là " + productType.getMinBudget());
        }

        // 3. Chặn hack giao diện: định mức, kho, tổng tiền, nguyên liệu đúng loại sản phẩm
        designService.validateDesignCart(req.getProductTypeId(), req.getBudgetLevel(), itemMap);

        // 4. Khởi tạo Order
        Order order = Order.builder()
                .receiverName(req.getReceiverName().trim())
                .receiverPhone(req.getReceiverPhone().trim())
                .productType(productType)
                .totalPrice(req.getBudgetLevel())
                .deliveryType(req.getDeliveryType())
                .shippingAddress(req.getShippingAddress())
                .deliveryTime(req.getDeliveryTime())
                .style(req.getStyle())
                .themeColor(req.getThemeColor())
                .styleNote(req.getStyleNote())
                .giftMessage(req.getGiftMessage())
                .giftImageUrl(req.getGiftImageUrl())
                .giftVideoUrl(req.getGiftVideoUrl())
                .paymentMethod(req.getPaymentMethod())
                .status("PENDING")
                .paymentStatus("UNPAID")
                .build();

        // 5. Voucher
        if (!blank(req.getVoucherCode())) {
            Voucher validVoucher = voucherService.validateAndGetVoucher(req.getVoucherCode().trim(), req.getBudgetLevel());

            BigDecimal discountAmount;
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
            voucherService.incrementVoucherUsage(validVoucher);
        }

        // 6. Trừ tồn kho (@Version trong Material chặn hai khách mua cùng lúc)
        List<OrderDetail> orderDetails = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : itemMap.entrySet()) {
            Material material = materialRepository.findById(e.getKey())
                    .orElseThrow(() -> new IllegalArgumentException("Nguyên liệu không tồn tại."));

            // IllegalArgumentException để GlobalExceptionHandler trả 400 + message (RuntimeException thường sẽ thành 500)
            if (material.getStock() < e.getValue()) {
                throw new IllegalArgumentException("Rất tiếc, " + material.getName()
                        + " vừa hết hàng do có khách khác đặt. Vui lòng đổi hoa khác!");
            }

            material.setStock(material.getStock() - e.getValue());
            materialRepository.save(material);

            orderDetails.add(OrderDetail.builder()
                    .order(order)
                    .material(material)
                    .quantity(e.getValue())
                    .unitPrice(material.getPrice())
                    .build());
        }
        order.setOrderDetails(orderDetails);

        // 7. Lưu đơn, rồi tạo mã đơn + link quà tặng (cần id nên phải sau khi save)
        Order saved = orderRepository.save(order);
        String orderCode = "MA-" + saved.getId();

        String giftUrl = null;
        if ("ONLINE".equals(req.getDeliveryType())) {
            // Token ngẫu nhiên: người lạ không đoán được link quà của khách khác
            String token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            giftUrl = qrCodeGenerator.generateGiftQRCode(token);
            saved.setQrCodeUrl(giftUrl);   // entity đang được quản lý trong transaction nên tự lưu khi commit
        }

        BigDecimal discount = saved.getDiscountAmount() == null ? BigDecimal.ZERO : saved.getDiscountAmount();
        BigDecimal payable = saved.getTotalPrice().subtract(discount).max(BigDecimal.ZERO);

        return OrderCheckoutRes.builder()
                .orderId(saved.getId())
                .orderCode(orderCode)
                .orderStatus(saved.getStatus())
                .paymentStatus(saved.getPaymentStatus())
                .paymentMethod(saved.getPaymentMethod())
                .payableAmount(payable)
                .qrCodeUrl(giftUrl)
                .message("Đơn hàng đã được ghi nhận.")
                .build();
    }
}