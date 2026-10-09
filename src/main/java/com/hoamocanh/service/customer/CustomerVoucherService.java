package com.hoamocanh.service.customer;

import com.hoamocanh.core.entity.Voucher;
import com.hoamocanh.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CustomerVoucherService {

    private final VoucherRepository voucherRepository;

    /**
     * Thuật toán kiểm tra tính hợp lệ của Voucher trước khi áp dụng
     */
    public Voucher validateAndGetVoucher(String code, BigDecimal orderTotal) {
        Voucher voucher = voucherRepository.findByCodeAndIsActiveTrue(code)
                .orElseThrow(() -> new IllegalArgumentException("Mã giảm giá không tồn tại hoặc đã bị khóa."));

        LocalDateTime now = LocalDateTime.now();
        if (voucher.getStartDate() != null && now.isBefore(voucher.getStartDate())) {
            throw new IllegalArgumentException("Mã giảm giá chưa đến thời gian sử dụng.");
        }
        if (voucher.getEndDate() != null && now.isAfter(voucher.getEndDate())) {
            throw new IllegalArgumentException("Mã giảm giá đã hết hạn.");
        }
        if (voucher.getMinOrderValue() != null && orderTotal.compareTo(voucher.getMinOrderValue()) < 0) {
            throw new IllegalArgumentException("Đơn hàng chưa đạt giá trị tối thiểu " + voucher.getMinOrderValue() + "đ để dùng mã này.");
        }
        if (voucher.getUsageLimit() != null && voucher.getUsedCount() >= voucher.getUsageLimit()) {
            throw new IllegalArgumentException("Mã giảm giá đã hết lượt sử dụng.");
        }

        return voucher;
    }

    /**
     * Tăng số lượt sử dụng một cách an toàn (Sẽ gọi trong CustomerOrderService lúc chốt đơn)
     */
    @Transactional
    public void incrementVoucherUsage(Voucher voucher) {
        // Cần đảm bảo Entity Voucher đã có @Version để kích hoạt Optimistic Locking
        voucher.setUsedCount(voucher.getUsedCount() + 1);
        voucherRepository.save(voucher);
    }
}