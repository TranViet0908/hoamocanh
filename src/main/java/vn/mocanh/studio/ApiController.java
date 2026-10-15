package vn.mocanh.studio;

import static vn.mocanh.studio.Json.*;

import jakarta.servlet.http.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class ApiController {
    final Engine e;

    public ApiController(Engine e) {
        this.e = e;
    }

    @RequestMapping({"/studio", "/static/**", "/gift/**", "/api/v1/**", "/openapi.json", "/docs"})
    public ResponseEntity<?> handle(HttpServletRequest r) throws Exception {
        String path = r.getRequestURI(), method = r.getMethod();
        var s = e.shop;
        var l = e.legacy;
        if (method.equals("OPTIONS")) return ResponseEntity.noContent().build();
        if (method.equals("GET") && path.startsWith("/static/")) return staticFile(path);
        if (method.equals("GET") && path.equals("/studio")) {
            if (!e.cfg.testUi()) throw new ApiError(404, "Not Found");
            return staticFile("/static/index.html");
        }
        if (method.equals("GET") && path.startsWith("/gift/")) {
            l.gift(path.substring(6));
            return staticFile("/static/gift.html");
        }
        if (method.equals("GET") && path.equals("/openapi.json"))
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Files.readAllBytes(e.cfg.root.resolve("docs/SHOP_OPENAPI.json")));
        if (method.equals("GET") && path.equals("/docs"))
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(
                            "<html><meta charset='utf-8'><h1>Mộc Anh Java API</h1><p>API giữ hợp đồng bản"
                                    + " 4.</p><a href='/openapi.json'>OpenAPI JSON</a></html>");
        if (method.equals("GET") && path.equals("/api/v1/health")) {
            var ready =
                    e.cfg.provider().equals("comfyui")
                            ? new ComfyProvider().readiness(e.cfg)
                            : map(
                            "ready",
                            e.cfg.provider().equals("openai") && !e.cfg.key().isBlank(),
                            "message",
                            "");
            return ok(
                    map(
                            "status",
                            "ok",
                            "provider",
                            e.cfg.provider(),
                            "real_ai_ready",
                            ready.get("ready"),
                            "ready_message",
                            ready.get("message"),
                            "model",
                            e.cfg.provider().equals("browser") ? "sd-turbo" : l.modelName(),
                            "cooldown_seconds",
                            e.cfg.number("COOLDOWN_SECONDS", 8),
                            "unlimited_total_generations",
                            true,
                            "catalog_mode",
                            "sample",
                            "test_ui",
                            e.cfg.testUi()));
        }
        if (method.equals("POST") && path.equals("/api/v1/sessions")) return ok(e.session());
        if (method.equals("GET") && path.equals("/api/v1/shop/catalog")) return ok(s.catalog());
        if (method.equals("GET") && path.equals("/api/v1/shop/ai-status")) return ok(s.status());
        if (method.equals("GET") && path.equals("/api/v1/catalog")) return ok(l.catalog());
        if (method.equals("GET") && path.equals("/api/v1/references")) return ok(l.references());
        if (method.equals("GET") && path.equals("/api/v1/browser/model")) return ok(l.manifest());
        String[] parts = path.substring(path.startsWith("/api/v1/") ? 8 : 0).split("/");
        if (method.equals("GET") && path.startsWith("/api/v1/gifts/")) {
            String token = parts[1];
            if (parts.length == 2) return ok(l.publicGift(token));
            var gift = l.gift(token);
            if (parts[2].equals("qr")) return image(l.qr(token), "image/png");
            if (parts[2].equals("image")) {
                var j = e.db.one("SELECT filename FROM jobs WHERE id=?", gift.get("job_id"));
                return image(e.media(str(j, "filename")), "image/png");
            }
            if (parts[2].equals("media") && parts.length == 4) {
                if (!list(any(str(gift, "media_ids"))).contains(parts[3]))
                    throw new ApiError(404, "Not Found");
                var upload = e.db.one("SELECT * FROM uploads WHERE id=?", parts[3]);
                if (upload == null) throw new ApiError(404, "Not Found");
                return image(e.media(str(upload, "filename")), str(upload, "mime"));
            }
        }
        if (method.equals("GET") && path.startsWith("/api/v1/catalog/photos/")) {
            var p = e.db.one("SELECT photo FROM inventory WHERE id=?", parts[2]);
            if (p == null || p.get("photo") == null) throw new ApiError(404, "Not Found");
            return image(e.media(str(p, "photo")), "image/png");
        }
        String integration = header(r, "X-Integration-Key");
        if (path.startsWith("/api/v1/shop/admin/")
                || path.startsWith("/api/v1/admin/")
                || path.equals("/api/v1/metrics")
                || path.equals("/api/v1/browser/metrics")) {
            e.admin(integration);
            if (method.equals("GET") && path.equals("/api/v1/shop/admin/requests"))
                return ok(s.adminRequests());
            if (parts.length == 5
                    && parts[0].equals("shop")
                    && parts[1].equals("admin")
                    && parts[2].equals("requests")) {
                String id = parts[3];
                if (method.equals("GET") && parts[4].equals("image")) {
                    var row = e.db.one("SELECT * FROM shop_requests WHERE id=?", id);
                    if (row == null || row.get("filename") == null)
                        throw new ApiError(404, "Chưa có ảnh đính kèm.");
                    return image(e.media(str(row, "filename")), "image/png");
                }
                if (method.equals("POST") && parts[4].equals("quote")) return ok(s.quote(id, body(r)));
                if (method.equals("POST") && parts[4].equals("status")) return ok(s.fulfill(id, body(r)));
            }
            if (method.equals("PUT")
                    && parts.length == 4
                    && path.startsWith("/api/v1/shop/admin/products/"))
                return ok(s.updateProduct(parts[3], body(r)));
            if (method.equals("GET") && path.equals("/api/v1/admin/consultations"))
                return ok(l.consultations(null));
            if (method.equals("PUT") && parts.length == 3 && parts[1].equals("palette"))
                return ok(l.updatePalette(parts[2], body(r)));
            if (method.equals("PUT") && parts.length == 3 && parts[1].equals("inventory"))
                return ok(l.updateInventory(parts[2], body(r)));
            if (method.equals("POST")
                    && parts.length == 4
                    && parts[1].equals("inventory")
                    && parts[3].equals("photo")) return ok(l.updatePhoto(parts[2], upload(r)));
            if (method.equals("GET") && path.equals("/api/v1/admin/rules")) return ok(l.rules());
            if (method.equals("PUT") && path.equals("/api/v1/admin/rules"))
                return ok(l.updateRules(body(r)));
            if (method.equals("GET") && path.equals("/api/v1/metrics")) return ok(l.metrics());
            if (method.equals("GET") && path.equals("/api/v1/browser/metrics"))
                return ok(l.browserMetrics());
            throw new ApiError(404, "Not Found");
        }
        String owner =
                e.identity(header(r, "X-Session-Token"), integration, header(r, "X-Customer-Id"));
        if (path.equals("/api/v1/shop/mixes")) {
            if (method.equals("GET")) return ok(s.mixes(owner));
            if (method.equals("POST")) return ok(s.createMix(body(r), owner));
        }
        if (parts.length >= 3
                && parts[0].equals("shop")
                && parts[1].equals("mixes")
                && method.equals("GET")) {
            var job = e.owned("shop_mix", parts[2], owner);
            if (parts.length == 3) return ok(s.publicMix(job));
            if (parts.length == 4 && parts[3].equals("image"))
                return image(e.media(str(job, "filename")), "image/png");
        }
        if (path.equals("/api/v1/shop/requests")) {
            if (method.equals("GET")) return ok(s.requests(owner));
            if (method.equals("POST")) return ok(s.createRequest(metadata(r), upload(r), owner));
        }
        if (parts.length == 4 && parts[0].equals("shop") && parts[1].equals("requests")) {
            if (method.equals("POST") && parts[3].equals("accept"))
                return ok(s.accept(parts[2], body(r), owner));
            if (method.equals("GET") && parts[3].equals("image"))
                return image(
                        e.media(str(e.owned("shop_requests", parts[2], owner), "filename")), "image/png");
        }
        if (method.equals("POST")
                && (path.equals("/api/v1/designs/validate") || path.equals("/api/v1/browser/prepare")))
            return ok(l.check(body(r)));
        if (method.equals("POST") && path.equals("/api/v1/browser/events"))
            return ok(l.event(body(r), owner));
        if (method.equals("POST") && path.equals("/api/v1/browser/designs"))
            return ok(l.save(metadata(r), upload(r), owner));
        if (method.equals("POST") && path.equals("/api/v1/generations"))
            return ok(l.generate(body(r), owner));
        if (method.equals("GET") && path.equals("/api/v1/jobs"))
            return ok(
                    map(
                            "jobs",
                            e
                                    .db
                                    .all("SELECT * FROM jobs WHERE owner=? ORDER BY created DESC LIMIT 30", owner)
                                    .stream()
                                    .map(j -> l.publicJob(j, false))
                                    .toList()));
        if (method.equals("GET") && parts[0].equals("jobs") && parts.length >= 2) {
            var j = e.owned("jobs", parts[1], owner);
            if (parts.length == 2) return ok(l.publicJob(j, false));
            if (parts.length == 3 && parts[2].equals("image"))
                return image(e.media(str(j, "filename")), "image/png");
        }
        if (path.equals("/api/v1/consultations")) {
            if (method.equals("GET")) return ok(l.consultations(owner));
            if (method.equals("POST")) return ok(l.consultation(body(r), owner));
        }
        if (method.equals("POST") && path.equals("/api/v1/checkout/quote"))
            return ok(l.quote(body(r), owner));
        if (method.equals("POST") && path.equals("/api/v1/checkout/reservations"))
            return ok(l.reserve(body(r), owner));
        if (method.equals("DELETE")
                && parts.length == 3
                && parts[0].equals("checkout")
                && parts[1].equals("reservations")) return ok(l.release(parts[2], owner));
        if (method.equals("POST") && path.equals("/api/v1/uploads")) {
            Part part = r.getPart("file");
            if (part == null) throw new ApiError(422, "Thiếu tệp.");
            return ok(l.upload(upload(r), Objects.toString(part.getContentType(), ""), owner));
        }
        if (method.equals("POST") && path.equals("/api/v1/gifts"))
            return ok(l.createGift(body(r), owner));
        if (method.equals("DELETE") && parts.length == 2 && parts[0].equals("gifts"))
            return ok(l.revoke(parts[1], owner));
        throw new ApiError(404, "Not Found");
    }

    String header(HttpServletRequest r, String k) {
        return Objects.toString(r.getHeader(k), "");
    }

    Map<String, Object> body(HttpServletRequest r) throws IOException {
        return read(
                new String(r.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
    }

    Map<String, Object> metadata(HttpServletRequest r) {
        String data = r.getParameter("metadata");
        if (data == null) throw new ApiError(422, "Thiếu thông tin metadata.");
        return read(data);
    }

    byte[] upload(HttpServletRequest r) throws Exception {
        Part file = r.getPart("file");
        if (file == null) return null;
        if (file.getSize() > 51L * 1024 * 1024) throw new ApiError(413, "Tệp quá lớn.");
        return file.getInputStream().readAllBytes();
    }

    ResponseEntity<?> ok(Object data) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(data);
    }

    ResponseEntity<?> image(byte[] bytes, String mime) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(mime)).body(bytes);
    }

    ResponseEntity<?> staticFile(String path) {
        Path p = e.cfg.root.resolve(path.substring(1)).normalize();
        if (!p.startsWith(e.cfg.root.resolve("static")) || !Files.isRegularFile(p))
            throw new ApiError(404, "Not Found");
        String name = p.toString().toLowerCase(Locale.ROOT);
        String mime =
                name.endsWith(".mjs") || name.endsWith(".js")
                        ? "text/javascript"
                        : name.endsWith(".html")
                        ? "text/html"
                        : name.endsWith(".css")
                        ? "text/css"
                        : name.endsWith(".json")
                        ? "application/json"
                        : name.endsWith(".png")
                        ? "image/png"
                        : name.endsWith(".jpg")
                        ? "image/jpeg"
                        : name.endsWith(".webp")
                        ? "image/webp"
                        : name.endsWith(".svg")
                        ? "image/svg+xml"
                        : name.endsWith(".wasm")
                        ? "application/wasm"
                        : "application/octet-stream";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mime))
                .body(new FileSystemResource(p));
    }

    @ExceptionHandler(ApiError.class)
    ResponseEntity<?> error(ApiError error) {
        return ResponseEntity.status(error.status).body(map("detail", error.detail));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception error) {
        return ResponseEntity.status(500)
                .body(map("detail", "Không hoàn tất yêu cầu. Kiểm tra máy chủ."));
    }
}