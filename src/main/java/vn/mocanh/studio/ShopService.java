package vn.mocanh.studio;

import static vn.mocanh.studio.Checks.*;
import static vn.mocanh.studio.Json.*;

import java.nio.file.*;
import java.time.*;
import java.util.*;

public class ShopService {
  public static final Set<String> MIX_KINDS =
      Set.of("bouquet", "basket", "gift-box", "vase", "cut-set", "event");
  final Engine e;
  final Store db;
  final Settings cfg;

  public ShopService(Engine e) {
    this.e = e;
    db = e.db;
    cfg = e.cfg;
  }

  public Map<String, Object> catalog() {
    var c = file(cfg.root.resolve("static/shop/catalog.json"));
    Map<String, Map<String, Object>> updates = new HashMap<>();
    for (var r : db.all("SELECT * FROM shop_products"))
      updates.put(str(r, "id"), read(str(r, "metadata")));
    for (Object item : list(c.get("products"))) {
      var p = obj(item);
      if (updates.containsKey(str(p, "id"))) {
        p.putAll(updates.get(str(p, "id")));
        p.put("price_status", p.get("price_vnd") != null ? "confirmed" : "unconfirmed");
      }
      p.put("mix_supported", MIX_KINDS.contains(str(p, "kind")));
    }
    return c;
  }

  public Map<String, Object> product(String id) {
    for (Object v : list(catalog().get("products"))) {
      var p = obj(v);
      if (str(p, "id").equals(id)) return p;
    }
    throw new ApiError(422, "Mẫu không tồn tại trong thư viện của tiệm.");
  }

  public Path photo(Map<String, Object> p) {
    Path path = cfg.root.resolve(str(p, "photo_url").replaceFirst("^/", "")).normalize();
    if (!path.startsWith(cfg.root.resolve("static/shop/images")))
      throw new ApiError(422, "Đường dẫn ảnh mẫu không hợp lệ.");
    return path;
  }

  public Map<String, Object> status() {
    return map(
        "ready",
        !cfg.key().isBlank(),
        "provider",
        "openai",
        "model",
        cfg.model(),
        "estimated_cost_usd",
        cfg.cost(),
        "daily_budget_usd",
        cfg.budget(),
        "message",
        cfg.key().isBlank()
            ? "Chưa kết nối dịch vụ sinh ảnh. Có thể chọn hai mẫu và gửi yêu cầu tư vấn."
            : "Sẵn sàng gửi hai ảnh tham chiếu.");
  }

  public Map<String, Object> publicMix(Map<String, Object> r) {
    return map(
        "id",
        r.get("id"),
        "status",
        r.get("status"),
        "recipe",
        read(str(r, "recipe")),
        "error",
        r.get("error"),
        "image_url",
        r.get("filename") != null ? "/api/v1/shop/mixes/" + r.get("id") + "/image" : null,
        "created_at",
        r.get("created"),
        "estimated_cost_usd",
        r.get("reserved"));
  }

  public Map<String, Object> publicRequest(Map<String, Object> r) {
    return map(
        "id",
        r.get("id"),
        "status",
        r.get("status"),
        "snapshot",
        read(str(r, "snapshot")),
        "quote",
        r.get("quote") != null ? read(str(r, "quote")) : null,
        "revision",
        r.get("revision"),
        "image_url",
        r.get("filename") != null ? "/api/v1/shop/requests/" + r.get("id") + "/image" : null,
        "created_at",
        r.get("created"));
  }

  public Map<String, Object> mixRecipe(Map<String, Object> raw) {
    var b =
        defaults(
            raw,
            map(
                "form",
                "a",
                "flowers_a",
                "",
                "flowers_b",
                "",
                "colors",
                "",
                "wrapping",
                "",
                "notes",
                ""),
            Set.of(
                "sample_a",
                "sample_b",
                "form",
                "flowers_a",
                "flowers_b",
                "colors",
                "wrapping",
                "notes",
                "budget",
                "request_id"));
    for (String k : List.of("sample_a", "sample_b")) string(b, k, 0, 80);
    choice(b, "form", "a", "b", "natural");
    for (String k : List.of("flowers_a", "flowers_b", "colors")) string(b, k, 0, 400);
    string(b, "wrapping", 0, 300);
    string(b, "notes", 0, 1000);
    range(b, "budget", 100000, 100000000);
    string(b, "request_id", 8, 100);
    return b;
  }

