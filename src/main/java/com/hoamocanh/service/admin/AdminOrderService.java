package com.hoamocanh.service.admin;

import com.hoamocanh.core.entity.*;
import com.hoamocanh.core.exception.ResourceNotFoundException;
import com.hoamocanh.dto.admin.*;
import com.hoamocanh.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private static final Set<String> STATUSES = Set.of("PENDING", "CONFIRMED", "PREPARING", "IN_PROGRESS",
            "AWAITING_APPROVAL", "READY", "COMPLETED", "CANCELLED");
    private static final Set<String> PAY_METHODS = Set.of("CASH", "BANK_TRANSFER", "MOMO");
    private static final Set<String> PAY_TYPES = Set.of("DEPOSIT", "FULL_PAYMENT");

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final MaterialRepository materialRepository;
    private final UserRepository userRepository;

    // ---------- Trạng thái ----------
    /** Dữ liệu cũ: PROCESSING -> IN_PROGRESS, DELIVERED -> COMPLETED. */
    static String normalize(String s) {
        if (s == null) return "PENDING";
        return switch (s) { case "PROCESSING" -> "IN_PROGRESS"; case "DELIVERED" -> "COMPLETED"; default -> s; };
    }

    /**
     * Quy trình theo hình thức:
     *  ONLINE   (gửi tặng online)    : Chờ xác nhận > Đã xác nhận > Hoàn thành
     *  PICKUP   (tự làm tại tiệm)    : ... > Chuẩn bị nguyên liệu > Đang thực hiện > Hoàn thành
     *  DELIVERY (Mộc Anh làm giúp)   : ... > Chuẩn bị > Thực hiện > Chờ khách duyệt > Sẵn sàng giao/nhận > Hoàn thành
     * Đơn chưa xong thì lúc nào cũng được hủy.
     */
    static List<String> nextStatuses(String deliveryType, String status) {
        List<String> flow = switch (deliveryType == null ? "" : deliveryType) {
            case "ONLINE" -> List.of("PENDING", "CONFIRMED", "COMPLETED");
            case "PICKUP" -> List.of("PENDING", "CONFIRMED", "PREPARING", "IN_PROGRESS", "COMPLETED");
            default -> List.of("PENDING", "CONFIRMED", "PREPARING", "IN_PROGRESS", "AWAITING_APPROVAL", "READY", "COMPLETED");
        };
        List<String> out = new ArrayList<>();
        int i = flow.indexOf(status);
        if (i >= 0 && i < flow.size() - 1) out.add(flow.get(i + 1));
        if ("DELIVERY".equals(deliveryType)) {
            if ("IN_PROGRESS".equals(status)) out.add("READY");              // bỏ qua bước khách duyệt
            if ("AWAITING_APPROVAL".equals(status)) out.add("IN_PROGRESS");  // khách muốn chỉnh lại
        }
        if (!"COMPLETED".equals(status) && !"CANCELLED".equals(status)) out.add("CANCELLED");
        return out;
    }

    private static BigDecimal payable(Order o) {
        BigDecimal d = o.getDiscountAmount() == null ? BigDecimal.ZERO : o.getDiscountAmount();
        return o.getTotalPrice().subtract(d).max(BigDecimal.ZERO);
    }

    // ---------- Đọc ----------
    @Transactional(readOnly = true)
    public List<OrderListRes> getAllOrders() {
        return orderRepository.findRecent(PageRequest.of(0, 300)).stream().map(o -> new OrderListRes(
                o.getId(), "MA-" + o.getId(), o.getReceiverName(), o.getReceiverPhone(),
                o.getProductType() == null ? null : o.getProductType().getName(),
                o.getDeliveryType(), normalize(o.getStatus()),
                o.getPaymentStatus() == null ? "UNPAID" : o.getPaymentStatus(),
                payable(o), o.getDeliveryTime(), o.getCreatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public OrderDetailRes getDetail(Integer id) {
        return toDetail(find(id));
    }

    // ---------- Ghi ----------
    @Transactional
    public OrderDetailRes updateStatus(Integer id, OrderStatusUpdateReq req, String staffPhone) {
        Order o = find(id);
        String to = req.getNewStatus().trim();
        String cur = normalize(o.getStatus());
        String note = req.getNote() == null || req.getNote().isBlank() ? null : req.getNote().trim();

        if (!STATUSES.contains(to) || !nextStatuses(o.getDeliveryType(), cur).contains(to)) {
            throw new IllegalArgumentException("Không thể chuyển đơn này sang trạng thái đó.");
        }
        if ("CANCELLED".equals(to)) {
            if (note == null) throw new IllegalArgumentException("Vui lòng nhập lý do hủy đơn.");
            // Chưa chuẩn bị nguyên liệu thì trả hoa về kho (lúc khách đặt đã trừ kho)
            if ("PENDING".equals(cur) || "CONFIRMED".equals(cur)) {
                for (OrderDetail d : o.getOrderDetails()) {
                    Material m = d.getMaterial();
                    m.setStock(m.getStock() + d.getQuantity());
                    materialRepository.save(m);
                }
            }
        }
        o.setStatus(to);
        orderRepository.save(o);
        historyRepository.save(OrderStatusHistory.builder()
                .order(o).status(to).note(note).createdBy(staff(staffPhone)).build());
        return toDetail(o);
    }

    @Transactional
    public OrderDetailRes addPayment(Integer id, PaymentReq req, String staffPhone) {
        Order o = find(id);
        if ("CANCELLED".equals(normalize(o.getStatus()))) throw new IllegalArgumentException("Đơn đã hủy, không thể ghi nhận thanh toán.");
        if (!PAY_METHODS.contains(req.getMethod()) || !PAY_TYPES.contains(req.getType())) {
            throw new IllegalArgumentException("Hình thức hoặc loại khoản thu không hợp lệ.");
        }
        BigDecimal remaining = payable(o).subtract(paidOf(paymentRepository.findByOrderIdOrderByIdAsc(id)));
        if (req.getAmount().compareTo(remaining) > 0) {
            throw new IllegalArgumentException("Số tiền vượt quá số còn phải thu (" + remaining.toBigInteger() + "đ).");
        }
        paymentRepository.save(Payment.builder().order(o).amount(req.getAmount())
                .paymentMethod(req.getMethod()).paymentType(req.getType()).paymentStatus("SUCCESS")
                .transactionCode(req.getTransactionCode() == null || req.getTransactionCode().isBlank() ? null : req.getTransactionCode().trim())
                .build());

        BigDecimal paid = paidOf(paymentRepository.findByOrderIdOrderByIdAsc(id));
        o.setDepositAmount(paid);
        o.setPaymentStatus(paid.compareTo(payable(o)) >= 0 ? "PAID" : "DEPOSITED");
        orderRepository.save(o);
        historyRepository.save(OrderStatusHistory.builder().order(o).status(normalize(o.getStatus()))
                .note("Đã nhận " + req.getAmount().toBigInteger() + "đ (" + req.getMethod() + ")")
                .createdBy(staff(staffPhone)).build());
        return toDetail(o);
    }

    // ---------- Tiện ích ----------
    private Order find(Integer id) {
        return orderRepository.findDetailById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
    }

    private User staff(String phone) {
        return phone == null ? null : userRepository.findByPhone(phone).orElse(null);
    }

    private static BigDecimal paidOf(List<Payment> ps) {
        return ps.stream().filter(p -> "SUCCESS".equals(p.getPaymentStatus()))
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private OrderDetailRes toDetail(Order o) {
        List<Payment> pays = paymentRepository.findByOrderIdOrderByIdAsc(o.getId());
        String status = normalize(o.getStatus());
        return new OrderDetailRes(
                o.getId(), "MA-" + o.getId(), status,
                o.getPaymentStatus() == null ? "UNPAID" : o.getPaymentStatus(), o.getPaymentMethod(), o.getDeliveryType(),
                o.getReceiverName(), o.getReceiverPhone(), o.getShippingAddress(), o.getDeliveryTime(), o.getCreatedAt(),
                o.getProductType() == null ? null : o.getProductType().getName(), o.getStyle(), o.getThemeColor(), o.getStyleNote(),
                o.getTotalPrice(), o.getDiscountAmount() == null ? BigDecimal.ZERO : o.getDiscountAmount(), payable(o), paidOf(pays),
                o.getGiftMessage(), o.getGiftImageUrl(), o.getGiftVideoUrl(), o.getQrCodeUrl(),
                o.getOrderDetails().stream().map(d -> new OrderDetailRes.Item(
                        d.getMaterial().getName(), d.getQuantity(), d.getUnitPrice(), d.getMaterial().getImageUrl())).toList(),
                pays.stream().map(p -> new OrderDetailRes.Pay(p.getId(), p.getAmount(), p.getPaymentMethod(),
                        p.getPaymentType(), p.getPaymentStatus(), p.getTransactionCode(), p.getCreatedAt())).toList(),
                historyRepository.findByOrderIdOrderByIdAsc(o.getId()).stream().map(h -> new OrderDetailRes.Hist(
                        h.getStatus(), h.getNote(), h.getCreatedBy() == null ? null : h.getCreatedBy().getFullName(),
                        h.getCreatedAt())).toList(),
                nextStatuses(o.getDeliveryType(), status));
    }
}
