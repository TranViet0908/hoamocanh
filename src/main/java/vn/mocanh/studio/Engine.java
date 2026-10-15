package vn.mocanh.studio;

import static vn.mocanh.studio.Json.*;

import jakarta.annotation.PreDestroy;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;

@Component
public class Engine {
  public final Settings cfg;
  public final Store db;
  public final ImageProvider provider;
  public final ShopService shop;
  public final LegacyService legacy;
  public final ExecutorService mixes, generations;

  public Engine() {
    this(new Settings(), new ImageProvider());
  }

  public Engine(Settings cfg, ImageProvider provider) {
    this.cfg = cfg;
    this.provider = provider;
    db = new Store(cfg);
    schema();
    int workers = Math.max(1, cfg.integer("WORKER_COUNT", 1));
    mixes = Executors.newFixedThreadPool(workers);
    generations = Executors.newFixedThreadPool(workers);
    shop = new ShopService(this);
    legacy = new LegacyService(this);
  }

  void schema() {
    for (String sql :
        List.of(
            "CREATE TABLE IF NOT EXISTS sessions(token TEXT PRIMARY KEY,created REAL,last_submit"
                + " REAL DEFAULT 0)",
            "CREATE TABLE IF NOT EXISTS jobs(id TEXT PRIMARY KEY,owner TEXT,request_id"
                + " TEXT,fingerprint TEXT,design TEXT,prompt TEXT,variant TEXT,status TEXT,created"
                + " REAL,updated REAL,filename TEXT,error TEXT,provider TEXT,model TEXT,usage"
                + " TEXT,estimate REAL DEFAULT 0,reserved REAL DEFAULT 0,UNIQUE(owner,request_id))",
            "CREATE INDEX IF NOT EXISTS cache_lookup ON jobs(owner,fingerprint,status)",
            "CREATE TABLE IF NOT EXISTS uploads(id TEXT PRIMARY KEY,owner TEXT,filename TEXT,mime"
                + " TEXT,created REAL)",
            "CREATE TABLE IF NOT EXISTS gifts(token TEXT PRIMARY KEY,owner TEXT,job_id"
                + " TEXT,recipient TEXT,sender TEXT,message TEXT,media_ids TEXT,created"
                + " REAL,expires REAL,revoked INTEGER DEFAULT 0)",
            "CREATE TABLE IF NOT EXISTS shop_mix(id TEXT PRIMARY KEY,owner TEXT,request_id"
                + " TEXT,fingerprint TEXT,recipe TEXT,prompt TEXT,status TEXT,filename TEXT,error"
                + " TEXT,usage TEXT,reserved REAL,created REAL,updated"
                + " REAL,UNIQUE(owner,request_id))",
            "CREATE TABLE IF NOT EXISTS shop_requests(id TEXT PRIMARY KEY,owner TEXT,request_id"
                + " TEXT,fingerprint TEXT,snapshot TEXT,status TEXT,quote TEXT,revision"
                + " INTEGER,filename TEXT,created REAL,updated REAL,UNIQUE(owner,request_id))",
            "CREATE TABLE IF NOT EXISTS shop_products(id TEXT PRIMARY KEY,metadata TEXT)",
            "CREATE TABLE IF NOT EXISTS inventory(id TEXT PRIMARY KEY,stock INTEGER,price"
                + " INTEGER,photo TEXT)",
            "CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY,value TEXT)",
            "CREATE TABLE IF NOT EXISTS browser_events(id INTEGER PRIMARY KEY,owner TEXT,action"
                + " TEXT,status TEXT,elapsed_ms REAL,downloaded_bytes INTEGER,error_code"
                + " TEXT,created REAL)",
            "CREATE TABLE IF NOT EXISTS quotes(id TEXT PRIMARY KEY,owner TEXT,job_id TEXT,mode"
                + " TEXT,snapshot TEXT,created REAL,expires REAL)",
            "CREATE TABLE IF NOT EXISTS reservations(id TEXT PRIMARY KEY,owner TEXT,request_id"
                + " TEXT,quote_id TEXT,snapshot TEXT,customer_name TEXT,phone TEXT,appointment"
                + " TEXT,expires REAL,status TEXT,created REAL,UNIQUE(owner,request_id))",
            "CREATE TABLE IF NOT EXISTS consultations(id TEXT PRIMARY KEY,owner TEXT,request_id"
                + " TEXT,fingerprint TEXT,snapshot TEXT,created REAL,UNIQUE(owner,request_id))"))
      db.exec(sql);
    db.exec(
        "UPDATE shop_mix SET status='failed',error='Máy chủ đã khởi động lại; không tự sinh lại ảnh"
            + " để tránh tính phí trùng.' WHERE status IN ('queued','processing')");
    db.exec(
        "UPDATE jobs SET status='failed',error='Tác vụ bị gián đoạn bởi lần khởi động trước. Hãy"
            + " chủ động thử lại.' WHERE status='processing'");
  }

