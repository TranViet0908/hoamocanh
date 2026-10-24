package com.hoamocanh.controller.admin;

import com.hoamocanh.core.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.mocanh.studio.Engine;

import java.util.*;

/**
 * Sửa thông tin mẫu hoa có sẵn (tên, kiểu dáng, thành phần) từ trang quản trị đã đăng nhập.
 * Gọi thẳng ShopService trong cùng ứng dụng nên không cần khóa tích hợp X-Integration-Key.
 * Giá (price_vnd), needs và approved được giữ nguyên: giá do khách chọn khi đặt hàng.
 */
@RestController
@RequestMapping("/admin/shop-products")
@RequiredArgsConstructor
public class AdminShopProductController {

    private final Engine engine;

    public record Edit(String name, String kind, List<String> ingredients, Boolean ingredientsVerified) { }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> edit(@PathVariable String id, @RequestBody Edit e) {
        Map<String, Object> cur;
        try {
            cur = engine.shop.product(id);
        } catch (RuntimeException ex) {
            throw new ResourceNotFoundException("Mẫu không tồn tại trong thư viện của tiệm.");
        }
        String name = e.name() == null ? "" : e.name().trim();
        if (name.isEmpty() || name.length() > 160) throw new IllegalArgumentException("Tên mẫu từ 1 đến 160 ký tự.");
        if (e.kind() == null || e.kind().isBlank()) throw new IllegalArgumentException("Vui lòng chọn kiểu dáng.");
        List<String> ing = new ArrayList<>();
        if (e.ingredients() != null) for (String s : e.ingredients()) {
            String t = s == null ? "" : s.trim();
            if (t.isEmpty()) continue;
            if (t.length() > 80) throw new IllegalArgumentException("Mỗi dòng thành phần tối đa 80 ký tự.");
            ing.add(t);
        }
        if (ing.size() > 30) throw new IllegalArgumentException("Tối đa 30 dòng thành phần.");

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("name", name);
        raw.put("kind", e.kind());
        raw.put("price_vnd", cur.get("price_vnd"));
        raw.put("ingredients", ing);
        raw.put("needs", cur.get("needs") == null ? List.of() : cur.get("needs"));
        raw.put("ingredients_verified", Boolean.TRUE.equals(e.ingredientsVerified()));
        raw.put("approved", Boolean.TRUE.equals(cur.get("approved")));
        try {
            return ResponseEntity.ok(engine.shop.updateProduct(id, raw));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(ex.getMessage() == null ? "Thông tin mẫu không hợp lệ." : ex.getMessage());
        }
    }
}