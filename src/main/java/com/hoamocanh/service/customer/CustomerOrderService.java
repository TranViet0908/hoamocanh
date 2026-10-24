package com.hoamocanh.service.customer;

import com.hoamocanh.core.entity.*;
import com.hoamocanh.core.exception.InvalidBudgetException;
import com.hoamocanh.core.exception.OutOfStockException;
import com.hoamocanh.core.exception.ResourceNotFoundException;
import com.hoamocanh.core.util.QRCodeGenerator;
import com.hoamocanh.dto.customer.CheckoutReq;
import com.hoamocanh.dto.customer.DesignItemReq;
import com.hoamocanh.dto.customer.OrderCheckoutRes;
import com.hoamocanh.dto.customer.OrderLookupRes;
import com.hoamocanh.dto.customer.OrderReviewReq;
import com.hoamocanh.repository.MaterialRepository;
import com.hoamocanh.repository.OrderRepository;
import com.hoamocanh.repository.OrderStatusHistoryRepository;
import com.hoamocanh.repository.ProductTypeRepository;
import com.hoamocanh.service.admin.BookingService;
import com.hoamocanh.service.notify.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.security.SecureRandom;
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

    // ONLINE: gửi tặng online | PICKUP: khách tự làm tại tiệm (có đặt lịch, chiếm slot)
    // DELIVERY: Mộc Anh làm giúp rồi giao | STUDIO_PICKUP: Mộc Anh làm giúp, khách đến tiệm nhận (không chiếm slot)
    private static final Set<String> DELIVERY_TYPES = Set.of("ONLINE", "PICKUP", "DELIVERY", "STUDIO_PICKUP");
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
    private final OrderFeeService feeService;
    private final NotificationService notificationService;
    private final com.hoamocanh.repository.OrderItemRepository orderItemRepository;

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static boolean tooLong(String s, int max) {
        return s != null && s.length() > max;
    }

    private static boolean phoneOk(String p) {
        return p != null && p.trim().replaceAll("[\\s.-]", "").matches("^(0|\\+84)\\d{9,10}$");
    }

    /** Số phải thu = giá gói − giảm giá + phí giao hàng. */
    private static BigDecimal payableOf(Order o) {
        BigDecimal d = o.getDiscountAmount() == null ? BigDecimal.ZERO : o.getDiscountAmount();
        BigDecimal ship = o.getShippingFee() == null ? BigDecimal.ZERO : o.getShippingFee();
        return o.getTotalPrice().subtract(d).add(ship).max(BigDecimal.ZERO);
    }

    /** Lưu danh sách mẫu thành các dòng "url<TAB>chú thích" (chú thích tối đa 150 ký tự). */
    private static String encodeSamples(List<CheckoutReq.SampleImage> list) {
        if (list == null || list.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (CheckoutReq.SampleImage s : list) {
            String cap = s.getCaption() == null ? "" : s.getCaption().replaceAll("[\\t\\r\\n]+", " ").trim();
            if (cap.length() > 150) cap = cap.substring(0, 150);
            if (sb.length() > 0) sb.append('\n');
            sb.append(s.getUrl().trim()).append('\t').append(cap);
        }
        return sb.toString();
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
        if (!phoneOk(req.getReceiverPhone())) {
            throw new IllegalArgumentException("Số điện thoại chưa đúng định dạng.");
        }
        // Người đặt hộ: nếu nhập thì phải đủ cả tên và số điện thoại
        if (!blank(req.getBuyerName()) || !blank(req.getBuyerPhone())) {
            if (blank(req.getBuyerName()) || blank(req.getBuyerPhone())) {
                throw new IllegalArgumentException("Vui lòng nhập đủ họ tên và số điện thoại người đặt.");
            }
            if (req.getBuyerName().trim().length() > 100 || !phoneOk(req.getBuyerPhone())) {
                throw new IllegalArgumentException("Thông tin người đặt chưa hợp lệ.");
            }
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
        for (String url : new String[]{req.getGiftImageUrl(), req.getGiftVideoUrl(), req.getBouquetImageUrl()}) {
            if (!blank(url) && (!url.startsWith(GIFT_UPLOAD_PREFIX) || url.contains(".."))) {
                throw new IllegalArgumentException("Ảnh/video đính kèm không hợp lệ.");
            }
        }
        if (req.getSampleImages() != null) {
            if (req.getSampleImages().size() > 4) throw new IllegalArgumentException("Quá nhiều ảnh mẫu đính kèm.");
            for (CheckoutReq.SampleImage s : req.getSampleImages()) {
                if (s == null || blank(s.getUrl()) || !s.getUrl().startsWith(GIFT_UPLOAD_PREFIX) || s.getUrl().contains("..")) {
                    throw new IllegalArgumentException("Ảnh mẫu đính kèm không hợp lệ.");
                }
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

        // Mã đặt lịch riêng cho đơn tự làm tại tiệm
        String bookingCode = "PICKUP".equals(req.getDeliveryType()) ? newBookingCode() : null;

        // 4. Khởi tạo Order
        Order order = Order.builder()
                .receiverName(req.getReceiverName().trim())
                .receiverPhone(req.getReceiverPhone().trim())
                .buyerName(blank(req.getBuyerName()) ? null : req.getBuyerName().trim())
                .buyerPhone(blank(req.getBuyerPhone()) ? null : req.getBuyerPhone().trim())
                .productType(productType)
                .totalPrice(req.getBudgetLevel())
                .deliveryType(req.getDeliveryType())
                .shippingAddress(req.getShippingAddress())
                .deliveryTime(req.getDeliveryTime())
                .bookingCode(bookingCode)
                .sampleImages(encodeSamples(req.getSampleImages()))
                .style(req.getStyle())
                .themeColor(req.getThemeColor())
                .styleNote(req.getStyleNote())
                .giftMessage(req.getGiftMessage())
                .giftImageUrl(req.getGiftImageUrl())
                .giftVideoUrl(req.getGiftVideoUrl())
                .bouquetImageUrl(blank(req.getBouquetImageUrl()) ? null : req.getBouquetImageUrl())   // mọi loại đơn: trang quà (ONLINE) và nhân viên xem thiết kế
                .paymentMethod(req.getPaymentMethod())
                .status("PENDING")
                .paymentStatus("UNPAID")
                .build();

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

        // 5b. Phí giao hàng (chỉ đơn DELIVERY) và tiền cọc giữ chỗ (chỉ đơn PICKUP); mức phí lấy từ system_configs
        OrderFeeService.Fees fees = feeService.fees();
        order.setShippingFee("DELIVERY".equals(req.getDeliveryType()) ? fees.shipFee() : BigDecimal.ZERO);
        if ("PICKUP".equals(req.getDeliveryType())) {
            order.setDepositRequired(feeService.depositFor(payableOf(order), fees.depositPercent()));
        }

        // 6. Trừ tồn kho: khóa ghi từng dòng nguyên liệu (theo id tăng dần nhờ TreeMap) nên 2 khách chốt cùng lúc
        //    sẽ xếp hàng, người sau thấy kho đã giảm và nhận thông báo hết hàng thay vì lỗi hệ thống
        List<OrderDetail> orderDetails = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : itemMap.entrySet()) {
            Material material = materialRepository.findByIdLocked(e.getKey())
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

        BigDecimal payable = payableOf(saved);
        BigDecimal deposit = saved.getDepositRequired();
        LocalDateTime holdUntil = deposit == null ? null
                : (saved.getCreatedAt() != null ? saved.getCreatedAt() : LocalDateTime.now()).plusMinutes(fees.holdMinutes());

        notificationService.orderCreated(saved, payable);   // giả lập: chỉ ghi log

        return OrderCheckoutRes.builder()
                .orderId(saved.getId())
                .orderCode(orderCode)
                .orderStatus(saved.getStatus())
                .paymentStatus(saved.getPaymentStatus())
                .paymentMethod(saved.getPaymentMethod())
                .payableAmount(payable)
                .shippingFee(saved.getShippingFee())
                .depositAmount(deposit)
                .payNowAmount(deposit != null ? deposit : payable)
                .holdUntil(holdUntil)
                .bookingCode(saved.getBookingCode())
                .qrCodeUrl(giftUrl)
                .message("Đơn hàng đã được ghi nhận.")
                .build();
    }

    /**
     * Khách tra lại đơn (kể cả link và QR quà tặng) bằng mã đơn + số điện thoại.
     * Số điện thoại phải khớp người nhận hoặc người đặt. Sai thông tin luôn trả cùng một thông báo.
     */
    @Transactional(readOnly = true)
    public OrderLookupRes lookup(String orderCode, String phone) {
        return toLookup(findOwned(orderCode, phone, false));
    }

    /**
     * Khách duyệt ảnh thành phẩm: đồng ý thì đơn sang READY, yêu cầu chỉnh lại thì quay về IN_PROGRESS.
     * Khóa dòng đơn trước khi đọc trạng thái để bấm hai lần không làm hai lần.
     */
    @Transactional
    public OrderLookupRes review(OrderReviewReq req) {
        Order o = findOwned(req.orderCode(), req.phone(), true);
        if (!"AWAITING_APPROVAL".equals(o.getStatus())) {
            throw new IllegalArgumentException("Đơn này hiện không chờ bạn duyệt ảnh.");
        }
        String note = req.note() == null ? "" : req.note().trim();
        if (note.length() > 200) throw new IllegalArgumentException("Nội dung tối đa 200 ký tự.");
        if (!req.approve() && note.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng cho tiệm biết bạn muốn chỉnh sửa điều gì.");
        }
        String to = req.approve() ? "READY" : "IN_PROGRESS";
        o.setStatus(to);
        orderRepository.save(o);
        orderStatusHistoryRepository.save(OrderStatusHistory.builder().order(o).status(to)
                .note(req.approve() ? "Khách đã duyệt ảnh thành phẩm" + (note.isEmpty() ? "" : ": " + note)
                        : "Khách yêu cầu chỉnh lại: " + note)
                .build());
        return toLookup(o);
    }

    /** Tìm đơn theo mã + số điện thoại (người nhận hoặc người đặt). Sai thông tin luôn trả cùng một thông báo. */
    private Order findOwned(String orderCode, String phone, boolean lock) {
        String notFound = "Không tìm thấy đơn hàng. Vui lòng kiểm tra lại mã đơn và số điện thoại.";
        String raw = orderCode == null ? "" : orderCode.trim().toUpperCase(Locale.ROOT).replaceAll("\\s", "");
        if (blank(phone)) throw new ResourceNotFoundException(notFound);
        int id;
        if (raw.startsWith("DL")) {   // mã đặt lịch, dạng DL-XXXXXX
            String bc = raw.startsWith("DL-") ? raw : "DL-" + raw.substring(2);
            id = orderRepository.findByBookingCode(bc).map(Order::getId).orElseThrow(() -> new ResourceNotFoundException(notFound));
        } else {                      // mã đơn, dạng MA-123
            String digits = raw.replaceFirst("^MA-?", "");
            if (!digits.matches("\\d{1,9}")) throw new ResourceNotFoundException(notFound);
            id = Integer.parseInt(digits);
        }
        Order o = (lock ? orderRepository.lockById(id) : orderRepository.findById(id))
                .orElseThrow(() -> new ResourceNotFoundException(notFound));
        String p = normPhone(phone);
        if (!p.equals(normPhone(o.getReceiverPhone())) && !p.equals(normPhone(o.getBuyerPhone()))) {
            throw new ResourceNotFoundException(notFound);
        }
        return o;
    }

    private OrderLookupRes toLookup(Order o) {
        // Ảnh thành phẩm chỉ hiện từ lúc gửi khách duyệt trở đi
        boolean showPhoto = Set.of("AWAITING_APPROVAL", "READY", "COMPLETED").contains(o.getStatus());
        return new OrderLookupRes("MA-" + o.getId(), o.getStatus(), o.getDeliveryType(),
                o.getPaymentStatus() == null ? "UNPAID" : o.getPaymentStatus(), o.getPaymentMethod(),
                payableOf(o), o.getShippingFee(), o.getDepositRequired(), o.getDeliveryTime(),
                "ONLINE".equals(o.getDeliveryType()) ? o.getQrCodeUrl() : null,
                showPhoto ? o.getFinishedImageUrl() : null, o.getBookingCode(),
                orderItemRepository.findByOrderIdOrderByIdAsc(o.getId()).stream()
                        .map(i -> new OrderLookupRes.Item(i.getSampleName(), i.getQuantity(), i.getUnitPrice(), i.getImageUrl())).toList());
    }

    private static final String BOOKING_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";   // bỏ ký tự dễ nhầm: I, O, 0, 1
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Sinh mã đặt lịch DL-XXXXXX chưa trùng đơn nào. */
    private String newBookingCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder("DL-");
            for (int i = 0; i < 6; i++) sb.append(BOOKING_CHARS.charAt(RANDOM.nextInt(BOOKING_CHARS.length())));
            if (!orderRepository.existsByBookingCode(sb.toString())) return sb.toString();
        }
        throw new IllegalStateException("Không tạo được mã đặt lịch, vui lòng thử lại.");
    }

    private static String normPhone(String p) {
        if (p == null) return "";
        String s = p.replaceAll("[\\s.-]", "");
        return s.startsWith("+84") ? "0" + s.substring(3) : s;
    }
}