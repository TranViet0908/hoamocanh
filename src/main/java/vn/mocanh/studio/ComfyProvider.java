package vn.mocanh.studio;

import static vn.mocanh.studio.Json.*;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

public class ComfyProvider {
  final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  String base(Settings c) {
    return c.get("COMFYUI_URL", "http://127.0.0.1:8188").replaceAll("/$", "");
  }

  HttpResponse<byte[]> call(Settings c, String path, Object body, int seconds) throws Exception {
    var b = HttpRequest.newBuilder(URI.create(base(c) + path)).timeout(Duration.ofSeconds(seconds));
    if (body == null) b.GET();
    else
      b.header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(write(body)));
    return http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
  }

  Map<String, Object> data(HttpResponse<byte[]> r) {
    if (r.statusCode() != 200)
      throw new ImageProvider.Failure(
          "Mất kết nối ComfyUI. Kiểm tra model và cấu hình máy chủ.", false);
    return read(new String(r.body(), java.nio.charset.StandardCharsets.UTF_8));
  }

  List<Object> models(Settings c) throws Exception {
    return list(
        list(obj(obj(obj(data(call(c, "/object_info/CheckpointLoaderSimple", null, 8))
                            .get("CheckpointLoaderSimple"))
                        .get("input"))
                    .get("required"))
                .get("ckpt_name"))
            .getFirst());
  }

  public Map<String, Object> readiness(Settings c) {
    String checkpoint = c.get("COMFYUI_CHECKPOINT", "v1-5-pruned-emaonly-fp16.safetensors");
    try {
      boolean ready = models(c).contains(checkpoint);
      return map(
          "ready",
          ready,
          "model",
          checkpoint,
          "message",
          ready
              ? "Đã kết nối ComfyUI và tìm thấy model. Bấm tạo ảnh để kiểm tra chạy trên GPU."
              : "ComfyUI đã mở nhưng chưa tìm thấy model đã chọn.");
    } catch (Exception e) {
      return map(
          "ready",
          false,
          "model",
          checkpoint,
          "message",
          "Chưa kết nối được ComfyUI. Mở ComfyUI tại http://127.0.0.1:8188; kiểm tra COMFYUI_URL"
              + " trong .env rồi thử lại.");
    }
  }

  Map<String, Object> node(String type, Object... args) {
    return map("class_type", type, "inputs", map(args));
  }

  public ImageProvider.Result generate(
      Settings c, Map<String, Object> d, String variant, LegacyService legacy) {
    String promptId = null;
    try {
      int w = c.integer("COMFYUI_WIDTH", 512),
          h = c.integer("COMFYUI_HEIGHT", 512),
          steps = c.integer("COMFYUI_STEPS", 20);
      double guidance = c.number("COMFYUI_CFG", 7);
      if (w < 256
          || w > 2048
          || h < 256
          || h > 2048
          || w % 64 != 0
          || h % 64 != 0
          || steps < 1
          || steps > 100
          || guidance < 0
          || guidance > 30)
        throw new ImageProvider.Failure(
            "Cấu hình ComfyUI kích thước/steps/CFG chưa hợp lệ.", false);
      String checkpoint = c.get("COMFYUI_CHECKPOINT", "v1-5-pruned-emaonly-fp16.safetensors");
      if (!models(c).contains(checkpoint))
        throw new ImageProvider.Failure("Không tìm thấy COMFYUI_CHECKPOINT.", false);
      long seed = Long.parseLong(hash(variant).substring(0, 15), 16);
      var graph =
          map(
              "4",
              node("CheckpointLoaderSimple", "ckpt_name", checkpoint),
              "5",
              node("EmptyLatentImage", "width", w, "height", h, "batch_size", 1),
              "6",
              node("CLIPTextEncode", "text", legacy.shortPrompt(d), "clip", List.of("4", 1)),
              "7",
              node(
                  "CLIPTextEncode",
                  "text",
                  "drawing, painting, illustration, cartoon, anime, CGI, 3d render, plastic,"
                      + " blurry, distorted, people, text, logo, watermark",
                  "clip",
                  List.of("4", 1)),
              "3",
              node(
                  "KSampler",
                  "seed",
                  seed,
                  "steps",
                  steps,
                  "cfg",
                  guidance,
                  "sampler_name",
                  c.get("COMFYUI_SAMPLER", "euler"),
                  "scheduler",
                  c.get("COMFYUI_SCHEDULER", "normal"),
                  "denoise",
                  1,
                  "model",
                  List.of("4", 0),
                  "positive",
                  List.of("6", 0),
                  "negative",
                  List.of("7", 0),
                  "latent_image",
                  List.of("5", 0)),
              "8",
              node("VAEDecode", "samples", List.of("3", 0), "vae", List.of("4", 2)),
              "9",
              node("SaveImage", "images", List.of("8", 0), "filename_prefix", "MocAnh/flower"));
      promptId =
          str(data(call(c, "/prompt", map("prompt", graph, "client_id", id(16)), 30)), "prompt_id");
      long deadline =
          System.nanoTime()
              + Duration.ofSeconds(c.integer("PROVIDER_TIMEOUT_SECONDS", 180)).toNanos();
      while (System.nanoTime() < deadline) {
        var history = data(call(c, "/history/" + promptId, null, 30));
        if (history.containsKey(promptId)) {
          Map<String, Object> entry = obj(history.get(promptId)),
              status = obj(entry.getOrDefault("status", map()));
          if (str(status, "status_str").equals("error")
              || list(status.getOrDefault("messages", List.of())).stream()
                  .anyMatch(
                      x ->
                          Set.of("execution_error", "execution_interrupted")
                              .contains(list(x).getFirst())))
            throw new ImageProvider.Failure(
                "ComfyUI tạo ảnh thất bại. Xem cửa sổ ComfyUI để kiểm tra model/GPU.", false);
          var images =
              list(
                  obj(obj(entry.getOrDefault("outputs", map())).getOrDefault("9", map()))
                      .getOrDefault("images", List.of()));
          if (!images.isEmpty()) {
            var info = obj(images.getFirst());
            String query =
                "/view?filename="
                    + URLEncoder.encode(
                        str(info, "filename"), java.nio.charset.StandardCharsets.UTF_8)
                    + "&subfolder="
                    + URLEncoder.encode(
                        str(info, "subfolder"), java.nio.charset.StandardCharsets.UTF_8)
                    + "&type="
                    + info.getOrDefault("type", "output");
            var response = call(c, query, null, 30);
            if (response.statusCode() != 200)
              throw new ImageProvider.Failure("ComfyUI không trả về dữ liệu ảnh hợp lệ.", false);
            return new ImageProvider.Result(
                Images.normalize(response.body(), 20000000, 1, "png", 0),
                map(
                    "backend",
                    "comfyui",
                    "checkpoint",
                    checkpoint,
                    "prompt_id",
                    promptId,
                    "seed",
                    seed,
                    "width",
                    w,
                    "height",
                    h,
                    "steps",
                    steps));
          }
          if (bool(status, "completed"))
            throw new ImageProvider.Failure(
                "ComfyUI hoàn thành nhưng không có ảnh tại node SaveImage.", false);
        }
        Thread.sleep((long) (Math.max(.05, c.number("COMFYUI_POLL_SECONDS", 1)) * 1000));
      }
      throw new java.net.http.HttpTimeoutException("ComfyUI timeout");
    } catch (ImageProvider.Failure e) {
      throw e;
    } catch (Exception e) {
      if (promptId != null)
        try {
          call(c, "/queue", map("delete", List.of(promptId)), 3);
        } catch (Exception ignored) {
        }
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new ImageProvider.Failure(
          "ComfyUI quá thời gian chờ hoặc mất kết nối; lượt còn chờ đã được gửi lệnh xóa. Nếu đang"
              + " chạy, chờ hoàn tất trước khi tạo lại.",
          false);
    }
  }
}
