package com.hoamocanh.controller.customer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Upload ảnh/video đính kèm lời chúc cho luồng GỬI TẶNG ONLINE. File lưu ở {app.upload-dir}/gift và phục vụ tại /uploads/gift/... */
@RestController
@RequestMapping("/customer/uploads")
public class CustomerUploadController {

    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> VIDEO_EXT = Set.of("mp4", "webm");
    private static final long MAX_IMAGE = 8L * 1024 * 1024;
    private static final long MAX_VIDEO = 40L * 1024 * 1024;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @PostMapping("/gift")
    public ResponseEntity<Map<String, String>> uploadGift(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new IllegalArgumentException("File trống.");

        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot < 0 ? "" : original.substring(dot + 1).toLowerCase(Locale.ROOT);
        String contentType = file.getContentType() == null ? "" : file.getContentType();

        String type;
        if (IMAGE_EXT.contains(ext) && contentType.startsWith("image/")) {
            if (file.getSize() > MAX_IMAGE) throw new IllegalArgumentException("Ảnh tối đa 8MB.");
            type = "image";
        } else if (VIDEO_EXT.contains(ext) && contentType.startsWith("video/")) {
            if (file.getSize() > MAX_VIDEO) throw new IllegalArgumentException("Video tối đa 40MB.");
            type = "video";
        } else {
            throw new IllegalArgumentException("Chỉ nhận ảnh (jpg, png, webp) hoặc video (mp4, webm).");
        }

        Path dir = Paths.get(uploadDir, "gift").toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;   // tên ngẫu nhiên, không dùng tên khách gửi
        file.transferTo(dir.resolve(name));

        return ResponseEntity.ok(Map.of("url", "/uploads/gift/" + name, "type", type));
    }
}