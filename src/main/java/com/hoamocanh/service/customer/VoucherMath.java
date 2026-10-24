package com.hoamocanh.service.customer;

import com.hoamocanh.core.entity.Voucher;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Cách tính số tiền giảm của một voucher, dùng chung cho xem trước và chốt đơn để hai nơi luôn khớp. */
public final class VoucherMath {
    private VoucherMath() { }

    /** FIXED: giảm số tiền cố định. PERCENT: giảm theo %, không vượt maxDiscount. Không bao giờ vượt quá giá trị đơn. */
    public static BigDecimal discount(Voucher v, BigDecimal amount) {
        BigDecimal d;
        if ("PERCENT".equals(v.getDiscountType())) {
            d = amount.multiply(v.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (v.getMaxDiscount() != null && d.compareTo(v.getMaxDiscount()) > 0) d = v.getMaxDiscount();
        } else {
            d = v.getDiscountValue();
        }
        return d.min(amount).max(BigDecimal.ZERO);
    }
}