package com.hoamocanh.repository;

import com.hoamocanh.core.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Truy vấn voucher cho trang quản trị (dùng chung bảng vouchers với CustomerVoucherService). */
@Repository
public interface VoucherAdminRepository extends JpaRepository<Voucher, Integer> {
    List<Voucher> findAllByOrderByIdDesc();
    Optional<Voucher> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
}