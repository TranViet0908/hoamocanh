package com.hoamocanh.service.admin;

import com.hoamocanh.core.entity.Voucher;
import com.hoamocanh.core.exception.ResourceNotFoundException;
import com.hoamocanh.repository.VoucherAdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/** Quản lý mã giảm giá: tạo, sửa, bật/tắt. Không xóa vì đơn cũ đã tham chiếu voucher; muốn ngưng thì tắt. */
@Service
@RequiredArgsConstructor
public class AdminVoucherService {

    private static final Pattern CODE = Pattern.compile("^[A-Z0-9_-]{3,30}$");
    private static final BigDecimal MAX_FIXED = new BigDecimal("5000000"), MAX_MONEY = new BigDecimal("100000000");

    public record Req(String code, String discountType, BigDecimal discountValue, BigDecimal maxDiscount, BigDecimal minOrderValue,
                      Integer usageLimit, LocalDateTime startDate, LocalDateTime endDate, Boolean active) { }

    public record Res(Integer id, String code, String discountType, BigDecimal discountValue, BigDecimal maxDiscount,
                      BigDecimal minOrderValue, Integer usageLimit, Integer usedCount, LocalDateTime startDate,
                      LocalDateTime endDate, boolean active, String state) { }

    private final VoucherAdminRepository repository;

    @Transactional(readOnly = true)
    public List<Res> list() {
        return repository.findAllByOrderByIdDesc().stream().map(AdminVoucherService::toRes).toList();
    }

    @Transactional
    public Res create(Req r) {
        String code = r.code() == null ? "" : r.code().trim().toUpperCase();
        if (!CODE.matcher(code).matches()) throw new IllegalArgumentException("Mã gồm 3 đến 30 ký tự: chữ không dấu, số, gạch ngang hoặc gạch dưới.");
        if (repository.existsByCodeIgnoreCase(code)) throw new IllegalArgumentException("Mã này đã tồn tại.");
        check(r);
        Voucher v = Voucher.builder().code(code).usedCount(0).build();   // không đặt version: Hibernate tự quản lý
        apply(v, r);
        return toRes(repository.save(v));
    }

    @Transactional
    public Res update(Integer id, Req r) {
        Voucher v = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mã giảm giá."));
        check(r);
        int used = v.getUsedCount() == null ? 0 : v.getUsedCount();
        if (r.usageLimit() != null && r.usageLimit() < used)
            throw new IllegalArgumentException("Giới hạn lượt dùng không được nhỏ hơn số lượt đã dùng (" + used + ").");
        apply(v, r);   // mã (code) không đổi sau khi tạo
        return toRes(repository.save(v));
    }

    @Transactional
    public Res setActive(Integer id, boolean active) {
        Voucher v = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mã giảm giá."));
        v.setIsActive(active);
        return toRes(repository.save(v));
    }

    private static void check(Req r) {
        if (!"FIXED".equals(r.discountType()) && !"PERCENT".equals(r.discountType()))
            throw new IllegalArgumentException("Loại giảm giá phải là giảm tiền hoặc giảm phần trăm.");
        BigDecimal val = r.discountValue();
        if (val == null || val.signum() <= 0) throw new IllegalArgumentException("Mức giảm phải lớn hơn 0.");
        if ("PERCENT".equals(r.discountType()) && val.compareTo(BigDecimal.valueOf(100)) > 0)
            throw new IllegalArgumentException("Giảm theo phần trăm tối đa 100%.");
        if ("FIXED".equals(r.discountType()) && val.compareTo(MAX_FIXED) > 0)
            throw new IllegalArgumentException("Giảm tiền cố định tối đa 5.000.000đ.");
        if (r.maxDiscount() != null && (r.maxDiscount().signum() <= 0 || r.maxDiscount().compareTo(MAX_MONEY) > 0))
            throw new IllegalArgumentException("Mức giảm tối đa chưa hợp lệ.");
        if (r.minOrderValue() != null && (r.minOrderValue().signum() < 0 || r.minOrderValue().compareTo(MAX_MONEY) > 0))
            throw new IllegalArgumentException("Giá trị đơn tối thiểu chưa hợp lệ.");
        if (r.usageLimit() != null && (r.usageLimit() < 1 || r.usageLimit() > 1_000_000))
            throw new IllegalArgumentException("Giới hạn lượt dùng từ 1 đến 1.000.000 (để trống nếu không giới hạn).");
        if (r.startDate() != null && r.endDate() != null && !r.endDate().isAfter(r.startDate()))
            throw new IllegalArgumentException("Ngày kết thúc phải sau ngày bắt đầu.");
    }

    private static void apply(Voucher v, Req r) {
        v.setDiscountType(r.discountType());
        v.setDiscountValue(r.discountValue());
        v.setMaxDiscount("PERCENT".equals(r.discountType()) ? r.maxDiscount() : null);
        v.setMinOrderValue(r.minOrderValue() == null ? BigDecimal.ZERO : r.minOrderValue());
        v.setUsageLimit(r.usageLimit());
        v.setStartDate(r.startDate());
        v.setEndDate(r.endDate());
        v.setIsActive(r.active() == null || r.active());
    }

    private static Res toRes(Voucher v) {
        LocalDateTime now = LocalDateTime.now();
        int used = v.getUsedCount() == null ? 0 : v.getUsedCount();
        boolean active = Boolean.TRUE.equals(v.getIsActive());
        String state = !active ? "INACTIVE"
                : v.getEndDate() != null && now.isAfter(v.getEndDate()) ? "EXPIRED"
                : v.getStartDate() != null && now.isBefore(v.getStartDate()) ? "NOT_STARTED"
                : v.getUsageLimit() != null && used >= v.getUsageLimit() ? "EXHAUSTED" : "ACTIVE";
        return new Res(v.getId(), v.getCode(), v.getDiscountType(), v.getDiscountValue(), v.getMaxDiscount(), v.getMinOrderValue(),
                v.getUsageLimit(), used, v.getStartDate(), v.getEndDate(), active, state);
    }
}