  public Map<String, Object> createMix(Map<String, Object> raw, String owner) {
    var b = mixRecipe(raw);
    var a = product(str(b, "sample_a"));
    var second = product(str(b, "sample_b"));
    if (str(a, "id").equals(str(second, "id"))
        || !MIX_KINDS.contains(str(a, "kind"))
        || !MIX_KINDS.contains(str(second, "kind")))
      throw new ApiError(
          422,
          "Hãy chọn hai mẫu khác nhau thuộc bó, giỏ, túi/hộp/vali, bình, set tự cắm hoặc hoa sự"
              + " kiện.");
    if (str(b, "form").equals("natural")
        && (!str(a, "kind").equals("bouquet") || !str(second, "kind").equals("bouquet")))
      throw new ApiError(
          422, "Chọn kiểu thành phẩm theo mẫu A hoặc B cho các mẫu không phải bó hoa.");
    var fingerprintBody = new LinkedHashMap<>(b);
    fingerprintBody.remove("request_id");
    String fingerprint = fingerprint(fingerprintBody);
    synchronized (this) {
      var old =
          db.one(
              "SELECT * FROM shop_mix WHERE owner=? AND request_id=?", owner, b.get("request_id"));
      if (old != null) {
        if (!str(old, "fingerprint").equals(fingerprint))
          throw new ApiError(409, "Mã yêu cầu đã dùng cho bản phối khác.");
        return publicMix(old);
      }
      if (cfg.key().isBlank())
        throw new ApiError(
            409, "Tiệm chưa kết nối dịch vụ sinh ảnh. Bạn vẫn có thể gửi hai mẫu để tiệm tư vấn.");
      if (num(
              db.one("SELECT COUNT(*) n FROM shop_mix WHERE status IN ('queued','processing')"),
              "n")
          >= Math.max(1, cfg.integer("WORKER_COUNT", 1)))
        throw new ApiError(429, "Đang có ảnh được xử lý. Chờ ảnh hiện tại hoàn tất rồi thử lại.");
      var last = db.one("SELECT MAX(created) last FROM shop_mix WHERE owner=?", owner);
      if (last.get("last") != null && now() - dec(last, "last") < cfg.number("COOLDOWN_SECONDS", 8))
        throw new ApiError(429, "Hãy chờ một chút trước khi tạo phương án tiếp theo.");
      double day = Math.floor(now() / 86400) * 86400,
          used =
              dec(
                      db.one(
                          "SELECT COALESCE(SUM(reserved),0) n FROM shop_mix WHERE created>=?", day),
                      "n")
                  + dec(
                      db.one("SELECT COALESCE(SUM(reserved),0) n FROM jobs WHERE created>=?", day),
                      "n");
      if (used + cfg.cost() > cfg.budget())
        throw new ApiError(429, "Đã đạt hạn mức dự kiến tạo ảnh trong ngày của tiệm.");
      String jid = id(12);
      double t = now();
      db.exec(
          "INSERT INTO shop_mix VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
          jid,
          owner,
          b.get("request_id"),
          fingerprint,
          write(b),
          prompt(b, a, second),
          "queued",
          null,
          null,
          "{}",
          cfg.cost(),
          t,
          t);
      var result = publicMix(e.owned("shop_mix", jid, owner));
      e.mixes.submit(() -> process(jid));
      return result;
    }
  }

  String flowers(Map<String, Object> b, String key, Map<String, Object> p, String label) {
    String value = str(b, key).trim();
    if (!value.isEmpty()) return value;
    var ingredients = list(p.getOrDefault("ingredients", List.of()));
    return ingredients.isEmpty()
        ? "Identify and retain the flower species visible in reference " + label
        : String.join(", ", ingredients.stream().map(Object::toString).toList());
  }

