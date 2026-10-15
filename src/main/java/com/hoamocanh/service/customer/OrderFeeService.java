package com.hoamocanh.service.customer;

import com.hoamocanh.core.entity.SystemConfig;
import com.hoamocanh.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Các khoản phí và quy tắc giữ chỗ, đọc từ system_configs (dùng các khóa đã có sẵn trong DB):
 *  WORKSHOP_DEPOSIT_RATIO tỷ lệ cọc đơn PICKUP, dạng 0.50 = 50% (thiếu thì 0.50)
 *  DEFAULT_SHIPPING_FEE   phí giao hàng đơn DELIVERY, đồng (thiếu thì 0 = miễn phí)
 *  booking.hold-minutes   số phút giữ chỗ khi chưa nhận được cọc (thiếu thì 60)
 */
@Service
@RequiredArgsConstructor
public class OrderFeeService {

    public static final String K_DEPOSIT_RATIO = "WORKSHOP_DEPOSIT_RATIO";
    public static final String K_SHIP_FEE = "DEFAULT_SHIPPING_FEE";
    public static final String K_HOLD_MINUTES = "booking.hold-minutes";

    public record Fees(int depositPercent, BigDecimal shipFee, int holdMinutes) {}

    private final SystemConfigRepository configRepository;

    @Transactional(readOnly = true)
    public Fees fees() {
        int pct = Math.min(100, Math.max(1, toMoney(get(K_DEPOSIT_RATIO), new BigDecimal("0.5"))
                .multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue()));
        BigDecimal ship = toMoney(get(K_SHIP_FEE), BigDecimal.ZERO);
        return new Fees(pct, ship, holdMinutes());
    }

    @Transactional(readOnly = true)
    public int holdMinutes() {
        return Math.min(1440, Math.max(1, toInt(get(K_HOLD_MINUTES), 60)));
    }

    /** Tiền cọc = payable × percent%, làm tròn lên 1.000đ và không vượt quá số phải thu. */
    public BigDecimal depositFor(BigDecimal payable, int percent) {
        BigDecimal raw = payable.multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100), 0, RoundingMode.UP);
        BigDecimal rounded = raw.divide(BigDecimal.valueOf(1000), 0, RoundingMode.UP).multiply(BigDecimal.valueOf(1000));
        return rounded.min(payable);
    }

    private String get(String key) {
        return configRepository.findById(key).map(SystemConfig::getConfigValue).orElse(null);
    }

    private static int toInt(String s, int def) {
        try { return s == null ? def : Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return def; }
    }

    private static BigDecimal toMoney(String s, BigDecimal def) {
        try {
            if (s == null) return def;
            BigDecimal v = new BigDecimal(s.trim());
            return v.signum() < 0 ? def : v;
        } catch (NumberFormatException e) { return def; }
    }
}