package vn.mocanh.studio;

import static vn.mocanh.studio.Json.*;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

public class ImageProvider {
  private final String apiBase;

  public ImageProvider() {
    this("https://api.openai.com/v1");
  }

  ImageProvider(String apiBase) {
    this.apiBase = apiBase;
  }

  public record Result(byte[] image, Map<String, Object> usage) {}

  public static class Failure extends RuntimeException {
    public final boolean billed;

    public Failure(String message, boolean billed) {
      super(message);
      this.billed = billed;
    }
  }

  final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public Result mix(Settings cfg, List<Path> photos, String prompt) {
    if (cfg.key().isBlank()) throw new Failure("Chưa cấu hình API key trên máy chủ.", false);
    String boundary = "MocAnh" + id(12);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      Map<String, Object> fields =
          map(
              "model",
              cfg.model(),
              "prompt",
              prompt,
              "size",
              cfg.get("IMAGE_SIZE", "1024x1024"),
              "quality",
              cfg.get("IMAGE_QUALITY", "low"),
              "output_format",
              "png",
              "n",
              "1");
      for (var e : fields.entrySet()) {
        out.write(
            ("--"
                    + boundary
                    + "\r\nContent-Disposition: form-data; name=\""
                    + e.getKey()
                    + "\"\r\n\r\n"
                    + e.getValue()
                    + "\r\n")
                .getBytes(StandardCharsets.UTF_8));
      }
      for (int i = 0; i < photos.size(); i++) {
        out.write(
            ("--"
                    + boundary
                    + "\r\nContent-Disposition: form-data; name=\"image[]\"; filename=\"reference-"
                    + (i + 1)
                    + ".jpg\"\r\nContent-Type: image/jpeg\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(Files.readAllBytes(photos.get(i)));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
      }
      out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
      return decode(
          send(
              cfg,
              apiBase + "/images/edits",
              out.toByteArray(),
              "multipart/form-data; boundary=" + boundary));
    } catch (Failure e) {
      throw e;
    } catch (Exception e) {
      throw new Failure(
          "Dịch vụ tạo ảnh lỗi kết nối hoặc quá thời gian. Không tự gửi lại vì lượt cũ có thể đã"
              + " tính phí.",
          true);
    }
  }

  public Result generate(Settings cfg, String prompt) {
    if (cfg.key().isBlank()) throw new Failure("Chưa cấu hình OPENAI_API_KEY ở backend.", false);
    return decode(
        send(
            cfg,
            apiBase + "/images/generations",
            write(
                    map(
                        "model",
                        cfg.model(),
                        "prompt",
                        prompt,
                        "n",
                        1,
                        "size",
                        cfg.get("IMAGE_SIZE", "1024x1024"),
                        "quality",
                        cfg.get("IMAGE_QUALITY", "low"),
                        "output_format",
                        "png"))
                .getBytes(StandardCharsets.UTF_8),
            "application/json"));
  }

  Map<String, Object> send(Settings cfg, String url, byte[] body, String type) {
    try {
      HttpRequest r =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(Duration.ofSeconds(cfg.integer("PROVIDER_TIMEOUT_SECONDS", 180)))
              .header("Authorization", "Bearer " + cfg.key())
              .header("Content-Type", type)
              .POST(HttpRequest.BodyPublishers.ofByteArray(body))
              .build();
      var response = http.send(r, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() != 200)
        throw failure(
            response.statusCode(),
            response.body(),
            response.headers().firstValue("Retry-After").orElse(""));
      return read(response.body());
    } catch (Failure e) {
      throw e;
    } catch (Exception e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new Failure(
          "Dịch vụ tạo ảnh lỗi kết nối hoặc quá thời gian. Không tự gửi lại vì lượt cũ có thể đã"
              + " tính phí.",
          true);
    }
  }

  Result decode(Map<String, Object> payload) {
    try {
      byte[] raw =
          Base64.getDecoder().decode(str(obj(list(payload.get("data")).getFirst()), "b64_json"));
      return new Result(
          Images.normalize(raw, 20000000, 128, "png", 0),
          obj(payload.getOrDefault("usage", map())));
    } catch (Exception e) {
      throw new Failure("Dịch vụ tạo ảnh chưa trả về ảnh hợp lệ.", true);
    }
  }

  public Failure failure(int code, String raw, String retry) {
    Map<String, String> messages =
        Map.ofEntries(
            Map.entry(
                "credit_balance_exhausted",
                "Tài khoản API đã hết số dư trả trước. Kiểm tra Billing và bổ sung số dư API nếu"
                    + " muốn tiếp tục."),
            Map.entry(
                "insufficient_quota",
                "Tài khoản API chưa có hoặc đã hết hạn mức sử dụng. Kiểm tra Billing, Usage và"
                    + " Limits của đúng project; gói ChatGPT không cấp số dư cho API key này."),
            Map.entry(
                "billing_hard_limit_reached",
                "API đã chạm giới hạn chi tiêu. Kiểm tra Billing và Limits trước khi tạo lại."),
            Map.entry(
                "organization_usage_limit_exceeded",
                "Tài khoản tổ chức đã đạt hạn mức sử dụng API được cấp. Kiểm tra Limits hoặc yêu"
                    + " cầu tăng hạn mức."),
            Map.entry(
                "organization_spend_limit_exceeded",
                "API đã chạm giới hạn chi tiêu của tổ chức. Kiểm tra Limits của tổ chức."),
            Map.entry(
                "project_spend_limit_exceeded",
                "API đã chạm giới hạn chi tiêu của project. Kiểm tra Limits của project chứa API"
                    + " key."),
            Map.entry(
                "rate_limit_exceeded",
                "API đang giới hạn tốc độ tạo ảnh. Chờ theo Retry-After rồi thử lại, hoặc giảm số"
                    + " yêu cầu đồng thời."),
            Map.entry(
                "rate_limit_error",
                "API đang giới hạn tốc độ tạo ảnh. Chờ theo Retry-After rồi thử lại, hoặc giảm số"
                    + " yêu cầu đồng thời."),
            Map.entry(
                "slow_down",
                "Tốc độ gửi yêu cầu tăng quá nhanh. Chờ trước khi thử lại và tăng tốc độ từ từ."),
            Map.entry(
                "server_is_overloaded",
                "Mô hình tạo ảnh đang quá tải tạm thời. Chờ trước khi thử lại."),
            Map.entry(
                "model_not_found",
                "Mô hình không tồn tại hoặc project chưa được cấp quyền. Kiểm tra IMAGE_MODEL và"
                    + " quyền truy cập mô hình."),
            Map.entry(
                "invalid_api_key",
                "API key không hợp lệ, đã hết hiệu lực hoặc bị thu hồi. Kiểm tra key trong .env rồi"
                    + " khởi động lại server."));
    String label = "";
    try {
      var error = obj(read(raw).get("error"));
      String c = str(error, "code"), t = str(error, "type");
      label = messages.containsKey(c) ? c : messages.containsKey(t) ? t : "";
    } catch (Exception ignored) {
    }
    String message = messages.get(label);
    if (message == null)
      message =
          switch (code) {
            case 400 ->
                "Yêu cầu tạo ảnh bị từ chối. Kiểm tra mô hình, kích thước và chất lượng trong"
                    + " .env.";
            case 401 ->
                "API key không hợp lệ hoặc không có quyền xác thực. Kiểm tra .env rồi khởi động lại"
                    + " server.";
            case 403 -> "Project/tài khoản chưa có quyền truy cập yêu cầu tạo ảnh.";
            case 404 -> "Không tìm thấy mô hình hoặc endpoint được yêu cầu. Kiểm tra IMAGE_MODEL.";
            case 429 ->
                "OpenAI trả HTTP 429 nhưng không có mã lỗi chi tiết nhận diện được. Kiểm tra"
                    + " Billing, Usage và Limits; chưa thể kết luận là giới hạn tốc độ hay hết số"
                    + " dư.";
            case 500 -> "Dịch vụ tạo ảnh gặp lỗi máy chủ. Chờ trước khi thử lại.";
            case 503 -> "Mô hình tạo ảnh tạm thời quá tải. Chờ trước khi thử lại.";
            default -> "Dịch vụ tạo ảnh trả về lỗi chưa xác định.";
          };
    if (Set.of("rate_limit_exceeded", "rate_limit_error", "slow_down", "server_is_overloaded")
            .contains(label)
        || code == 503)
      try {
        int seconds = (int) Double.parseDouble(retry);
        if (seconds > 0 && seconds <= 86400) message += " Chờ tối thiểu " + seconds + " giây.";
      } catch (Exception ignored) {
      }
    return new Failure(
        message + " [HTTP " + code + (label.isEmpty() ? "" : "; " + label) + "]", code >= 500);
  }
}