  String prompt(Map<String, Object> b, Map<String, Object> a, Map<String, Object> second) {
    var target = str(b, "form").equals("b") ? second : a;
    String shape =
        str(b, "form").equals("natural")
            ? "an airy natural hand-tied bouquet"
            : "the product type, silhouette and container of reference "
                + (str(b, "form").equals("b") ? "B" : "A")
                + " ("
                + str(target, "kind")
                + "); retain the vessel, packaging, supports and presentation visible in that"
                + " reference";
    String presentation =
        switch (str(target, "kind")) {
          case "vase" ->
              "Keep the vase from the target reference and arrange the flowers inside it.";
          case "cut-set" ->
              "Present a coordinated set of loose cut flowers ready for customer arranging,"
                  + " matching the target reference packaging; do not invent a vase or premade"
                  + " arrangement.";
          case "event" ->
              "Preserve the target reference event installation scale, structure and setting;"
                  + " combine floral elements into one coherent event design.";
          default -> "";
        };
    return "Create ONE new photorealistic florist arrangement using BOTH reference photographs."
        + " Image 1 is sample A ("
        + str(a, "name")
        + "); image 2 is sample B ("
        + str(second, "name")
        + "). Combine these flowers from A: "
        + flowers(b, "flowers_a", a, "A")
        + ". Combine these flowers from B: "
        + flowers(b, "flowers_b", second, "B")
        + ". Include the flower types from BOTH references in one arrangement, not only one"
        + " reference. Use "
        + shape
        + ". "
        + presentation
        + " Preserve the original color of each flower species from BOTH reference photographs. Do"
        + " not recolor the flowers or apply paper/ribbon colors to flowers; only rearrange them."
        + " Wrapping and ribbon: "
        + (str(b, "wrapping").isEmpty() ? "use wrapping of reference A" : str(b, "wrapping"))
        + ". When paper is requested according to the finished-product reference, retain paper only"
        + " if visible in that target reference; if absent, do not add it. If no paper is selected,"
        + " omit decorative paper and wrapping. For baskets, bags, boxes and suitcases, paper means"
        + " only inner lining or decorative paper, never external bouquet wrapping. Apply paper"
        + " only where appropriate to the chosen product type; do not replace its container with"
        + " bouquet wrapping. Customer notes: "
        + str(b, "notes")
        + ". Budget intent: "
        + num(b, "budget")
        + " VND, not a price guarantee. Single cohesive arrangement, flower species visibly"
        + " identifiable, realistic florist arrangement, natural light, appropriate background"
        + " matching the target reference for event installations, otherwise a plain neutral"
        + " background, entire arrangement and container fit frame. Do not make a collage or two"
        + " separate arrangements. Remove all text, watermarks, logos, signs and people. Create a"
        + " fresh arrangement for this variation "
        + str(b, "request_id")
        + ". Vary flower placement and balance while preserving the selected flower types, colors,"
        + " paper and ribbon. This is a visual concept, do not add prices or assert exact stem"
        + " counts.";
  }

  void process(String jid) {
    db.exec("UPDATE shop_mix SET status='processing',updated=? WHERE id=?", now(), jid);
    try {
      var r = db.one("SELECT * FROM shop_mix WHERE id=?", jid);
      var recipe = read(str(r, "recipe"));
      var result =
          e.provider.mix(
              cfg,
              List.of(
                  photo(product(str(recipe, "sample_a"))), photo(product(str(recipe, "sample_b")))),
              str(r, "prompt"));
      String name = "mix-" + jid + ".png";
      e.save(name, Images.normalize(result.image(), 20000000, 128, "png", 0));
      db.exec(
          "UPDATE shop_mix SET status='completed',filename=?,usage=?,updated=? WHERE id=?",
          name,
          write(result.usage()),
          now(),
          jid);
    } catch (Exception failure) {
      String message =
          failure instanceof ImageProvider.Failure
              ? failure.getMessage()
              : "Chưa tạo được ảnh hoặc hết thời gian chờ; hãy giữ hai mẫu để gửi tiệm tư vấn.";
      db.exec(
          "UPDATE shop_mix SET status='failed',error=?,updated=? WHERE id=?", message, now(), jid);
      if (failure instanceof ImageProvider.Failure f && !f.billed)
        db.exec("UPDATE shop_mix SET reserved=0 WHERE id=?", jid);
    }
  }

  public List<Map<String, Object>> mixes(String owner) {
    return db
        .all("SELECT * FROM shop_mix WHERE owner=? ORDER BY created DESC LIMIT 30", owner)
        .stream()
        .map(this::publicMix)
        .toList();
  }

  public Map<String, Object> requestBody(Map<String, Object> raw) {
    var b =
        defaults(
            raw,
            map(
                "mode",
                "original",
                "mix_id",
                "",
                "colors",
                map(),
                "need",
                "gift",
                "address",
                "",
                "card_message",
                "",
                "notes",
                "",
                "service_details",
                ""),
            Set.of(
                "request_id",
                "mode",
                "sample_ids",
                "mix_id",
                "colors",
                "need",
                "budget",
                "customer_name",
                "phone",
                "receive_date",
                "address",
                "card_message",
                "notes",
                "service_details"));
    string(b, "request_id", 8, 100);
    choice(b, "mode", "original", "color", "mix");
    var ids = list(b.get("sample_ids"));
    if (ids.isEmpty() || ids.size() > 2 || new HashSet<>(ids).size() != ids.size())
      throw new ApiError(422, "Mẫu tham chiếu bị trùng hoặc thiếu.");
    for (Object id : ids)
      if (!(id instanceof String)) throw new ApiError(422, "Mã mẫu không hợp lệ.");
    string(b, "mix_id", 0, 80);
    obj(b.get("colors"));
    string(b, "need", 0, 40);
    range(b, "budget", 100000, 100000000);
    nonblank(b, "customer_name", 100);
    phone(b);
    date(b, "receive_date", true);
    string(b, "address", 0, 500);
    string(b, "card_message", 0, 500);
    string(b, "notes", 0, 1500);
    string(b, "service_details", 0, 1000);
    return b;
  }

