package com.hoamocanh.core.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.io.IOException;
import java.util.Map;

/**
 * Chuyển mọi lỗi thành JSON {"error", "message"} để giao diện hiện đúng thông báo.
 * Riêng khi người dùng mở thẳng một đường dẫn trên trình duyệt (Accept có text/html) mà gặp lỗi 404, 405, 500...,
 * trả về trang lỗi tĩnh trong static/error/ (404.html, 4xx.html, 5xx.html) thay vì một đoạn JSON.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static ResponseEntity<Map<String, String>> body(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(Map.of("error", error, "message", message));
    }

    // Trình duyệt đang mở trang (không phải fetch/AJAX của giao diện)
    private static boolean wantsHtml(HttpServletRequest req) {
        String accept = req.getHeader("Accept");
        return accept != null && accept.contains("text/html");
    }

    // 1. Race condition: dữ liệu vừa bị người khác sửa (hoa, voucher vừa hết...)
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex) {
        return body(HttpStatus.CONFLICT, "Conflict",
                "Hệ thống đang cập nhật hoặc loại hoa bạn chọn vừa có người mua. Vui lòng tải lại giỏ hàng!");
    }

    // Khóa dòng (kho, đơn, khung giờ) quá lâu hoặc bị deadlock: thử lại là được
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleBusy(PessimisticLockingFailureException ex) {
        log.warn("Khóa dữ liệu bị tranh chấp: {}", ex.getMessage());
        return body(HttpStatus.CONFLICT, "Conflict", "Hệ thống đang xử lý nhiều đơn cùng lúc. Vui lòng thử lại sau vài giây.");
    }

    // Vi phạm ràng buộc DB (trùng khóa, dữ liệu quá dài...). Không lộ chi tiết SQL ra ngoài.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Vi phạm ràng buộc dữ liệu", ex);
        return body(HttpStatus.CONFLICT, "Conflict", "Dữ liệu không hợp lệ hoặc đã tồn tại. Vui lòng kiểm tra lại.");
    }

    // 2. Vượt ngân sách hoặc chọn sai quy định
    @ExceptionHandler(BudgetExceededException.class)
    public ResponseEntity<Map<String, String>> handleBudgetExceeded(BudgetExceededException ex) {
        return body(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    // 3. Hết hàng
    @ExceptionHandler(OutOfStockException.class)
    public ResponseEntity<Map<String, String>> handleOutOfStock(OutOfStockException ex) {
        return body(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    // 4. Tham số/dữ liệu sai (đa số lỗi kiểm tra đầu vào trong service)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return body(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    // 5. Không tìm thấy tài nguyên
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleResourceNotFound(ResourceNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
    }

    // 6. Mức giá thấp hơn mức tối thiểu của loại sản phẩm
    @ExceptionHandler(InvalidBudgetException.class)
    public ResponseEntity<Map<String, String>> handleInvalidBudget(InvalidBudgetException ex) {
        return body(HttpStatus.BAD_REQUEST, "Invalid Budget", ex.getMessage());
    }

    // 7. Mở khóa quà thất bại (sai mã, đang bị khóa)
    @ExceptionHandler(GiftUnlockException.class)
    public ResponseEntity<Map<String, String>> handleGiftUnlock(GiftUnlockException ex) {
        return body(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    // Sai số điện thoại / mật khẩu khi đăng nhập quản trị
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> handleAuth(AuthenticationException ex) {
        return body(HttpStatus.UNAUTHORIZED, "Unauthorized", "Số điện thoại hoặc mật khẩu không đúng.");
    }

    // Lỗi @Valid trên body: trả câu báo lỗi đầu tiên
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getDefaultMessage()).findFirst().orElse("Dữ liệu không hợp lệ.");
        return body(HttpStatus.BAD_REQUEST, "Bad Request", msg);
    }

    // Lỗi ràng buộc trên tham số (@RequestParam, @PathVariable)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleConstraint(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().stream().map(v -> v.getMessage()).findFirst().orElse("Dữ liệu không hợp lệ.");
        return body(HttpStatus.BAD_REQUEST, "Bad Request", msg);
    }

    // Tham số sai kiểu (VD: ngày không đúng định dạng, id không phải số)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return body(HttpStatus.BAD_REQUEST, "Bad Request", "Giá trị gửi lên không hợp lệ.");
    }

    // File tải lên vượt giới hạn trong application.properties
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleTooLarge(MaxUploadSizeExceededException ex) {
        return body(HttpStatus.BAD_REQUEST, "Bad Request", "Tệp tải lên quá lớn.");
    }

    // Yêu cầu tải tệp sai cách (không phải multipart, tệp hỏng...)
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, String>> handleMultipart(MultipartException ex) {
        return body(HttpStatus.BAD_REQUEST, "Bad Request", "Không đọc được tệp tải lên. Vui lòng chọn lại tệp.");
    }

    // JSON gửi lên sai định dạng
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException ex) {
        return body(HttpStatus.BAD_REQUEST, "Bad Request", "Dữ liệu gửi lên không hợp lệ.");
    }

    // Không đủ quyền (VD: STAFF gọi API chỉ dành cho ADMIN)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleDenied(AccessDeniedException ex) {
        return body(HttpStatus.FORBIDDEN, "Forbidden", "Bạn không có quyền thực hiện thao tác này.");
    }

    private static String friendly(int status) {
        return switch (status) {
            case 404 -> "Không tìm thấy trang hoặc đường dẫn này.";
            case 405 -> "Thao tác này không được hỗ trợ.";
            case 415 -> "Định dạng dữ liệu không được hỗ trợ.";
            default -> "Yêu cầu không hợp lệ.";
        };
    }

    /**
     * Lỗi còn lại. Lỗi chuẩn của Spring MVC (404 không có trang, 405, thiếu tham số...) giữ nguyên mã trạng thái.
     * Mở thẳng bằng trình duyệt thì hiện trang lỗi (static/error/*.html), còn giao diện gọi API thì nhận JSON.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleOther(Exception ex, HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        int status;
        String message;
        if (ex instanceof ErrorResponse er) {
            status = er.getStatusCode().value();
            message = friendly(status);
        } else {
            log.error("Lỗi không mong đợi tại {} {}", req.getMethod(), req.getRequestURI(), ex);
            status = HttpStatus.INTERNAL_SERVER_ERROR.value();
            message = "Hệ thống gặp sự cố, vui lòng thử lại sau.";
        }
        if (wantsHtml(req)) {
            res.sendError(status);   // Spring Boot dựng trang lỗi từ static/error/404.html, 4xx.html, 5xx.html
            return null;
        }
        return ResponseEntity.status(status).body(Map.of("error", status >= 500 ? "Internal Server Error" : "Error", "message", message));
    }
}