package com.hoamocanh.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. Xử lý rủi ro "Race Condition" - Khách hàng đặt trùng lúc hoa/voucher vừa hết[cite: 23]
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "Conflict");
        response.put("message", "Hệ thống đang cập nhật hoặc loại hoa bạn chọn vừa có người mua. Vui lòng tải lại giỏ hàng!");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    // 2. Xử lý lỗi Vượt ngân sách hoặc chọn sai quy định[cite: 23]
    @ExceptionHandler(BudgetExceededException.class)
    public ResponseEntity<Map<String, String>> handleBudgetExceeded(BudgetExceededException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "Bad Request");
        response.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 3. Xử lý lỗi Hết hàng cục bộ[cite: 23]
    @ExceptionHandler(OutOfStockException.class)
    public ResponseEntity<Map<String, String>> handleOutOfStock(OutOfStockException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "Bad Request");
        response.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 4. Các lỗi tham số thông thường (VD: Truyền thiếu ID)[cite: 23]
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "Bad Request");
        response.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // 5. Lỗi không tìm thấy tài nguyên (MỚI)
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleResourceNotFound(ResourceNotFoundException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "Not Found");
        response.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    // 6. Xử lý lỗi chọn mức giá không khớp với định mức tối thiểu của loại sản phẩm (VD: Kệ hoa phải từ 1 triệu trở lên)
    @ExceptionHandler(InvalidBudgetException.class)
    public ResponseEntity<Map<String, String>> handleInvalidBudget(InvalidBudgetException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "Invalid Budget");
        response.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}