  public synchronized Map<String, Object> createRequest(
      Map<String, Object> raw, byte[] upload, String owner) {
    var b = requestBody(raw);
    var samples = list(b.get("sample_ids")).stream().map(v -> product(v.toString())).toList();
    byte[] image = null;
    Map<String, Object> mix = null;
    if (str(b, "mode").equals("mix")) {
      if (samples.size() != 2
          || samples.stream().anyMatch(p -> !MIX_KINDS.contains(str(p, "kind"))))
        throw new ApiError(
            422, "Kết hợp cần hai mẫu bó, giỏ, túi/hộp/vali, bình, set tự cắm hoặc hoa sự kiện.");
      if (!str(b, "mix_id").isEmpty()) {
        mix = e.owned("shop_mix", str(b, "mix_id"), owner);
        var recipe = read(str(mix, "recipe"));
        if (!str(mix, "status").equals("completed")
            || !list(b.get("sample_ids"))
                .equals(List.of(recipe.get("sample_a"), recipe.get("sample_b"))))
          throw new ApiError(422, "Ảnh kết hợp không khớp hai mẫu đã chọn.");
        if (num(b, "budget") != num(recipe, "budget"))
          throw new ApiError(
              422,
              "Ngân sách đã đổi sau khi tạo ảnh. Hãy tạo phương án mới hoặc gửi yêu cầu không dùng"
                  + " ảnh cũ.");
        image = e.media(str(mix, "filename"));
      }
    } else if (samples.size() != 1) throw new ApiError(422, "Chọn một mẫu cho phương án này.");
    if (str(b, "mode").equals("color")) {
      if (samples.getFirst().get("color_template") == null)
        throw new ApiError(422, "Mẫu này chưa hỗ trợ đổi màu.");
      var colors = obj(b.get("colors"));
      if (!colors.keySet().equals(Set.of("flower"))
          || !Set.of("#e4b7c1", "#eadfcf", "#c5a0c4", "#e7a2a0", "#b92042", "#e956aa")
              .contains(str(colors, "flower")))
        throw new ApiError(422, "Màu lựa chọn không hợp lệ.");
      if (upload == null) throw new ApiError(422, "Cần đính kèm ảnh phối màu.");
      if (upload.length > 12 * 1024 * 1024) throw new ApiError(413, "Ảnh vượt 12 MB.");
      image = Images.normalize(upload, 20000000, 128, "png", 0);
    } else if (upload != null)
      throw new ApiError(422, "Không đính kèm ảnh tự do cho phương án này.");
    var snapshot =
        map(
            "request",
            b,
            "samples",
            samples,
            "mix_recipe",
            mix == null ? null : read(str(mix, "recipe")),
            "image_kind",
            mix != null
                ? "ai_concept"
                : str(b, "mode").equals("color") ? "color_preview" : "reference",
            "shop_confirmation_required",
            true);
    String fingerprint =
        hash(Json.fingerprint(snapshot) + hash(image == null ? new byte[0] : image));
    var old =
        db.one(
            "SELECT * FROM shop_requests WHERE owner=? AND request_id=?",
            owner,
            b.get("request_id"));
    if (old != null) {
      if (!str(old, "fingerprint").equals(fingerprint))
        throw new ApiError(409, "Mã yêu cầu đã dùng cho nội dung khác.");
      return publicRequest(old);
    }
    String id = id(12), filename = null;
    if (image != null) {
      filename = "request-" + id + ".png";
      e.save(filename, image);
    }
    double t = now();
    db.exec(
        "INSERT INTO shop_requests VALUES(?,?,?,?,?,?,?,?,?,?,?)",
        id,
        owner,
        b.get("request_id"),
        fingerprint,
        write(snapshot),
        "pending_shop",
        null,
        0,
        filename,
        t,
        t);
    return publicRequest(e.owned("shop_requests", id, owner));
  }

  public List<Map<String, Object>> requests(String owner) {
    return db
        .all("SELECT * FROM shop_requests WHERE owner=? ORDER BY created DESC LIMIT 50", owner)
        .stream()
        .map(this::publicRequest)
        .toList();
  }