  public String identity(String session, String integration, String customer) {
    if (!cfg.integration().isBlank() && equal(integration, cfg.integration())) {
      if (customer.isBlank() || customer.length() > 100)
        throw new ApiError(400, "Backend chính cần gửi X-Customer-Id ổn định.");
      String owner = "integration:" + hash(customer);
      db.exec("INSERT OR IGNORE INTO sessions(token,created) VALUES(?,?)", owner, now());
      return owner;
    }
    if (!cfg.testUi()) throw new ApiError(401, "Cần khóa tích hợp backend.");
    if (session.isBlank() || db.one("SELECT token FROM sessions WHERE token=?", session) == null)
      throw new ApiError(401, "Phiên test không hợp lệ. Hãy tải lại trang.");
    return session;
  }

  static boolean equal(String a, String b) {
    return MessageDigest.isEqual(
        a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
        b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  public void admin(String key) {
    if (cfg.integration().isBlank() || !equal(key, cfg.integration()))
      throw new ApiError(401, "Khóa quản trị không hợp lệ.");
  }

  public Map<String, Object> owned(String table, String id, String owner) {
    if (!Set.of("shop_mix", "shop_requests", "jobs", "uploads", "quotes", "reservations")
        .contains(table)) throw new IllegalArgumentException();
    var r = db.one("SELECT * FROM " + table + " WHERE id=? AND owner=?", id, owner);
    if (r == null) throw new ApiError(404, "Không tìm thấy yêu cầu trong phiên này.");
    return r;
  }

  public Map<String, Object> session() {
    if (!cfg.testUi()) throw new ApiError(404, "Not Found");
    String t = token(32);
    db.exec("INSERT INTO sessions(token,created) VALUES(?,?)", t, now());
    return map("token", t);
  }

  public byte[] media(String name) {
    try {
      Path p = cfg.media.resolve(name).normalize();
      if (!p.startsWith(cfg.media) || name.isBlank()) throw new ApiError(404, "Ảnh chưa sẵn sàng.");
      return Files.readAllBytes(p);
    } catch (java.io.IOException e) {
      throw new ApiError(404, "Ảnh chưa sẵn sàng.");
    }
  }

  public void save(String name, byte[] data) {
    try {
      Files.write(cfg.media.resolve(name), data);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public long storage() {
    try (var files = Files.list(cfg.media)) {
      return files
          .filter(Files::isRegularFile)
          .mapToLong(
              p -> {
                try {
                  return Files.size(p);
                } catch (Exception e) {
                  return 0;
                }
              })
          .sum();
    } catch (Exception e) {
      return 0;
    }
  }

  @PreDestroy
  public void close() {
    mixes.shutdownNow();
    generations.shutdownNow();
    try {
      mixes.awaitTermination(10, TimeUnit.SECONDS);
      generations.awaitTermination(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    db.exec(
        "UPDATE shop_mix SET status='failed',error='Máy chủ dừng khi đang tạo ảnh; không tự thử"
            + " lại.' WHERE status IN ('queued','processing')");
    db.close();
  }
}
