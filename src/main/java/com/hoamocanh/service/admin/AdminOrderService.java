package com.hoamocanh.service.admin;

import com.hoamocanh.core.entity.*;
import com.hoamocanh.core.exception.ResourceNotFoundException;
import com.hoamocanh.dto.admin.*;
import com.hoamocanh.repository.*;
import com.hoamocanh.core.util.QRCodeGenerator;
import com.hoamocanh.service.notify.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private final NotificationService notificationService;
    private final QRCodeGenerator qrCodeGenerator;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

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
     *  DELIVERY / STUDIO_PICKUP (Mộc Anh làm giúp, giao hoặc khách đến nhận):
     *                                  ... > Chuẩn bị > Thực hiện > Chờ khách duyệt > Sẵn sàng giao/nhận > Hoàn thành
     * Đơn chưa xong thì lúc nào cũng được hủy.
     */
    /** Mộc Anh làm giúp: giao tận nơi hoặc để khách đến tiệm nhận. */
    static boolean studioMade(String deliveryType) {
        return "DELIVERY".equals(deliveryType) || "STUDIO_PICKUP".equals(deliveryType);
    }

    static List<String> nextStatuses(String deliveryType, String status) {
        List<String> flow = switch (deliveryType == null ? "" : deliveryType) {
            case "ONLINE" -> List.of("PENDING", "CONFIRMED", "COMPLETED");
            case "PICKUP" -> List.of("PENDING", "CONFIRMED", "PREPARING", "IN_PROGRESS", "COMPLETED");
            default -> List.of("PENDING", "CONFIRMED", "PREPARING", "IN_PROGRESS", "AWAITING_APPROVAL", "READY", "COMPLETED");
        };
        List<String> out = new ArrayList<>();
        int i = flow.indexOf(status);
        if (i >= 0 && i < flow.size() - 1) out.add(flow.get(i + 1));
        if (studioMade(deliveryType)) {
            if ("IN_PROGRESS".equals(status)) out.add("READY");              // bỏ qua bước khách duyệt
            if ("AWAITING_APPROVAL".equals(status)) out.add("IN_PROGRESS");  // khách muốn chỉnh lại
        }
        if (!"COMPLETED".equals(status) && !"CANCELLED".equals(status)) out.add("CANCELLED");
        return out;
    }

    private static BigDecimal payable(Order o) {
        BigDecimal d = o.getDiscountAmount() == null ? BigDecimal.ZERO : o.getDiscountAmount();
        BigDecimal ship = o.getShippingFee() == null ? BigDecimal.ZERO : o.getShippingFee();
        return o.getTotalPrice().subtract(d).add(ship).max(BigDecimal.ZERO);
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
        // Khóa dòng đơn trước: tránh 2 nhân viên cùng hủy một đơn làm hoàn kho 2 lần
        orderRepository.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
        Order o = find(id);
        String to = req.getNewStatus().trim();
        String cur = normalize(o.getStatus());
        String note = req.getNote() == null || req.getNote().isBlank() ? null : req.getNote().trim();

        if (!STATUSES.contains(to) || !nextStatuses(o.getDeliveryType(), cur).contains(to)) {
            throw new IllegalArgumentException("Không thể chuyển đơn này sang trạng thái đó.");
        }
        if ("AWAITING_APPROVAL".equals(to) && (o.getFinishedImageUrl() == null || o.getFinishedImageUrl().isBlank())) {
            throw new IllegalArgumentException("Vui lòng tải ảnh thành phẩm trước khi gửi khách duyệt.");
        }
        if ("CANCELLED".equals(to)) {
            if (note == null) throw new IllegalArgumentException("Vui lòng nhập lý do hủy đơn.");
            // Chưa chuẩn bị nguyên liệu thì trả hoa về kho (lúc khách đặt đã trừ kho)
            if ("PENDING".equals(cur) || "CONFIRMED".equals(cur)) {
                // Khóa từng nguyên liệu theo id tăng dần (cùng thứ tự với lúc khách đặt) để không deadlock
                List<OrderDetail> sorted = o.getOrderDetails().stream()
                        .sorted(Comparator.comparing(d -> d.getMaterial().getId())).toList();
                for (OrderDetail d : sorted) {
                    Material m = materialRepository.findByIdLocked(d.getMaterial().getId())
                            .orElseThrow(() -> new ResourceNotFoundException("Nguyên liệu không tồn tại."));
                    m.setStock(m.getStock() + d.getQuantity());
                    materialRepository.save(m);
                }
            }
        }
        o.setStatus(to);
        orderRepository.save(o);
        historyRepository.save(OrderStatusHistory.builder()
                .order(o).status(to).note(note).createdBy(staff(staffPhone)).build());
        notificationService.statusChanged(o, to);   // giả lập: chỉ ghi log
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

    /** Tiệm tải (hoặc gỡ, url = null) ảnh thành phẩm để khách duyệt. Chỉ đơn Mộc Anh làm giúp, đang thực hiện hoặc chờ duyệt. */
    @Transactional
    public OrderDetailRes setFinishedPhoto(Integer id, String url, String staffPhone) {
        orderRepository.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
        Order o = find(id);
        String st = normalize(o.getStatus());
        if (!studioMade(o.getDeliveryType())) throw new IllegalArgumentException("Chỉ đơn Mộc Anh làm giúp mới có ảnh thành phẩm.");
        if (!"IN_PROGRESS".equals(st) && !"AWAITING_APPROVAL".equals(st)) {
            throw new IllegalArgumentException("Chỉ đổi ảnh thành phẩm khi đơn đang thực hiện hoặc đang chờ khách duyệt.");
        }
        String clean = url == null || url.isBlank() ? null : url.trim();
        if (clean != null && (!clean.startsWith("/uploads/product/") || clean.contains(".."))) {
            throw new IllegalArgumentException("Ảnh thành phẩm không hợp lệ.");
        }
        o.setFinishedImageUrl(clean);
        orderRepository.save(o);
        historyRepository.save(OrderStatusHistory.builder().order(o).status(st)
                .note(clean == null ? "Đã gỡ ảnh thành phẩm" : "Đã cập nhật ảnh thành phẩm").createdBy(staff(staffPhone)).build());
        return toDetail(o);
    }

    // ---------- Quản lý quà tặng online ----------
    private static void requireGiftOrder(Order o) {
        if (!"ONLINE".equals(o.getDeliveryType())) throw new IllegalArgumentException("Chỉ đơn gửi tặng online mới có quà tặng.");
        if ("CANCELLED".equals(normalize(o.getStatus()))) throw new IllegalArgumentException("Đơn đã hủy.");
    }

    /** Gỡ một phần nội dung quà (message | image | video | bouquet) khi nội dung không phù hợp. File ảnh/video cũng bị xóa. */
    @Transactional
    public OrderDetailRes removeGiftContent(Integer id, String part, String staffPhone) {
        orderRepository.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
        Order o = find(id);
        requireGiftOrder(o);
        String oldFile = null, label;
        String p = part == null ? "" : part;
        if (p.equals("message")) { o.setGiftMessage(null); label = "lời chúc"; }
        else if (p.equals("image")) { oldFile = o.getGiftImageUrl(); o.setGiftImageUrl(null); label = "ảnh kèm theo"; }
        else if (p.equals("video")) { oldFile = o.getGiftVideoUrl(); o.setGiftVideoUrl(null); label = "video kèm theo"; }
        else if (p.equals("bouquet")) { oldFile = o.getBouquetImageUrl(); o.setBouquetImageUrl(null); label = "hình mô phỏng"; }
        else throw new IllegalArgumentException("Phần nội dung không hợp lệ.");
        orderRepository.save(o);
        historyRepository.save(OrderStatusHistory.builder().order(o).status(normalize(o.getStatus()))
                .note("Đã gỡ " + label + " của quà tặng").createdBy(staff(staffPhone)).build());
        deleteGiftFile(oldFile);
        return toDetail(o);
    }

    /** Tạo lại link và mã QR quà tặng. Link cũ không còn mở được nữa. */
    @Transactional
    public OrderDetailRes regenerateGiftLink(Integer id, String staffPhone) {
        orderRepository.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
        Order o = find(id);
        requireGiftOrder(o);
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        o.setQrCodeUrl(qrCodeGenerator.generateGiftQRCode(token));
        orderRepository.save(o);
        historyRepository.save(OrderStatusHistory.builder().order(o).status(normalize(o.getStatus()))
                .note("Đã tạo lại link và mã QR quà tặng (link cũ không còn dùng được)").createdBy(staff(staffPhone)).build());
        return toDetail(o);
    }

    /** Xóa file trong /uploads/gift/ khỏi ổ đĩa; lỗi xóa file không làm hỏng thao tác chính. */
    private void deleteGiftFile(String url) {
        if (url == null || !url.startsWith("/uploads/gift/") || url.contains("..")) return;
        try {
            Path dir = Paths.get(uploadDir, "gift").toAbsolutePath().normalize();
            Path file = dir.resolve(url.substring("/uploads/gift/".length())).normalize();
            if (file.startsWith(dir)) Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // file còn sót trên ổ đĩa, không ảnh hưởng nội dung đã gỡ khỏi đơn
        }
    }

    /** Đọc các dòng "url<TAB>tên mẫu" đã lưu ở đơn. */
    private static List<OrderDetailRes.Sample> parseSamples(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<OrderDetailRes.Sample> out = new ArrayList<>();
        for (String line : raw.split("\n")) {
            if (line.isBlank()) continue;
            int t = line.indexOf('\t');
            out.add(t < 0 ? new OrderDetailRes.Sample(line.trim(), "")
                    : new OrderDetailRes.Sample(line.substring(0, t).trim(), line.substring(t + 1).trim()));
        }
        return out;
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
                nextStatuses(o.getDeliveryType(), status),
                o.getBuyerName(), o.getBuyerPhone(), o.getShippingFee() == null ? BigDecimal.ZERO : o.getShippingFee(),
                o.getDepositRequired(), o.getFinishedImageUrl(), o.getBouquetImageUrl(), o.getBookingCode(),
                parseSamples(o.getSampleImages()));
    }
}