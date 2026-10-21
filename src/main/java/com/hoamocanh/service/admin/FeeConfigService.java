package com.hoamocanh.service.admin;

import com.hoamocanh.core.entity.SystemConfig;
import com.hoamocanh.dto.admin.FeeConfigRes;
import com.hoamocanh.repository.SystemConfigRepository;
import com.hoamocanh.service.customer.OrderFeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Trang Mức giá > tab "Phí & cọc": đọc/ghi các khóa cấu hình đã có trong system_configs. */
@Service
@RequiredArgsConstructor
public class FeeConfigService {

    private static final String K_LABOR = "LABOR_AND_MARGIN_RATIO";

    private final SystemConfigRepository repository;

    @Transactional(readOnly = true)
    public FeeConfigRes get() {
        return new FeeConfigRes(
                dec(K_LABOR, "0.20"), dec(OrderFeeService.K_DEPOSIT_RATIO, "0.50"),
                dec(OrderFeeService.K_SHIP_FEE, "0"), dec(OrderFeeService.K_HOLD_MINUTES, "60").intValue());
    }

    @Transactional
    public FeeConfigRes save(FeeConfigRes r) {
        if (r == null || r.laborRatio() == null || r.depositRatio() == null || r.shipFee() == null || r.holdMinutes() == null) {
            throw new IllegalArgumentException("Vui lòng nhập đủ các ô.");
        }
        BigDecimal labor = r.laborRatio().setScale(2, RoundingMode.HALF_UP);
        BigDecimal deposit = r.depositRatio().setScale(2, RoundingMode.HALF_UP);
        BigDecimal ship = r.shipFee().setScale(0, RoundingMode.HALF_UP);
        if (labor.compareTo(BigDecimal.ZERO) < 0 || labor.compareTo(new BigDecimal("0.90")) > 0) {
            throw new IllegalArgumentException("Tỷ lệ nhân sự và lợi nhuận phải từ 0% đến 90%.");
        }
        if (deposit.compareTo(new BigDecimal("0.05")) < 0 || deposit.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Tỷ lệ cọc phải từ 5% đến 100%.");
        }
        if (ship.signum() < 0 || ship.compareTo(BigDecimal.valueOf(1_000_000)) > 0) {
            throw new IllegalArgumentException("Phí giao hàng phải từ 0đ đến 1.000.000đ.");
        }
        if (r.holdMinutes() < 5 || r.holdMinutes() > 1440) {
            throw new IllegalArgumentException("Thời gian giữ chỗ phải từ 5 đến 1440 phút.");
        }
        put(K_LABOR, labor.toPlainString(), "Tỷ lệ dự phòng chi phí nhân sự, hướng dẫn tại tiệm và biên lợi nhuận tối thiểu");
        put(OrderFeeService.K_DEPOSIT_RATIO, deposit.toPlainString(), "Tỷ lệ đặt cọc mặc định khi khách đặt lịch tự làm tại tiệm");
        put(OrderFeeService.K_SHIP_FEE, ship.toPlainString(), "Phí giao hàng mặc định");
        put(OrderFeeService.K_HOLD_MINUTES, String.valueOf(r.holdMinutes()), "Số phút giữ chỗ cho đơn tự làm tại tiệm khi chưa nhận được cọc");
        return get();
    }

    private BigDecimal dec(String key, String def) {
        try {
            return new BigDecimal(repository.findById(key).map(SystemConfig::getConfigValue).orElse(def).trim());
        } catch (NumberFormatException e) {
            return new BigDecimal(def);
        }
    }

    private void put(String key, String value, String desc) {
        SystemConfig c = repository.findById(key)
                .orElseGet(() -> SystemConfig.builder().configKey(key).description(desc).build());
        c.setConfigValue(value);
        repository.save(c);
    }
}