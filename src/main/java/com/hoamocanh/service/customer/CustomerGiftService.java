package com.hoamocanh.service.customer;

import com.hoamocanh.core.entity.Order;
import com.hoamocanh.core.exception.GiftUnlockException;
import com.hoamocanh.dto.customer.GiftRes;
import com.hoamocanh.repository.GiftOrderRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CustomerGiftService {

    private static final Pattern TOKEN = Pattern.compile("^[a-f0-9]{16}$");
    private final GiftOrderRepository giftOrderRepository;
    private final PasswordEncoder passwordEncoder;
    private static final int MAX_ATTEMPTS = 5;

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public GiftRes getGift(String code, HttpSession session) {
        if (code == null || !TOKEN.matcher(code).matches()) {
            throw new IllegalArgumentException("Đường link quà tặng không hợp lệ.");
        }

        Order o = giftOrderRepository.findByQrCodeUrlEndingWith("code=" + code)
                .filter(x -> "ONLINE".equals(x.getDeliveryType()) && !"CANCELLED".equals(x.getStatus()))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy món quà này hoặc quà đã bị hủy."));

        boolean isPrivate = o.getGiftPasswordHash() != null;
        boolean isUnlocked = Boolean.TRUE.equals(session.getAttribute("unlocked_gift_" + o.getId()));

        if (!isPrivate || isUnlocked) {
            return buildGiftRes(o, false);
        }

        // Bị khóa -> Che dấu nội dung, trả về rỗng
        // Chỉ lộ tên người gửi + gợi ý; không lộ mã đơn nội bộ (MA-id) hay thông tin thiết kế
        return new GiftRes(true, o.getGiftPasswordHint(), null, o.getReceiverName(),
                null, null, null, null, null, null, Collections.emptyList());
    }

    // noRollbackFor: số lần nhập sai và thời điểm khóa PHẢI được lưu dù có ném lỗi báo cho người dùng
    // (nếu để mặc định, RuntimeException làm hoàn tác transaction nên giới hạn 5 lần không bao giờ có tác dụng)
    @Transactional(noRollbackFor = GiftUnlockException.class)
    public void unlockGift(String code, String password, HttpSession session) {
        if (code == null || !TOKEN.matcher(code).matches()) {
            throw new IllegalArgumentException("Đường link quà tặng không hợp lệ.");
        }
        if (password == null || password.isBlank()) {
            throw new GiftUnlockException("Vui lòng nhập mã mở quà.");
        }
        Order order = giftOrderRepository.findByQrCodeUrlEndingWith("code=" + code)
                .filter(x -> "ONLINE".equals(x.getDeliveryType()) && !"CANCELLED".equals(x.getStatus()))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy món quà này hoặc quà đã bị hủy."));

        // Khóa dòng và đọc lại: nhiều yêu cầu đoán mã cùng lúc phải xếp hàng, không né được giới hạn 5 lần
        em.refresh(order, LockModeType.PESSIMISTIC_WRITE);

        if (order.getGiftPasswordHash() == null) return;   // quà công khai

        LocalDateTime now = LocalDateTime.now();
        if (order.getGiftLockUntil() != null) {
            if (now.isBefore(order.getGiftLockUntil())) {
                throw new GiftUnlockException("Nhập sai quá nhiều. Món quà đang bị khóa tạm thời, vui lòng thử lại sau ít phút.");
            }
            order.setGiftLockUntil(null);          // hết thời gian khóa -> đếm lại từ đầu
            order.setGiftFailedAttempts(0);
        }

        if (passwordEncoder.matches(password.trim(), order.getGiftPasswordHash())) {
            order.setGiftFailedAttempts(0);
            order.setGiftLockUntil(null);
            session.setAttribute("unlocked_gift_" + order.getId(), true);
            return;   // entity đang được quản lý trong transaction nên tự lưu khi commit
        }

        int attempts = (order.getGiftFailedAttempts() == null ? 0 : order.getGiftFailedAttempts()) + 1;
        order.setGiftFailedAttempts(attempts);
        if (attempts >= MAX_ATTEMPTS) {
            order.setGiftLockUntil(now.plusMinutes(15));
            throw new GiftUnlockException("Nhập sai " + MAX_ATTEMPTS + " lần. Món quà bị khóa 15 phút.");
        }
        throw new GiftUnlockException("Mã mở quà không chính xác. (Sai " + attempts + "/" + MAX_ATTEMPTS + " lần)");
    }

    private GiftRes buildGiftRes(Order o, boolean isLocked) {
        List<GiftRes.Item> items = o.getOrderDetails().stream()
                .map(d -> new GiftRes.Item(d.getMaterial().getName(), d.getQuantity(), d.getMaterial().getImageUrl()))
                .toList();

        return new GiftRes(
                isLocked, o.getGiftPasswordHint(), "MA-" + o.getId(),
                o.getReceiverName(), o.getGiftMessage(), o.getGiftImageUrl(), o.getGiftVideoUrl(),
                o.getProductType() != null ? o.getProductType().getName() : null,
                o.getStyle(), o.getThemeColor(), items
        );
    }
}