  public List<Map<String, Object>> adminRequests() {
    return db.all("SELECT * FROM shop_requests ORDER BY created DESC LIMIT 100").stream()
        .map(this::publicRequest)
        .toList();
  }

  public synchronized Map<String, Object> accept(String id, Map<String, Object> b, String owner) {
    b = defaults(b, map(), Set.of("revision"));
    range(b, "revision", 1, Integer.MAX_VALUE);
    var r = e.owned("shop_requests", id, owner);
    if (str(r, "status").equals("accepted") && num(r, "revision") == num(b, "revision"))
      return publicRequest(r);
    if (!str(r, "status").equals("quoted") || num(r, "revision") != num(b, "revision"))
      throw new ApiError(409, "Phương án của tiệm đã thay đổi; hãy tải lại trước khi xác nhận.");
    db.exec("UPDATE shop_requests SET status='accepted',updated=? WHERE id=?", now(), id);
    return publicRequest(e.owned("shop_requests", id, owner));
  }

  public synchronized Map<String, Object> quote(String id, Map<String, Object> b) {
    b = defaults(b, map(), Set.of("price_vnd", "message", "revision"));
    range(b, "price_vnd", 10000, 100000000);
    nonblank(b, "message", 2000);
    range(b, "revision", 0, Integer.MAX_VALUE);
    var r = db.one("SELECT * FROM shop_requests WHERE id=?", id);
    if (r == null) throw new ApiError(404, "Không tìm thấy yêu cầu.");
    if (!Set.of("pending_shop", "quoted").contains(str(r, "status"))
        || num(r, "revision") != num(b, "revision"))
      throw new ApiError(409, "Yêu cầu đã đổi trạng thái; hãy tải lại.");
    db.exec(
        "UPDATE shop_requests SET status='quoted',quote=?,revision=revision+1,updated=? WHERE id=?",
        write(b),
        now(),
        id);
    return publicRequest(db.one("SELECT * FROM shop_requests WHERE id=?", id));
  }

  public synchronized Map<String, Object> fulfill(String id, Map<String, Object> b) {
    b = defaults(b, map(), Set.of("status"));
    choice(b, "status", "making", "delivered");
    var r = db.one("SELECT * FROM shop_requests WHERE id=?", id);
    if (r == null) throw new ApiError(404, "Không tìm thấy yêu cầu.");
    if (!str(r, "status").equals(str(b, "status").equals("making") ? "accepted" : "making"))
      throw new ApiError(409, "Chưa đủ bước xác nhận để chuyển trạng thái.");
    db.exec("UPDATE shop_requests SET status=?,updated=? WHERE id=?", b.get("status"), now(), id);
    return map("status", b.get("status"));
  }

  public Map<String, Object> updateProduct(String id, Map<String, Object> raw) {
    product(id);
    var b =
        defaults(
            raw,
            map(
                "price_vnd",
                null,
                "ingredients",
                List.of(),
                "needs",
                List.of(),
                "ingredients_verified",
                false,
                "approved",
                false),
            Set.of(
                "name",
                "kind",
                "price_vnd",
                "ingredients",
                "needs",
                "ingredients_verified",
                "approved"));
    nonblank(b, "name", 160);
    choice(
        b,
        "kind",
        "bouquet",
        "basket",
        "vase",
        "gift-box",
        "stand",
        "cut-set",
        "orchid-pot",
        "subscription",
        "wedding",
        "event",
        "accessory",
        "other");
    if (b.get("price_vnd") != null) range(b, "price_vnd", 10000, 100000000);
    var ingredients = list(b.get("ingredients"));
    if (ingredients.size() > 20
        || ingredients.stream()
            .anyMatch(x -> !(x instanceof String s) || s.isBlank() || s.length() > 120))
      throw new ApiError(422, "Tên nguyên liệu không hợp lệ.");
    var needs = list(b.get("needs"));
    if (needs.size() > 6
        || needs.stream()
            .anyMatch(
                x ->
                    !Set.of("gift", "celebration", "home", "office", "wedding", "event")
                        .contains(x))) throw new ApiError(422, "Nhu cầu không hợp lệ.");
    for (String key : List.of("ingredients_verified", "approved"))
      if (!(b.get(key) instanceof Boolean))
        throw new ApiError(422, "Thông tin duyệt không hợp lệ.");
    db.exec("INSERT OR REPLACE INTO shop_products VALUES(?,?)", id, write(b));
    return product(id);
  }
}
