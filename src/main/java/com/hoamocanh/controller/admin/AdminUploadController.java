package com.hoamocanh.controller.admin;

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

/** Upload ảnh nguyên liệu. File lưu ở {app.upload-dir}/material, phục vụ tại /uploads/material/... */
@RestController
@RequestMapping("/admin/uploads")
public class AdminUploadController {

    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "webp");
    private static final long MAX_IMAGE = 5L * 1024 * 1024;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @PostMapping("/material-image")
    public ResponseEntity<Map<String, String>> uploadMaterialImage(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new IllegalArgumentException("File trống.");

        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot < 0 ? "" : original.substring(dot + 1).toLowerCase(Locale.ROOT);
        String type = file.getContentType() == null ? "" : file.getContentType();

        if (!IMAGE_EXT.contains(ext) || !type.startsWith("image/")) {
            throw new IllegalArgumentException("Chỉ nhận ảnh jpg, png hoặc webp.");
        }
        if (file.getSize() > MAX_IMAGE) throw new IllegalArgumentException("Ảnh tối đa 5MB.");

        Path dir = Paths.get(uploadDir, "material").toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        file.transferTo(dir.resolve(name));

        return ResponseEntity.ok(Map.of("url", "/uploads/material/" + name));
    }
}
