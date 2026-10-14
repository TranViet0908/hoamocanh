package com.hoamocanh.service.customer;

import com.hoamocanh.core.entity.*;
import com.hoamocanh.core.exception.InvalidBudgetException;
import com.hoamocanh.core.exception.OutOfStockException;
import com.hoamocanh.core.exception.ResourceNotFoundException;
import com.hoamocanh.core.util.QRCodeGenerator;
import com.hoamocanh.dto.customer.CheckoutReq;
import com.hoamocanh.dto.customer.DesignItemReq;
import com.hoamocanh.dto.customer.OrderCheckoutRes;
import com.hoamocanh.repository.MaterialRepository;
import com.hoamocanh.repository.OrderRepository;
import com.hoamocanh.repository.OrderStatusHistoryRepository;
import com.hoamocanh.repository.ProductTypeRepository;
import com.hoamocanh.service.admin.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerOrderService {

    private static final Set<String> DELIVERY_TYPES = Set.of("ONLINE", "PICKUP", "DELIVERY");
    private static final Set<String> PAYMENT_METHODS = Set.of("CASH", "ONLINE");
    private static final String GIFT_UPLOAD_PREFIX = "/uploads/gift/";

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final MaterialRepository materialRepository;
    private final ProductTypeRepository productTypeRepository;
    private final CustomerDesignService designService;
    private final CustomerVoucherService voucherService;
    private final QRCodeGenerator qrCodeGenerator;
    private final BookingService bookingService;
    private final PasswordEncoder passwordEncoder;

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static boolean tooLong(String s, int max) {
        return s != null && s.length() > max;
    }

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
        // Mã mở quà tự đặt: 4-20 chữ hoặc số (BCrypt chỉ xét 72 byte đầu nên cần giới hạn độ dài)
        if (!blank(req.getGiftPassword()) && !req.getGiftPassword().trim().matches("^[\\p{L}\\p{N}]{4,20}$")) {
            throw new IllegalArgumentException("Mã mở quà gồm 4-20 chữ hoặc số, không có dấu cách.");
        }
        if (tooLong(req.getGiftPasswordHint(), 100)) {
            throw new IllegalArgumentException("Gợi ý mã mở quà tối đa 100 ký tự.");
        }
        if (req.getDeliveryType() == null || !DELIVERY_TYPES.contains(req.getDeliveryType())) {
            throw new IllegalArgumentException("Hình thức đầu ra không hợp lệ.");
        }
        if (req.getPaymentMethod() == null || !PAYMENT_METHODS.contains(req.getPaymentMethod())) {
            throw new IllegalArgumentException("Vui lòng chọn hình thức thanh toán.");
        }
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

        // Khách tự làm tại tiệm: kiểm tra khung giờ còn chỗ (khóa cấu hình booking.lock chống trùng chỗ cuối)
        if ("PICKUP".equals(req.getDeliveryType())) {
            bookingService.assertSlotAvailable(req.getDeliveryTime());
        }

        // 1. Gộp dòng trùng materialId và dùng TreeMap sắp xếp ID tăng dần để chống Deadlock khi trừ kho đồng thời
        Map<Integer, Integer> itemMap = req.getItems().stream()
                .filter(i -> i.getMaterialId() != null && i.getQuantity() != null && i.getQuantity() > 0)
                .collect(Collectors.toMap(
                        DesignItemReq::getMaterialId,
                        DesignItemReq::getQuantity,
                        Integer::sum,
                        TreeMap::new
                ));
        if (itemMap.isEmpty()) {
            throw new IllegalArgumentException("Giỏ hoa đang trống.");
        }

        // 2. Loại sản phẩm + mức giá tối thiểu
        ProductType productType = productTypeRepository.findById(req.getProductTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Loại sản phẩm không tồn tại."));
        if (req.getBudgetLevel().compareTo(productType.getMinBudget()) < 0) {
            NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));
            throw new InvalidBudgetException("Ngân sách tối thiểu cho " + productType.getName()
                    + " là " + nf.format(productType.getMinBudget()) + "đ.");
        }

        // 3. Chặn bypass giao diện: kiểm tra định mức, kho, tổng tiền, danh mục hợp lệ
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

        String plainGiftPassword = null;
        if ("ONLINE".equals(req.getDeliveryType()) && Boolean.TRUE.equals(req.getIsGiftPrivate())) {
            if (blank(req.getGiftPassword())) {
                plainGiftPassword = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            } else {
                plainGiftPassword = req.getGiftPassword().trim();
            }
            order.setGiftPasswordHash(passwordEncoder.encode(plainGiftPassword));
            order.setGiftPasswordHint(req.getGiftPasswordHint());
        }

        // 5. Áp dụng Voucher (có @Version chống dùng lố số lượt)
        if (!blank(req.getVoucherCode())) {
            Voucher validVoucher = voucherService.validateAndGetVoucher(req.getVoucherCode().trim(), req.getBudgetLevel());

            BigDecimal discountAmount;
            if ("PERCENT".equals(validVoucher.getDiscountType())) {
                discountAmount = req.getBudgetLevel()
                        .multiply(validVoucher.getDiscountValue())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
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

        // 6. Trừ tồn kho (@Version trong Material kích hoạt Optimistic Lock nếu 2 khách chốt cùng mili-giây)
        List<OrderDetail> orderDetails = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : itemMap.entrySet()) {
            Material material = materialRepository.findById(e.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException("Nguyên liệu không tồn tại."));

            if (Boolean.TRUE.equals(material.getIsDeleted()) || material.getStock() < e.getValue()) {
                throw new OutOfStockException("Rất tiếc, " + material.getName()
                        + " vừa hết hàng do có khách khác đặt. Vui lòng đổi hoa khác!");
            }

            material.setStock(material.getStock() - e.getValue());
            materialRepository.saveAndFlush(material);

            orderDetails.add(OrderDetail.builder()
                    .order(order)
                    .material(material)
                    .quantity(e.getValue())
                    .unitPrice(material.getPrice())
                    .build());
        }
        order.setOrderDetails(orderDetails);

        // 7. Lưu đơn + Tạo QR quà tặng + Lưu lịch sử trạng thái ban đầu
        Order saved = orderRepository.save(order);
        String orderCode = "MA-" + saved.getId();

        String giftUrl = null;
        if ("ONLINE".equals(req.getDeliveryType())) {
            String token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            giftUrl = qrCodeGenerator.generateGiftQRCode(token);
            saved.setQrCodeUrl(giftUrl);
        }

        orderStatusHistoryRepository.save(OrderStatusHistory.builder()
                .order(saved)
                .status("PENDING")
                .note("Khách tạo đơn (" + req.getDeliveryType() + ")")
                .build());

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
                .giftPassword(plainGiftPassword)
                .message("Đơn hàng đã được ghi nhận.")
                .build();
    }
}