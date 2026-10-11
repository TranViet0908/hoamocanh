package com.hoamocanh.repository;

import com.hoamocanh.core.entity.SystemConfig;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, String> {

    // Khóa một dòng cấu hình để hai khách không đặt trúng chỗ cuối của cùng một khung giờ (phải gọi trong transaction)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM SystemConfig c WHERE c.configKey = :key")
    Optional<SystemConfig> lockByKey(@Param("key") String key);
}
