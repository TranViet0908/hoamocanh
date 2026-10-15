package vn.mocanh.studio;

import static vn.mocanh.studio.Checks.*;
import static vn.mocanh.studio.Json.*;

import com.google.zxing.*;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;

public class LegacyService {
  final Engine e;
  final Store db;
  final Settings cfg;
  final Map<String, Object> constants;
  public static final String REVISION = "ace89b7d2cd849f9a73914cdbb8a3ea60c853dd1";
  static final Map<String, Object> RULES =
      map(
          "labor_vnd",
          50000,
          "guidance_vnd",
          30000,
          "margin_vnd",
          40000,
          "reservation_seconds",
          900);
  static final Set<String> DESIGN_FIELDS =
      Set.of(
          "product",
          "budget",
          "materials",
          "packaging",
          "style",
          "tone",
          "ribbon",
          "occasion",
          "notes",
          "form",
          "size",
          "reference_id");

  public LegacyService(Engine e) {
    this.e = e;
    db = e.db;
    cfg = e.cfg;
    try {
      constants =
          read(
              new String(
                  getClass().getResourceAsStream("/legacy-catalog.json").readAllBytes(),
                  java.nio.charset.StandardCharsets.UTF_8));
    } catch (Exception x) {
      throw new IllegalStateException(x);
    }
    for (Object item : list(constants.get("MATERIALS"))) {
      var m = obj(item);
      db.exec(
          "INSERT OR IGNORE INTO inventory VALUES(?,?,?,?)",
          m.get("id"),
          m.get("stock"),
          m.get("price"),
          null);
    }
    for (var p : obj(constants.get("PACKAGING")).entrySet())
      db.exec(
          "INSERT OR IGNORE INTO inventory VALUES(?,?,?,?)",
          p.getKey(),
          999,
          list(p.getValue()).get(2),
          null);
    db.exec("INSERT OR IGNORE INTO settings VALUES(?,?)", "rules", write(RULES));
    for (var job : db.all("SELECT id FROM jobs WHERE status='queued'"))
      e.generations.submit(() -> process(str(job, "id")));
  }

  Map<String, Object> rules() {
    return read(str(db.one("SELECT value FROM settings WHERE key='rules'"), "value"));
  }

  void expire() {
    db.exec(
        "UPDATE reservations SET status='expired' WHERE status='reserved_test' AND expires<?",
        now());
  }

