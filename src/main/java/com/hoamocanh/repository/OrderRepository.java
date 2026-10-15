package com.hoamocanh.repository;

import com.hoamocanh.core.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {

    // Admin: đơn mới nhất trước, nạp sẵn loại sản phẩm (truyền PageRequest.of(0, 300) để giới hạn)
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.productType ORDER BY o.createdAt DESC")
    List<Order> findRecent(Pageable pageable);

    // Admin: một đơn kèm chi tiết nguyên liệu
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.productType LEFT JOIN FETCH o.orderDetails d LEFT JOIN FETCH d.material WHERE o.id = :id")
    Optional<Order> findDetailById(@Param("id") Integer id);

    // Khóa dòng đơn khi đổi trạng thái/hủy: hai nhân viên cùng bấm hủy thì chỉ một người hoàn kho
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> lockById(@Param("id") Integer id);

    // Chỗ đang được giữ cho khách: đơn PICKUP chưa hủy và (đã có tiền hoặc còn trong hạn giữ chỗ).
    // Đơn chưa cọc mà quá hạn thì không chiếm chỗ nữa (admin vẫn thấy đơn qua findPickupBetween).
    @Query("SELECT o FROM Order o WHERE o.deliveryType = 'PICKUP' AND o.status <> 'CANCELLED' AND o.deliveryTime >= :from AND o.deliveryTime < :to "
            + "AND (COALESCE(o.paymentStatus, 'UNPAID') <> 'UNPAID' OR o.createdAt >= :holdCutoff) ORDER BY o.deliveryTime, o.id")
    List<Order> findHeldPickupBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, @Param("holdCutoff") LocalDateTime holdCutoff);

    // Lịch trải nghiệm: đơn khách tự làm tại tiệm (PICKUP) chưa hủy, hẹn trong khoảng [from, to)
    @Query("SELECT o FROM Order o WHERE o.deliveryType = 'PICKUP' AND o.status <> 'CANCELLED' AND o.deliveryTime >= :from AND o.deliveryTime < :to ORDER BY o.deliveryTime, o.id")
    List<Order> findPickupBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}