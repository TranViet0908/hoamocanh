package com.hoamocanh.controller.customer;

import com.hoamocanh.core.entity.Voucher;
import com.hoamocanh.service.customer.CustomerVoucherService;
import com.hoamocanh.service.customer.VoucherMath;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Khách nhập mã và xem trước số tiền giảm. Việc dùng mã (tăng lượt dùng) chỉ xảy ra khi chốt đơn. */
@RestController
@RequestMapping("/customer/vouchers")
@RequiredArgsConstructor
public class CustomerVoucherController {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("100000000");
    private final CustomerVoucherService voucherService;

    // Chống dò mã: tối đa 30 lần xem trước mỗi phút cho mỗi địa chỉ
    private final ConcurrentHashMap<String, long[]> hits = new ConcurrentHashMap<>();

    @GetMapping("/preview")
    public ResponseEntity<Map<String, Object>> preview(@RequestParam String code, @RequestParam BigDecimal amount, HttpServletRequest req) {
        throttle(req.getRemoteAddr());
        if (code == null || code.isBlank() || code.length() > 50) throw new IllegalArgumentException("Mã giảm giá không hợp lệ.");
        if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0) throw new IllegalArgumentException("Giá trị đơn không hợp lệ.");
        Voucher v = voucherService.validateAndGetVoucher(code.trim(), amount);
        BigDecimal d = VoucherMath.discount(v, amount);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", v.getCode());
        out.put("discount", d);
        out.put("type", v.getDiscountType());
        out.put("value", v.getDiscountValue());
        return ResponseEntity.ok(out);
    }

    private void throttle(String key) {
        long now = System.currentTimeMillis();
        long[] w = hits.compute(key == null ? "?" : key, (k, v) -> {
            if (v == null || now - v[0] > 60_000) return new long[]{now, 1};
            v[1]++;
            return v;
        });
        if (w[1] > 30) throw new IllegalArgumentException("Bạn thử mã quá nhanh. Vui lòng đợi một phút rồi thử lại.");
    }
}