  public List<Map<String, Object>> materials() {
    expire();
    Map<String, Long> held = new HashMap<>();
    for (var r : db.all("SELECT snapshot FROM reservations WHERE status='reserved_test'"))
      for (Object v : list(obj(read(str(r, "snapshot")).get("design")).get("materials"))) {
        var p = obj(v);
        held.merge(str(p, "id"), num(p, "quantity"), Long::sum);
      }
    var setting = db.one("SELECT value FROM settings WHERE key='palette'");
    var palette = setting == null ? map() : read(str(setting, "value"));
    Map<String, Double> weights =
        Map.of(
            "rose",
            1.,
            "tulip",
            .8,
            "daisy",
            .6,
            "chrysanthemum",
            1.5,
            "tana",
            .6,
            "baby",
            1.2,
            "eucalyptus",
            1.3);
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object v : list(constants.get("MATERIALS"))) {
      var m = new LinkedHashMap<>(obj(v));
      String id = str(m, "id");
      var inv = db.one("SELECT * FROM inventory WHERE id=?", id);
      m.put("visual_weight", weights.getOrDefault(id, 1.));
      if (palette.containsKey(id)) m.putAll(obj(palette.get(id)));
      m.put("stock", Math.max(0, num(inv, "stock") - held.getOrDefault(id, 0L)));
      m.put("price", inv.get("price"));
      m.put("photo_is_sample", inv.get("photo") == null);
      if (inv.get("photo") != null) m.put("photo_url", "/api/v1/catalog/photos/" + id);
      else m.putIfAbsent("photo_url", "/static/photos/" + id + ".jpg");
      result.add(m);
    }
    return result;
  }

  Map<String, Object> packagePhoto(String id) {
    var inv = db.one("SELECT photo FROM inventory WHERE id=?", id);
    boolean custom = inv != null && inv.get("photo") != null;
    var asset = obj(obj(constants.get("ASSETS")).get("packaging"));
    return map(
        "photo_url",
        custom
            ? "/api/v1/catalog/photos/" + id
            : obj(asset.getOrDefault(id, map()))
                .getOrDefault("photo_url", "/static/photos/" + id + ".jpg"),
        "photo_is_sample",
        !custom);
  }

  public Map<String, Object> catalog() {
    List<Map<String, Object>> products = new ArrayList<>(), packaging = new ArrayList<>();
    obj(constants.get("PRODUCTS"))
        .forEach(
            (id, value) -> {
              var p = list(value);
              products.add(map("id", id, "name", p.get(0), "minimum", p.get(2)));
            });
    obj(constants.get("PACKAGING"))
        .forEach(
            (id, value) -> {
              var p = list(value);
              var b = map("id", id, "name", p.get(0), "price", p.get(2), "products", p.get(3));
              b.putAll(
                  obj(obj(obj(constants.get("ASSETS")).get("packaging")).getOrDefault(id, map())));
              b.putAll(packagePhoto(id));
              packaging.add(b);
            });
    return map(
        "products",
        products,
        "materials",
        materials(),
        "packaging",
        packaging,
        "ribbon_options",
        obj(constants.get("ASSETS")).get("ribbons"),
        "styles",
        constants.get("STYLES"),
        "tones",
        constants.get("TONES"),
        "ribbons",
        constants.get("RIBBONS"),
        "occasions",
        constants.get("OCCASIONS"),
        "sample_data",
        true);
  }

  Map<String, Object> design(Map<String, Object> raw) {
    var d =
        defaults(
            raw,
            map(
                "product",
                "bouquet",
                "budget",
                500000,
                "packaging",
                "cream",
                "style",
                "romantic",
                "tone",
                "pink-white",
                "ribbon",
                "pink",
                "occasion",
                "birthday",
                "notes",
                "",
                "form",
                "round",
                "size",
                "medium",
                "reference_id",
                ""),
            DESIGN_FIELDS);
    choice(d, "product", "mix", "bouquet", "basket", "box", "bag", "stand");
    range(d, "budget", 400000, 10000000);
    string(d, "notes", 0, 500);
    string(d, "reference_id", 0, 80);
    choice(d, "form", "round", "natural", "long");
    choice(d, "size", "small", "medium", "large");
    var picks = list(d.get("materials"));
    if (picks.isEmpty() || picks.size() > 10)
      throw new ApiError(422, "Chọn từ 1 đến 10 nguyên liệu.");
    Set<String> seen = new HashSet<>();
    for (Object v : picks) {
      var p = obj(v);
      defaults(p, map(), Set.of("id", "quantity"));
      string(p, "id", 0, 40);
      range(p, "quantity", 1, 40);
      if (!seen.add(str(p, "id"))) throw new ApiError(422, "Nguyên liệu bị trùng.");
    }
    return d;
  }

  public synchronized Map<String, Object> check(Map<String, Object> raw) {
    var d = design(raw);
    Map<String, Object> products = obj(constants.get("PRODUCTS")),
        packages = obj(constants.get("PACKAGING"));
    var product = list(products.get(str(d, "product")));
    if (num(d, "budget") % 100000 != 0 || num(d, "budget") < ((Number) product.get(2)).longValue())
      throw new ApiError(422, "Mức giá không hợp lệ cho sản phẩm này.");
    for (var entry :
        Map.of("style", "STYLES", "tone", "TONES", "ribbon", "RIBBONS", "occasion", "OCCASIONS")
            .entrySet())
      if (!obj(constants.get(entry.getValue())).containsKey(str(d, entry.getKey())))
        throw new ApiError(422, "Lựa chọn phong cách/màu/phụ kiện không hợp lệ.");
    if (!packages.containsKey(str(d, "packaging"))
        || !list(list(packages.get(str(d, "packaging"))).get(3)).contains(d.get("product")))
      throw new ApiError(422, "Bao bì không phù hợp loại sản phẩm.");
    Map<String, Map<String, Object>> byId = new HashMap<>();
    for (var m : materials()) byId.put(str(m, "id"), m);
    var r = rules();
    long packageCost = ((Number) list(packages.get(str(d, "packaging"))).get(2)).longValue(),
        ribbon = str(d, "ribbon").equals("none") ? 0 : 15000,
        materialCost = 0;
    boolean flower = false;
    List<Map<String, Object>> selections = new ArrayList<>();
    Map<String, Long> quantities = new HashMap<>();
    for (Object v : list(d.get("materials"))) {
      var p = obj(v);
      var m = byId.get(str(p, "id"));
      if (m == null || num(p, "quantity") > num(m, "stock"))
        throw new ApiError(422, "Nguyên liệu hết hàng hoặc số lượng vượt tồn kho mẫu.");
      materialCost += num(m, "price") * num(p, "quantity");
      flower |= str(m, "kind").equals("flower");
      quantities.put(str(p, "id"), num(p, "quantity"));
      selections.add(
          map(
              "id",
              p.get("id"),
              "name",
              m.get("name"),
              "quantity",
              p.get("quantity"),
              "unit_price_vnd",
              m.get("price")));
    }
    if (!flower) throw new ApiError(422, "Hãy chọn ít nhất một loại hoa.");
    long
        total =
            materialCost
                + packageCost
                + ribbon
                + num(r, "labor_vnd")
                + num(r, "guidance_vnd")
                + num(r, "margin_vnd"),
        remaining = num(d, "budget") - total;
    if (remaining < 0)
      throw new ApiError(
          422,
          "Vượt ngân sách: định mức mẫu là "
              + String.format(Locale.US, "%,d", total)
              + "đ. Hãy giảm nguyên liệu hoặc tăng mức giá.");
    var units = map();
    for (var m : byId.values())
      units.put(
          str(m, "id"),
          Math.min(
              Math.max(0, num(m, "stock") - quantities.getOrDefault(str(m, "id"), 0L)),
              num(m, "price") == 0 ? num(m, "stock") : remaining / num(m, "price")));
    return map(
        "estimated_total_vnd",
        total,
        "remaining_vnd",
        remaining,
        "catalog_mode",
        "sample",
        "breakdown",
        map(
            "materials_vnd",
            materialCost,
            "packaging_vnd",
            packageCost,
            "ribbon_vnd",
            ribbon,
            "labor_vnd",
            r.get("labor_vnd"),
            "guidance_vnd",
            r.get("guidance_vnd"),
            "margin_vnd",
            r.get("margin_vnd")),
        "materials",
        selections,
        "prompt",
        shortPrompt(d),
        "design",
        d,
        "remaining_units",
        units);
  }

  public String shortPrompt(Map<String, Object> d) {
    Map<String, String> names = new HashMap<>();
    for (Object v : list(constants.get("MATERIALS"))) {
      var m = obj(v);
      names.put(str(m, "id"), str(m, "en"));
    }
    String ingredients =
        String.join(
            ", ",
            list(d.get("materials")).stream().map(p -> names.get(str(obj(p), "id"))).toList());
    String packaging =
        list(obj(constants.get("PACKAGING")).get(str(d, "packaging"))).get(1).toString();
    String form =
        switch (str(d, "product")) {
          case "mix" -> "loose cut flower stems bundled in " + packaging;
          case "basket" -> "flowers arranged in a woven basket";
          case "box" -> "flowers arranged in a pink box";
          case "bag" -> "flowers arranged in a white paper gift bag";
          case "stand" -> "a tall floral display mounted on an easel stand";
          default -> "a hand-tied bouquet wrapped in " + packaging;
        };
    String notes = str(d, "notes").toLowerCase(Locale.ROOT);
    for (var pair :
        Map.ofEntries(
                Map.entry("bó tròn", "round bouquet"),
                Map.entry("bo tron", "round bouquet"),
                Map.entry("nhẹ nhàng", "delicate"),
                Map.entry("nhe nhang", "delicate"),
                Map.entry("bất đối xứng", "asymmetric"),
                Map.entry("tự nhiên", "natural"),
                Map.entry("nơ lớn", "large ribbon bow"),
                Map.entry("ít giấy", "minimal wrapping"),
                Map.entry("cao", "tall"),
                Map.entry("thấp", "low"))
            .entrySet()) notes = notes.replace(pair.getKey(), pair.getValue());
    return "Real photo of "
        + form
        + ": "
        + ingredients
        + ". "
        + obj(constants.get("RIBBONS")).get(d.get("ribbon"))
        + ", "
        + obj(constants.get("STYLES")).get(d.get("style"))
        + ", "
        + obj(constants.get("TONES")).get(d.get("tone"))
        + ". Matte fresh petals, daylight, plain background, no lettering. "
        + notes;
  }

  public Map<String, Object> manifest() {
    List<Map<String, Object>> models = new ArrayList<>();
    String[] names = {"text_encoder", "unet", "vae_encoder", "vae_decoder"};
    long[] bytes = {681393168L, 1733430199L, 68430468L, 99094314L};
    boolean installed = true;
    for (int i = 0; i < 4; i++) {
      String path = names[i] + "/model.onnx";
      Path file = cfg.root.resolve("static/models/sd-turbo").resolve(path);
      try {
        installed &= Files.size(file) == bytes[i];
      } catch (Exception x) {
        installed = false;
      }
      models.add(map("name", names[i], "path", path, "bytes", bytes[i]));
    }
    String base =
        installed
            ? "/static/models/sd-turbo"
            : "https://huggingface.co/schmuell/sd-turbo-ort-web/resolve/" + REVISION;
    for (var model : models) model.put("url", base + "/" + model.get("path"));
    return map(
        "id",
        "sd-turbo",
        "revision",
        REVISION,
        "runtime",
        "onnxruntime-web@1.22.0",
        "resolution",
        512,
        "total_bytes",
        Arrays.stream(bytes).sum(),
        "files",
        models,
        "local_files",
        installed,
        "commercial_license_review_required",
        true,
        "pipeline_version",
        "vae-mean-v1",
        "vae_encoding",
        "posterior_mean",
        "supported_strengths",
        List.of(.25, .5, .75, 1.),
        "default_strength",
        .5,
        "quality_status",
        "experimental_not_quality_approved");
  }

  public Map<String, Object> references() {
    var c = file(cfg.root.resolve("static/references/catalog.json"));
    for (Object v : list(c.get("templates"))) {
      var item = obj(v);
      if (!str(item, "photo_url").isEmpty()) continue;
      for (String suffix : List.of("jpg", "png", "webp")) {
        String name = str(item, "id") + "." + suffix;
        if (Files.isRegularFile(cfg.root.resolve("static/references").resolve(name))) {
          item.put("photo_url", "/static/references/" + name);
          item.put("description", str(item, "description").split(";")[0] + ".");
          break;
        }
      }
    }
    return c;
  }

  public Map<String, Object> publicJob(Map<String, Object> j, boolean cached) {
    long queue =
        str(j, "status").equals("queued")
            ? num(
                db.one(
                    "SELECT COUNT(*) n FROM jobs WHERE status='queued' AND created<=?",
                    j.get("created")),
                "n")
            : 0;
    return map(
        "job_id",
        j.get("id"),
        "status",
        j.get("status"),
        "image_url",
        j.get("filename") != null ? "/api/v1/jobs/" + j.get("id") + "/image" : null,
        "cached",
        cached,
        "queue_position",
        queue,
        "provider",
        j.get("provider"),
        "model",
        j.get("model"),
        "error",
        j.get("error"),
        "estimated_cost_usd",
        j.get("estimate"),
        "usage",
        str(j, "usage").isBlank() ? map() : read(str(j, "usage")),
        "design",
        read(str(j, "design")),
        "prompt",
        j.get("prompt"),
        "created_at",
        j.get("created"),
        "updated_at",
        j.get("updated"));
  }

  Map<String, Object> saved(String id, String owner) {
    var j = e.owned("jobs", id, owner);
    if (!str(j, "status").equals("completed"))
      throw new ApiError(404, "Không tìm thấy thiết kế đã lưu.");
    return j;
  }

  public synchronized Map<String, Object> save(
      Map<String, Object> raw, byte[] image, String owner) {
    var b =
        defaults(
            raw,
            map(
                "seed",
                0,
                "strength",
                .5,
                "elapsed_ms",
                0,
                "model",
                "sd-turbo",
                "prompt",
                "",
                "pipeline_version",
                "legacy"),
            Set.of(
                "design",
                "source",
                "request_id",
                "seed",
                "strength",
                "elapsed_ms",
                "model",
                "prompt",
                "pipeline_version"));
    b.put("design", design(obj(b.get("design"))));
    choice(b, "source", "collage", "moodboard", "browser_ai", "palette");
    string(b, "request_id", 8, 100);
    range(b, "seed", 0, 4294967295L);
    decimal(b, "strength", .25, 1);
    decimal(b, "elapsed_ms", 0, 36000000);
    choice(b, "model", "sd-turbo", "none");
    string(b, "prompt", 0, 3000);
    choice(b, "pipeline_version", "legacy", "vae-mean-v1");
    String fingerprint = fingerprint(b);
    var old =
        db.one("SELECT * FROM jobs WHERE owner=? AND request_id=?", owner, b.get("request_id"));
    if (old != null) {
      if (!str(old, "fingerprint").equals(fingerprint))
        throw new ApiError(409, "request_id đã dùng cho thiết kế khác.");
      return publicJob(old, true);
    }
    check(obj(b.get("design")));
    if (image == null
        || image.length == 0
        || image.length > cfg.integer("MAX_UPLOAD_MB", 20) * 1024 * 1024)
      throw new ApiError(413, "Ảnh vượt dung lượng cho phép.");
    try {
      image = Images.normalize(image, 16000000, 1, "png", 0);
    } catch (ApiError x) {
      throw new ApiError(415, "Ảnh lưu không hợp lệ.");
    }
    String id = id(16), name = id + ".png";
    var usage =
        map(
            "source",
            b.get("source"),
            "seed",
            b.get("seed"),
            "strength",
            b.get("strength"),
            "elapsed_ms",
            b.get("elapsed_ms"),
            "client_reported",
            true,
            "image_authenticity_verified",
            false,
            "model_revision",
            str(b, "source").equals("browser_ai") ? REVISION : null,
            "pipeline_version",
            str(b, "source").equals("browser_ai") ? b.get("pipeline_version") : null);
    if (str(b, "source").equals("palette")) {
      Map<String, Object> quantities = map();
      for (Object v : list(obj(b.get("design")).get("materials"))) {
        var p = obj(v);
        quantities.put(str(p, "id"), p.get("quantity"));
      }
      usage.put(
          "palette_snapshot",
          materials().stream()
              .filter(m -> quantities.containsKey(str(m, "id")))
              .map(
                  m ->
                      map(
                          "id",
                          m.get("id"),
                          "name",
                          m.get("name"),
                          "quantity",
                          quantities.get(str(m, "id")),
                          "color",
                          m.get("color"),
                          "visual_weight",
                          m.get("visual_weight")))
              .toList());
    }
    e.save(name, image);
    double t = now();
    db.exec(
        "INSERT INTO"
            + " jobs(id,owner,request_id,fingerprint,design,prompt,variant,status,created,updated,filename,provider,model,usage)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        owner,
        b.get("request_id"),
        fingerprint,
        write(b.get("design")),
        b.get("prompt"),
        "" + b.get("seed"),
        "completed",
        t,
        t,
        name,
        str(b, "source").equals("browser_ai") ? "browser_ai" : b.get("source"),
        str(b, "source").equals("browser_ai") ? b.get("model") : "photo-compositor",
        write(usage));
    return publicJob(e.owned("jobs", id, owner), false);
  }

  public Map<String, Object> event(Map<String, Object> b, String owner) {
    b =
        defaults(
            b,
            map("elapsed_ms", 0, "downloaded_bytes", 0, "error_code", ""),
            Set.of("action", "status", "elapsed_ms", "downloaded_bytes", "error_code"));
    choice(b, "action", "device_check", "model_load", "generation");
    choice(b, "status", "ready", "unsupported", "started", "completed", "failed", "cancelled");
    decimal(b, "elapsed_ms", 0, 36000000);
    range(b, "downloaded_bytes", 0, 20000000000L);
    string(b, "error_code", 0, 80);
    if (!str(b, "error_code").matches("[a-zA-Z0-9_-]*"))
      throw new ApiError(422, "Mã lỗi không hợp lệ.");
    db.exec(
        "INSERT INTO"
            + " browser_events(owner,action,status,elapsed_ms,downloaded_bytes,error_code,created)"
            + " VALUES(?,?,?,?,?,?,?)",
        owner,
        b.get("action"),
        b.get("status"),
        b.get("elapsed_ms"),
        b.get("downloaded_bytes"),
        b.get("error_code"),
        now());
    return map("recorded", true, "client_reported", true);
  }

  public Map<String, Object> quote(Map<String, Object> b, String owner) {
    b = defaults(b, map(), Set.of("job_id", "mode"));
    choice(b, "mode", "workshop", "made_by_shop");
    var j = saved(str(b, "job_id"), owner);
    var snapshot = check(read(str(j, "design")));
    String id = id(16);
    double t = now();
    db.exec(
        "INSERT INTO quotes VALUES(?,?,?,?,?,?,?)",
        id,
        owner,
        j.get("id"),
        b.get("mode"),
        write(snapshot),
        t,
        t + 300);
    return map(
        "quote_id",
        id,
        "mode",
        b.get("mode"),
        "retail_price_vnd",
        obj(snapshot.get("design")).get("budget"),
        "snapshot",
        snapshot,
        "expires_at",
        t + 300,
        "image_url",
        "/api/v1/jobs/" + j.get("id") + "/image",
        "test_only",
        true);
  }

  public synchronized Map<String, Object> reserve(Map<String, Object> b, String owner) {
    b =
        defaults(
            b,
            map("appointment", ""),
            Set.of("quote_id", "request_id", "customer_name", "phone", "appointment"));
    string(b, "request_id", 8, 100);
    string(b, "customer_name", 1, 100);
    phone(b);
    string(b, "appointment", 0, 40);
    var old =
        db.one(
            "SELECT * FROM reservations WHERE owner=? AND request_id=?",
            owner,
            b.get("request_id"));
    if (old != null) {
      if (!str(old, "quote_id").equals(str(b, "quote_id")))
        throw new ApiError(409, "request_id đã dùng cho báo giá khác.");
      return map(
          "reservation_id",
          old.get("id"),
          "status",
          old.get("status"),
          "expires_at",
          old.get("expires"),
          "test_only",
          true);
    }
    var q = db.one("SELECT * FROM quotes WHERE id=? AND owner=?", b.get("quote_id"), owner);
    if (q == null || dec(q, "expires") < now())
      throw new ApiError(409, "Báo giá đã hết hạn; hãy kiểm tra lại.");
    var snapshot = read(str(q, "snapshot"));
    var current = check(obj(snapshot.get("design")));
    for (String key : List.of("estimated_total_vnd", "breakdown", "materials"))
      if (!fingerprint(current.get(key)).equals(fingerprint(snapshot.get(key))))
        throw new ApiError(409, "Giá hoặc định mức vừa thay đổi; hãy lấy báo giá mới.");
    if (str(q, "mode").equals("workshop"))
      try {
        if (!OffsetDateTime.parse(str(b, "appointment")).isAfter(OffsetDateTime.now()))
          throw new IllegalArgumentException();
      } catch (Exception ex) {
        throw new ApiError(422, "Cần lịch trải nghiệm trong tương lai, kèm múi giờ.");
      }
    String id = "MA-" + id(5).toUpperCase(Locale.ROOT);
    double t = now(), expires = t + num(rules(), "reservation_seconds");
    db.exec(
        "INSERT INTO reservations VALUES(?,?,?,?,?,?,?,?,?,?,?)",
        id,
        owner,
        b.get("request_id"),
        q.get("id"),
        write(current),
        b.get("customer_name"),
        b.get("phone"),
        b.get("appointment"),
        expires,
        "reserved_test",
        t);
    return map(
        "reservation_id",
        id,
        "status",
        "reserved_test",
        "expires_at",
        expires,
        "test_only",
        true,
        "message",
        "Đã giữ nguyên liệu mẫu. Chưa thanh toán, chưa tạo đơn thật; tiệm cần xác nhận.");
  }

  public Map<String, Object> release(String id, String owner) {
    e.owned("reservations", id, owner);
    db.exec("UPDATE reservations SET status='cancelled' WHERE id=?", id);
    return map("released", true);
  }

  public synchronized Map<String, Object> consultation(Map<String, Object> b, String owner) {
    b =
        defaults(
            b,
            map("priority", "color", "card_message", ""),
            Set.of(
                "job_id",
                "request_id",
                "customer_name",
                "phone",
                "receive_date",
                "priority",
                "card_message"));
    string(b, "request_id", 8, 100);
    nonblank(b, "customer_name", 100);
    phone(b);
    date(b, "receive_date", false);
    choice(b, "priority", "color", "flowers", "advice");
    string(b, "card_message", 0, 500);
    String fingerprint = fingerprint(b);
    var old =
        db.one(
            "SELECT * FROM consultations WHERE owner=? AND request_id=?",
            owner,
            b.get("request_id"));
    if (old != null) {
      if (!str(old, "fingerprint").equals(fingerprint))
        throw new ApiError(409, "Mã yêu cầu đã dùng cho nội dung khác.");
      return map(
          "request_id", old.get("id"), "status", "pending_shop_confirmation", "sample_data", true);
    }
    var j = saved(str(b, "job_id"), owner);
    var design = read(str(j, "design"));
    var snapshot = new LinkedHashMap<>(b);
    snapshot.putAll(
        map(
            "design",
            design,
            "estimate",
            check(design),
            "image_url",
            "/api/v1/jobs/" + j.get("id") + "/image",
            "palette",
            read(str(j, "usage")).get("palette_snapshot")));
    String id = id(12);
    db.exec(
        "INSERT INTO consultations VALUES(?,?,?,?,?,?)",
        id,
        owner,
        b.get("request_id"),
        fingerprint,
        write(snapshot),
        now());
    return map("request_id", id, "status", "pending_shop_confirmation", "sample_data", true);
  }

  public Map<String, Object> consultations(String owner) {
    var rows =
        owner == null
            ? db.all("SELECT * FROM consultations ORDER BY created DESC")
            : db.all("SELECT * FROM consultations WHERE owner=? ORDER BY created DESC", owner);
    return map(
        "requests",
        rows.stream()
            .map(
                r ->
                    map(
                        "request_id",
                        r.get("id"),
                        "created_at",
                        r.get("created"),
                        "snapshot",
                        read(str(r, "snapshot"))))
            .toList());
  }

  public Map<String, Object> updatePalette(String id, Map<String, Object> b) {
    if (materials().stream().noneMatch(m -> str(m, "id").equals(id)))
      throw new ApiError(404, "Không tìm thấy nguyên liệu.");
    b = defaults(b, map(), Set.of("color", "visual_weight"));
    string(b, "color", 7, 7);
    if (!str(b, "color").matches("#[0-9a-fA-F]{6}")) throw new ApiError(422, "Màu không hợp lệ.");
    decimal(b, "visual_weight", .1, 100);
    var old = db.one("SELECT value FROM settings WHERE key='palette'");
    var palette = old == null ? map() : read(str(old, "value"));
    palette.put(id, b);
    db.exec("INSERT OR REPLACE INTO settings VALUES(?,?)", "palette", write(palette));
    return b;
  }

  public Map<String, Object> updateInventory(String id, Map<String, Object> b) {
    b = defaults(b, map(), Set.of("stock", "price"));
    range(b, "stock", 0, 100000);
    range(b, "price", 0, 10000000);
    if (db.one("SELECT id FROM inventory WHERE id=?", id) == null)
      throw new ApiError(404, "Not Found");
    db.exec("UPDATE inventory SET stock=?,price=? WHERE id=?", b.get("stock"), b.get("price"), id);
    return map("id", id, "stock", b.get("stock"), "price", b.get("price"));
  }

  public Map<String, Object> updatePhoto(String id, byte[] image) {
    if (db.one("SELECT id FROM inventory WHERE id=?", id) == null)
      throw new ApiError(404, "Not Found");
    if (image == null || image.length > cfg.integer("MAX_UPLOAD_MB", 20) * 1024 * 1024)
      throw new ApiError(413, "Ảnh vượt dung lượng cho phép.");
    String name = "catalog-" + id(16) + ".png";
    try {
      image = Images.normalize(image, 20000000, 1, "png", 1200, true);
    } catch (ApiError ex) {
      throw new ApiError(415, "Ảnh không hợp lệ.");
    }
    e.save(name, image);
    db.exec("UPDATE inventory SET photo=? WHERE id=?", name, id);
    return map("photo_url", "/api/v1/catalog/photos/" + id);
  }

  public Map<String, Object> updateRules(Map<String, Object> b) {
    b = defaults(b, RULES, RULES.keySet());
    for (String k : List.of("labor_vnd", "guidance_vnd", "margin_vnd")) range(b, k, 0, 10000000);
    range(b, "reservation_seconds", 60, 86400);
    db.exec("UPDATE settings SET value=? WHERE key='rules'", write(b));
    return b;
  }

  public Map<String, Object> browserMetrics() {
    return map(
        "events",
        db.all(
            "SELECT action,status,COUNT(*) count,AVG(elapsed_ms)"
                + " avg_elapsed_ms,SUM(downloaded_bytes) downloaded_bytes FROM browser_events GROUP"
                + " BY action,status"),
        "client_reported",
        true,
        "gpu_api_cost_usd",
        0,
        "storage_bytes",
        e.storage(),
        "hosting_cost_usd",
        null,
        "hosting_cost_note",
        "Cần hóa đơn hosting/CDN; không quy đổi telemetry thành phí khách.");
  }

  public Map<String, Object> metrics() {
    var counts = map();
    for (var row : db.all("SELECT status,COUNT(*) n FROM jobs GROUP BY status"))
      counts.put(str(row, "status"), row.get("n"));
    var totals =
        db.one(
            "SELECT COALESCE(SUM(estimate),0) estimate,COALESCE(SUM(reserved),0) reserved FROM"
                + " jobs");
    return map(
        "jobs",
        counts,
        "estimated_cost_usd",
        totals.get("estimate"),
        "reserved_budget_usd",
        totals.get("reserved"),
        "cost_is_estimate",
        true);
  }

  public Map<String, Object> upload(byte[] data, String mime, String owner) {
    if (data == null
        || data.length == 0
        || data.length > cfg.integer("MAX_UPLOAD_MB", 20) * 1024 * 1024)
      throw new ApiError(413, "Tệp vượt dung lượng cho phép.");
    String ext;
    if (Set.of("image/jpeg", "image/png", "image/webp").contains(mime)) {
      try {
        data = Images.normalize(data, 20000000, 1, "jpg", 0);
      } catch (ApiError x) {
        throw new ApiError(415, "Ảnh không hợp lệ hoặc quá lớn.");
      }
      ext = ".jpg";
      mime = "image/jpeg";
    } else if (mime.equals("video/mp4")
        && data.length >= 12
        && new String(data, 4, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("ftyp"))
      ext = ".mp4";
    else if (mime.equals("video/webm")
        && data.length >= 4
        && HexFormat.of().formatHex(Arrays.copyOf(data, 4)).equals("1a45dfa3")) ext = ".webm";
    else throw new ApiError(415, "Chỉ nhận JPG/PNG/WebP, MP4 hoặc WebM.");
    String id = id(16), name = id + ext;
    e.save(name, data);
    db.exec("INSERT INTO uploads VALUES(?,?,?,?,?)", id, owner, name, mime, now());
    return map("media_id", id, "mime", mime);
  }

  public Map<String, Object> gift(String token) {
    var g = db.one("SELECT * FROM gifts WHERE token=?", token);
    if (g == null || num(g, "revoked") != 0 || dec(g, "expires") < now())
      throw new ApiError(404, "Quà tặng không tồn tại, hết hạn hoặc đã thu hồi.");
    return g;
  }

  public Map<String, Object> createGift(Map<String, Object> b, String owner) {
    b =
        defaults(
            b,
            map("sender", "", "media_ids", List.of()),
            Set.of("job_id", "recipient", "sender", "message", "media_ids"));
    string(b, "recipient", 1, 100);
    string(b, "sender", 0, 100);
    string(b, "message", 1, 2000);
    if (list(b.get("media_ids")).size() > 4) throw new ApiError(422, "Chỉ nhận tối đa 4 tệp.");
    var j = e.owned("jobs", str(b, "job_id"), owner);
    if (!str(j, "status").equals("completed"))
      throw new ApiError(409, "Chờ ảnh tạo xong trước khi gửi quà.");
    for (Object uid : list(b.get("media_ids")))
      if (db.one("SELECT id FROM uploads WHERE id=? AND owner=?", uid, owner) == null)
        throw new ApiError(422, "Tệp đính kèm không thuộc phiên này.");
    String token = token(24);
    double t = now(), expires = t + cfg.integer("GIFT_TTL_DAYS", 30) * 86400.;
    db.exec(
        "INSERT INTO gifts VALUES(?,?,?,?,?,?,?,?,?,0)",
        token,
        owner,
        b.get("job_id"),
        b.get("recipient"),
        b.get("sender"),
        b.get("message"),
        write(b.get("media_ids")),
        t,
        expires);
    return map(
        "gift_id",
        token,
        "url",
        cfg.baseUrl() + "/gift/" + token,
        "qr_url",
        "/api/v1/gifts/" + token + "/qr",
        "expires_at",
        expires);
  }

  public Map<String, Object> publicGift(String token) {
    var g = gift(token);
    List<Map<String, Object>> files = new ArrayList<>();
    for (Object uid : list(any(str(g, "media_ids")))) {
      var u = db.one("SELECT * FROM uploads WHERE id=?", uid);
      if (u != null)
        files.add(map("url", "/api/v1/gifts/" + token + "/media/" + uid, "mime", u.get("mime")));
    }
    return map(
        "recipient",
        g.get("recipient"),
        "sender",
        g.get("sender"),
        "message",
        g.get("message"),
        "image_url",
        "/api/v1/gifts/" + token + "/image",
        "media",
        files,
        "expires_at",
        g.get("expires"));
  }

  public Map<String, Object> revoke(String token, String owner) {
    if (db.one("SELECT token FROM gifts WHERE token=? AND owner=?", token, owner) == null)
      throw new ApiError(404, "Not Found");
    db.exec("UPDATE gifts SET revoked=1 WHERE token=?", token);
    return map("revoked", true);
  }

  public byte[] qr(String token) {
    gift(token);
    try {
      var matrix =
          new QRCodeWriter()
              .encode(cfg.baseUrl() + "/gift/" + token, BarcodeFormat.QR_CODE, 360, 360);
      BufferedImage img = new BufferedImage(360, 360, BufferedImage.TYPE_INT_RGB);
      for (int x = 0; x < 360; x++)
        for (int y = 0; y < 360; y++) img.setRGB(x, y, matrix.get(x, y) ? 0 : 0xffffff);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(img, "png", out);
      return out.toByteArray();
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  public String modelName() {
    return cfg.provider().equals("comfyui")
        ? cfg.get("COMFYUI_CHECKPOINT", "v1-5-pruned-emaonly-fp16.safetensors")
        : cfg.provider().equals("openai") ? cfg.model() : "local-illustration";
  }

  public synchronized Map<String, Object> generate(Map<String, Object> b, String owner) {
    b = defaults(b, map("new_variant", false), Set.of("design", "new_variant", "request_id"));
    string(b, "request_id", 8, 100);
    if (!(b.get("new_variant") instanceof Boolean))
      throw new ApiError(422, "new_variant không hợp lệ.");
    if (cfg.provider().equals("browser"))
      throw new ApiError(
          409, "Tạo ảnh trong trình duyệt rồi lưu qua /browser/designs; server không chạy model.");
    var d = obj(check(obj(b.get("design"))).get("design"));
    var picks = new ArrayList<>(list(d.get("materials")));
    picks.sort(Comparator.comparing(x -> str(obj(x), "id")));
    d.put("materials", picks);
    String fingerprint =
        fingerprint(
            List.of(
                d,
                cfg.provider(),
                cfg.provider().equals("comfyui")
                    ? List.of(
                        "sd15-photo-v1",
                        cfg.get("COMFYUI_CHECKPOINT", "v1-5-pruned-emaonly-fp16.safetensors"),
                        cfg.integer("COMFYUI_WIDTH", 512),
                        cfg.integer("COMFYUI_HEIGHT", 512),
                        cfg.integer("COMFYUI_STEPS", 20),
                        cfg.number("COMFYUI_CFG", 7),
                        cfg.get("COMFYUI_SAMPLER", "euler"),
                        cfg.get("COMFYUI_SCHEDULER", "normal"))
                    : List.of(
                        cfg.model(),
                        cfg.get("IMAGE_QUALITY", "low"),
                        cfg.get("IMAGE_SIZE", "1024x1024"))));
    var old =
        db.one("SELECT * FROM jobs WHERE owner=? AND request_id=?", owner, b.get("request_id"));
    if (old != null) {
      if (!str(old, "fingerprint").equals(fingerprint))
        throw new ApiError(409, "request_id đã dùng cho cấu hình khác.");
      return publicJob(old, true);
    }
    var active =
        db.one("SELECT * FROM jobs WHERE owner=? AND status IN ('queued','processing')", owner);
    if (active != null) {
      if (str(active, "fingerprint").equals(fingerprint)) return publicJob(active, false);
      throw new ApiError(409, "Bạn đang có một yêu cầu được xử lý.");
    }
    if (!bool(b, "new_variant")) {
      var cached =
          db.one(
              "SELECT * FROM jobs WHERE owner=? AND fingerprint=? AND status='completed' ORDER BY"
                  + " created DESC LIMIT 1",
              owner,
              fingerprint);
      if (cached != null) return publicJob(cached, true);
    }
    if (now() - dec(db.one("SELECT last_submit FROM sessions WHERE token=?", owner), "last_submit")
        < cfg.number("COOLDOWN_SECONDS", 8))
      throw new ApiError(429, "Vui lòng chờ trước lần tạo tiếp theo.");
    if (num(db.one("SELECT COUNT(*) n FROM jobs WHERE status='queued'"), "n")
        >= cfg.integer("MAX_QUEUE_SIZE", 100))
      throw new ApiError(503, "Hàng đợi đầy. Hãy thử lại sau.");
    if (cfg.provider().equals("openai") && cfg.key().isBlank())
      throw new ApiError(503, "Chưa cấu hình khóa API tạo ảnh ở backend.");
    double reserve = cfg.provider().equals("openai") ? cfg.cost() : 0,
        day = Math.floor(now() / 86400) * 86400;
    double spent =
        dec(db.one("SELECT COALESCE(SUM(reserved),0) n FROM jobs WHERE created>=?", day), "n");
    if (cfg.provider().equals("openai") && cfg.budget() > 0 && spent + reserve > cfg.budget())
      throw new ApiError(
          503,
          "Dịch vụ tạm dừng theo ngân sách vận hành. Thử lại vào ngày tiếp theo hoặc liên hệ quản"
              + " trị.");
    String id = id(16), variant = id(8);
    double t = now();
    db.exec(
        "INSERT INTO"
            + " jobs(id,owner,request_id,fingerprint,design,prompt,variant,status,created,updated,provider,model,reserved)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        owner,
        b.get("request_id"),
        fingerprint,
        write(d),
        longPrompt(d, variant),
        variant,
        "queued",
        t,
        t,
        cfg.provider(),
        modelName(),
        reserve);
    db.exec("UPDATE sessions SET last_submit=? WHERE token=?", t, owner);
    var result = publicJob(e.owned("jobs", id, owner), false);
    e.generations.submit(() -> process(id));
    return result;
  }

  String longPrompt(Map<String, Object> d, String variant) {
    Map<String, Map<String, Object>> names = new HashMap<>();
    for (Object v : list(constants.get("MATERIALS"))) {
      var m = obj(v);
      names.put(str(m, "id"), m);
    }
    var picks = new ArrayList<>(list(d.get("materials")));
    picks.sort(Comparator.comparing(p -> str(obj(p), "id")));
    String ingredients =
        String.join(
            ", ",
            picks.stream()
                .map(
                    p ->
                        num(obj(p), "quantity")
                            + " stems/units of "
                            + str(names.get(str(obj(p), "id")), "en"))
                .toList());
    return "Create a photorealistic studio product photograph of "
        + list(obj(constants.get("PRODUCTS")).get(str(d, "product"))).get(1)
        + ". Use these selected ingredients: "
        + ingredients
        + ". Packaging: "
        + list(obj(constants.get("PACKAGING")).get(str(d, "packaging"))).get(1)
        + ". Accessory: "
        + obj(constants.get("RIBBONS")).get(d.get("ribbon"))
        + ". Style: "
        + obj(constants.get("STYLES")).get(d.get("style"))
        + ". Requested palette: "
        + obj(constants.get("TONES")).get(d.get("tone"))
        + "; preserve the named flowers' natural colors when the palette conflicts. Occasion: "
        + obj(constants.get("OCCASIONS")).get(d.get("occasion"))
        + ". Real fresh flowers with detailed petals, natural veins, realistic stems, authentic"
        + " wrapping texture, soft natural shadows and realistic photographic depth of field. Whole"
        + " product visible, soft daylight, warm ivory background, beautiful florist arrangement."
        + " Must look like a professional camera photograph, not a drawing, painting, vector"
        + " illustration, cartoon or 3D render. Do not add other flower species, people, text,"
        + " logos, watermarks or price labels. This is an illustrative concept; exact stem counting"
        + " is not essential. Compose a fresh arrangement for variation identifier "
        + variant
        + ".";
  }

  void process(String id) {
    db.exec("UPDATE jobs SET status='processing',updated=? WHERE id=?", now(), id);
    var job = db.one("SELECT * FROM jobs WHERE id=?", id);
    try {
      ImageProvider.Result result;
      switch (str(job, "provider")) {
        case "openai":
          result = e.provider.generate(cfg, str(job, "prompt"));
          break;
        case "demo":
          Thread.sleep(800);
          result = new ImageProvider.Result(Images.demo(), map());
          break;
        case "comfyui":
          result =
              new ComfyProvider()
                  .generate(cfg, read(str(job, "design")), str(job, "variant"), this);
          break;
        default:
          throw new ImageProvider.Failure(
              "IMAGE_PROVIDER phải là demo, openai hoặc comfyui.", false);
      }
      String name = id + ".png";
      e.save(name, Images.normalize(result.image(), 20000000, 1, "png", 0));
      double cost = str(job, "provider").equals("openai") ? cfg.cost() : 0;
      db.exec(
          "UPDATE jobs SET status='completed',filename=?,usage=?,estimate=?,reserved=?,updated=?"
              + " WHERE id=?",
          name,
          write(result.usage()),
          cost,
          cost,
          now(),
          id);
    } catch (Exception failure) {
      db.exec(
          "UPDATE jobs SET status='failed',error=?,updated=? WHERE id=?",
          failure instanceof ImageProvider.Failure
              ? failure.getMessage()
              : "Tạo ảnh thất bại hoặc quá thời gian. Hãy thử lại.",
          now(),
          id);
      if (failure instanceof ImageProvider.Failure f && !f.billed)
        db.exec("UPDATE jobs SET reserved=0 WHERE id=?", id);
    }
  }
}
