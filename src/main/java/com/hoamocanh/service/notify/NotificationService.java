package com.hoamocanh.service.notify;

import com.hoamocanh.core.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * *** GIẢ LẬP *** Thông báo xác nhận gửi khách.
 * Hiện chỉ GHI LOG, chưa gửi gì thật (chưa có Zalo ZNS / SMS / email). Xem GHI_CHU_THAY_DU_LIEU_AO.md.
 * Khi có dịch vụ thật, chỉ cần sửa hàm send() (hoặc thay cả lớp này); chỗ gọi trong
 * CustomerOrderService và AdminOrderService không phải đổi.
 */
@Slf4j
@Service
public class NotificationService {

    private static String money(BigDecimal v) {
        return NumberFormat.getInstance(new Locale("vi", "VN")).format(v == null ? BigDecimal.ZERO : v) + "đ";
    }

    /** Khách vừa đặt đơn. */
    public void orderCreated(Order o, BigDecimal payable) {
        String msg = "Mộc Anh đã nhận đơn MA-" + o.getId() + ". Số tiền cần thanh toán: " + money(payable) + ".";
        if (o.getBookingCode() != null) msg += " Mã đặt lịch: " + o.getBookingCode() + ".";
        if (o.getDepositRequired() != null) {
            msg += " Vui lòng đặt cọc " + money(o.getDepositRequired()) + " để giữ chỗ.";
        }
        sendToCustomer(o, msg);
    }

    /** Nhân viên đổi trạng thái đơn. */
    public void statusChanged(Order o, String status) {
        String code = "MA-" + o.getId();
        String msg = switch (status) {
            case "CONFIRMED" -> "Đơn " + code + " đã được Mộc Anh xác nhận.";
            case "PREPARING", "IN_PROGRESS" -> "Mộc Anh đang thực hiện đơn " + code + ".";
            case "AWAITING_APPROVAL" -> "Ảnh thành phẩm đơn " + code + " đã sẵn sàng. Mời bạn vào trang Tra cứu đơn hàng để duyệt.";
            case "READY" -> "Đơn " + code + " đã sẵn sàng để giao hoặc nhận.";
            case "COMPLETED" -> "Đơn " + code + " đã hoàn thành. Cảm ơn bạn đã chọn Mộc Anh!";
            case "CANCELLED" -> "Đơn " + code + " đã được hủy. Mộc Anh sẽ liên hệ nếu cần hoàn tiền.";
            default -> null;
        };
        if (msg != null) sendToCustomer(o, msg);
    }

    private void sendToCustomer(Order o, String message) {
        send(o.getReceiverPhone(), message);
        if (o.getBuyerPhone() != null && !o.getBuyerPhone().equals(o.getReceiverPhone())) {
            send(o.getBuyerPhone(), message);
        }
    }

    /** GIẢ LẬP: chỉ ghi log. Thay thân hàm này bằng lời gọi Zalo/SMS/email thật. */
    protected void send(String phone, String message) {
        log.info("[GIẢ LẬP THÔNG BÁO] gửi tới {}: {}", phone, message);
    }
}