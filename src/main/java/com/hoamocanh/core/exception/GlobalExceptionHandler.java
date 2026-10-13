package com.hoamocanh.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;

@Slf4j
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

    // Sai số điện thoại / mật khẩu khi đăng nhập quản trị
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> handleAuth(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Unauthorized", "message", "Số điện thoại hoặc mật khẩu không đúng."));
    }

    // Lỗi @Valid: trả câu báo lỗi đầu tiên
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getDefaultMessage()).findFirst().orElse("Dữ liệu không hợp lệ.");
        return ResponseEntity.badRequest().body(Map.of("error", "Bad Request", "message", msg));
    }

    // File tải lên vượt giới hạn trong application.properties
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Bad Request", "message", "Tệp tải lên quá lớn."));
    }

    // JSON gửi lên sai định dạng (thiếu dấu, sai kiểu dữ liệu...)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Bad Request", "message", "Dữ liệu gửi lên không hợp lệ."));
    }

    // Không đủ quyền (VD: STAFF gọi API chỉ dành cho ADMIN). Phải khai báo riêng để nhánh Exception bên dưới không nuốt thành 500.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Forbidden", "message", "Bạn không có quyền thực hiện thao tác này."));
    }

    // Lỗi còn lại: ghi log để tra cứu, trả thông báo thân thiện thay vì "Lỗi máy chủ (500)" trống
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleOther(Exception ex) {
        // Các lỗi chuẩn của Spring MVC (404 không có trang, 405, thiếu tham số...) giữ nguyên mã trạng thái của chúng
        if (ex instanceof ErrorResponse er) {
            return ResponseEntity.status(er.getStatusCode())
                    .body(Map.of("error", "Error", "message", "Yêu cầu không hợp lệ."));
        }
        log.error("Lỗi không mong đợi", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal Server Error", "message", "Hệ thống gặp sự cố, vui lòng thử lại sau."));
    }
}