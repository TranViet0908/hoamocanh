package com.hoamocanh.service.customer;

import com.hoamocanh.core.entity.*;
import com.hoamocanh.dto.customer.OrderCheckoutRes;
import com.hoamocanh.dto.customer.ShopCheckoutReq;
import com.hoamocanh.repository.OrderItemRepository;
import com.hoamocanh.repository.OrderRepository;
import com.hoamocanh.repository.OrderStatusHistoryRepository;
import com.hoamocanh.repository.UserRepository;
import com.hoamocanh.service.notify.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.mocanh.studio.Engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Đặt hàng từ giỏ "mẫu có sẵn". Giá mỗi mẫu do khách chọn (400.000đ đến 1.500.000đ, bước 100.000đ) và là giá cuối cùng.
 * Mọi thứ được kiểm tra lại ở đây: giá, số lượng, mẫu có thật, mức tối thiểu theo khoảng giá của mẫu, phí giao, voucher.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerShopOrderService {

    private static final Set<String> DELIVERY_TYPES = Set.of("DELIVERY", "STUDIO_PICKUP");
    private static final Set<String> PAYMENT_METHODS = Set.of("CASH", "ONLINE");
    private static final BigDecimal MIN = BigDecimal.valueOf(400_000), MAX = BigDecimal.valueOf(1_500_000),
            STEP = BigDecimal.valueOf(100_000), CAP = BigDecimal.valueOf(50_000_000);
    private static final Map<String, String> KINDS = Map.ofEntries(
            Map.entry("bouquet", "Bó hoa"), Map.entry("basket", "Giỏ hoa"), Map.entry("stand", "Kệ hoa"),
            Map.entry("gift-box", "Hộp & túi hoa"), Map.entry("cut-set", "Set mix thả bình"),
            Map.entry("vase", "Bình hoa thiết kế"), Map.entry("orchid-pot", "Hoa lan hồ điệp"),
            Map.entry("wedding", "Hoa cưới"), Map.entry("event", "Hoa sự kiện"),
            Map.entry("subscription", "Hoa định kỳ"), Map.entry("accessory", "Phụ kiện"));

    private final OrderRepository orderRepository;
    private final OrderItemRepository itemRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final CustomerVoucherService voucherService;
    private final OrderFeeService feeService;
    private final NotificationService notificationService;
    private final Engine engine;
    private final ShopCurationService curation;

    /** Khách đang đăng nhập (nếu có). Khách vãng lai vẫn đặt được. */
    public Optional<User> findCustomer(Authentication a) {
        if (a == null || !a.isAuthenticated() || a instanceof AnonymousAuthenticationToken
                || a.getAuthorities().stream().noneMatch(g -> g.getAuthority().equals("ROLE_CUSTOMER"))) return Optional.empty();
        return userRepository.findByPhone(a.getName()).filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()));
    }

    static String normPhone(String p) {
        String s = p == null ? "" : p.replaceAll("[\\s.-]", "");
        return s.startsWith("+84") ? "0" + s.substring(3) : s;
    }

    private static boolean blank(String s) { return s == null || s.trim().isEmpty(); }

    private Map<String, Object> sample(String id) {
        try {
            return engine.shop.product(id == null ? "" : id);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Có mẫu trong giỏ không còn trong thư viện của tiệm. Vui lòng xóa mẫu đó và chọn lại.");
        }
    }

    /** Mức thấp nhất của mẫu: theo khoảng giá của thư mục ảnh, làm tròn lên 100.000đ, nằm trong 400.000đ đến 1.500.000đ. */
    private static BigDecimal minFor(Map<String, Object> p) {
        if (p.get("price_band") instanceof Map<?, ?> b && b.get("min") instanceof Number n && n.longValue() > 0) {
            BigDecimal v = BigDecimal.valueOf(n.longValue()).divide(STEP, 0, RoundingMode.UP).multiply(STEP);
            return v.max(MIN).min(MAX);
        }
        return MIN;
    }

    /** Tên đã đặt riêng (không bắt đầu bằng "Mẫu MA-") thì dùng tên đó, ngược lại dùng kiểu dáng + mã mẫu. */
    private static String sampleName(Map<String, Object> p, String kind) {
        String n = p.get("name") == null ? "" : String.valueOf(p.get("name")).trim();
        String n2 = n.isEmpty() || n.startsWith("Mẫu MA-") ? KINDS.getOrDefault(kind, "Mẫu hoa") + " " + p.get("code") : n + " (" + p.get("code") + ")";
        return n2.length() > 160 ? n2.substring(0, 160) : n2;
    }

    @Transactional
    public OrderCheckoutRes checkout(ShopCheckoutReq req, Optional<User> customer) {
        if (req.getItems() == null || req.getItems().isEmpty() || req.getItems().size() > 20)
            throw new IllegalArgumentException("Giỏ hàng trống hoặc có quá nhiều dòng.");
        if (blank(req.getReceiverName()) || req.getReceiverName().trim().length() > 100)
            throw new IllegalArgumentException("Vui lòng nhập họ tên (tối đa 100 ký tự).");
        String phone = normPhone(req.getReceiverPhone());
        if (!phone.matches("^0\\d{9,10}$")) throw new IllegalArgumentException("Số điện thoại chưa đúng định dạng.");
        if (req.getDeliveryType() == null || !DELIVERY_TYPES.contains(req.getDeliveryType()))
            throw new IllegalArgumentException("Hình thức nhận hàng không hợp lệ.");
        if (req.getPaymentMethod() == null || !PAYMENT_METHODS.contains(req.getPaymentMethod()))
            throw new IllegalArgumentException("Vui lòng chọn hình thức thanh toán.");
        if ("DELIVERY".equals(req.getDeliveryType()) && blank(req.getShippingAddress()))
            throw new IllegalArgumentException("Vui lòng nhập địa chỉ nhận hàng.");
        if (req.getDeliveryTime() == null || req.getDeliveryTime().isBefore(LocalDateTime.now()))
            throw new IllegalArgumentException("Vui lòng chọn thời gian nhận hàng trong tương lai.");
        String cardMsg = req.getCardMessage() == null ? "" : req.getCardMessage().trim();
        String note = req.getNote() == null ? "" : req.getNote().trim();
        if (cardMsg.length() > 500 || note.length() > 500 || (req.getShippingAddress() != null && req.getShippingAddress().length() > 500))
            throw new IllegalArgumentException("Nội dung thiệp, ghi chú hoặc địa chỉ quá dài (tối đa 500 ký tự).");

        BigDecimal total = BigDecimal.ZERO;
        List<OrderItem> lines = new ArrayList<>();
        for (ShopCheckoutReq.Line l : req.getItems()) {
            if (l == null || l.getQuantity() == null || l.getQuantity() < 1 || l.getQuantity() > 20)
                throw new IllegalArgumentException("Số lượng mỗi mẫu từ 1 đến 20.");
            BigDecimal price = l.getPrice();
            if (price == null || price.compareTo(MIN) < 0 || price.compareTo(MAX) > 0
                    || price.remainder(STEP).signum() != 0)
                throw new IllegalArgumentException("Mức giá phải từ 400.000đ đến 1.500.000đ, mỗi bước 100.000đ.");
            Map<String, Object> p = sample(l.getSampleId());
            if (price.compareTo(minFor(p)) < 0)
                throw new IllegalArgumentException("Mẫu " + p.get("code") + " có mức giá tối thiểu " + minFor(p).toPlainString() + "đ.");
            String kind = String.valueOf(p.get("kind"));
            lines.add(OrderItem.builder().sampleId(String.valueOf(p.get("id")))
                    .sampleName(sampleName(p, kind))
                    .imageUrl(p.get("thumbnail_url") == null ? null : String.valueOf(p.get("thumbnail_url")))
                    .unitPrice(price).quantity(l.getQuantity()).build());
            total = total.add(price.multiply(BigDecimal.valueOf(l.getQuantity())));
        }
        if (total.compareTo(CAP) > 0) throw new IllegalArgumentException("Giá trị đơn vượt hạn mức, vui lòng chia thành nhiều đơn.");

        BigDecimal off = curation.flashDiscount(lines);   // Flash sale đang diễn ra (server tự tính)
        OrderFeeService.Fees fees = feeService.fees();
        StringBuilder sn = new StringBuilder();
        if (!cardMsg.isEmpty()) sn.append("Thiệp: ").append(cardMsg);
        if (!note.isEmpty()) sn.append(sn.length() > 0 ? "\n" : "").append("Ghi chú: ").append(note);

        Order order = Order.builder()
                .receiverName(req.getReceiverName().trim()).receiverPhone(phone)
                .deliveryType(req.getDeliveryType()).shippingAddress(blank(req.getShippingAddress()) ? null : req.getShippingAddress().trim())
                .deliveryTime(req.getDeliveryTime()).paymentMethod(req.getPaymentMethod())
                .style("Mẫu có sẵn").styleNote(sn.length() == 0 ? null : sn.toString())
                .totalPrice(total).status("PENDING").paymentStatus("UNPAID")
                .shippingFee("DELIVERY".equals(req.getDeliveryType()) ? fees.shipFee() : BigDecimal.ZERO)
                .build();
        customer.ifPresent(order::setUser);

        if (!blank(req.getVoucherCode())) {
            Voucher v = voucherService.validateAndGetVoucher(req.getVoucherCode().trim(), total);
            BigDecimal d;
            if ("PERCENT".equals(v.getDiscountType())) {
                d = total.multiply(v.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                if (v.getMaxDiscount() != null && d.compareTo(v.getMaxDiscount()) > 0) d = v.getMaxDiscount();
            } else d = v.getDiscountValue();
            off = off.add(d);
            order.setVoucher(v);
            voucherService.incrementVoucherUsage(v);
        }

        if (off.signum() > 0) order.setDiscountAmount(off.min(total));
        Order saved = orderRepository.save(order);
        for (OrderItem i : lines) i.setOrder(saved);
        itemRepository.saveAll(lines);
        historyRepository.save(OrderStatusHistory.builder().order(saved).status("PENDING")
                .note("Khách tạo đơn mẫu có sẵn (" + req.getDeliveryType() + ")").build());

        BigDecimal discount = saved.getDiscountAmount() == null ? BigDecimal.ZERO : saved.getDiscountAmount();
        BigDecimal payable = total.subtract(discount).add(saved.getShippingFee()).max(BigDecimal.ZERO);
        try { notificationService.orderCreated(saved, payable); }
        catch (Exception e) { log.warn("Không ghi được thông báo cho đơn MA-{}: {}", saved.getId(), e.getMessage()); }

        return OrderCheckoutRes.builder().orderId(saved.getId()).orderCode("MA-" + saved.getId())
                .orderStatus(saved.getStatus()).paymentStatus(saved.getPaymentStatus())
                .paymentMethod(saved.getPaymentMethod()).payableAmount(payable).shippingFee(saved.getShippingFee())
                .payNowAmount(payable).message("Đơn hàng đã được ghi nhận.").build();
    }
}