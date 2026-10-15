package vn.mocanh.studio;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

public final class Json {
  static final JsonMapper MAPPER = JsonMapper.builder().build();

  public static Map<String, Object> map(Object... pairs) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) m.put((String) pairs[i], pairs[i + 1]);
    return m;
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> obj(Object value) {
    if (value instanceof Map) return (Map<String, Object>) value;
    throw new ApiError(422, "Thông tin chưa hợp lệ.");
  }

  @SuppressWarnings("unchecked")
  public static List<Object> list(Object value) {
    if (value instanceof List) return (List<Object>) value;
    throw new ApiError(422, "Danh sách chưa hợp lệ.");
  }

  public static String str(Map<String, Object> m, String k) {
    return Objects.toString(m.get(k), "");
  }

  public static long num(Map<String, Object> m, String k) {
    Object v = m.get(k);
    if (v instanceof Number n) return n.longValue();
    try {
      return Long.parseLong(Objects.toString(v, "0"));
    } catch (Exception e) {
      throw new ApiError(422, "Số không hợp lệ: " + k);
    }
  }

  public static double dec(Map<String, Object> m, String k) {
    Object v = m.get(k);
    return v instanceof Number n ? n.doubleValue() : Double.parseDouble(Objects.toString(v, "0"));
  }

  public static boolean bool(Map<String, Object> m, String k) {
    return Boolean.TRUE.equals(m.get(k));
  }

  public static String write(Object o) {
    return MAPPER.writeValueAsString(o);
  }

  public static Map<String, Object> read(String s) {
    try {
      return obj(MAPPER.readValue(s, Object.class));
    } catch (ApiError e) {
      throw e;
    } catch (Exception e) {
      throw new ApiError(422, "JSON chưa hợp lệ.");
    }
  }

  public static Object any(String s) {
    return MAPPER.readValue(s, Object.class);
  }

  public static Map<String, Object> file(Path p) {
    try {
      return read(Files.readString(p));
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Missing catalog: " + p);
    }
  }

  public static String hash(String s) {
    return hash(s.getBytes(StandardCharsets.UTF_8));
  }

  public static String hash(byte[] b) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static final SecureRandom RANDOM = new SecureRandom();

  public static String id(int bytes) {
    byte[] b = new byte[bytes];
    RANDOM.nextBytes(b);
    return HexFormat.of().formatHex(b);
  }

  public static String token(int bytes) {
    byte[] b = new byte[bytes];
    RANDOM.nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  public static double now() {
    return System.currentTimeMillis() / 1000.0;
  }

  public static String fingerprint(Object value) {
    return hash(write(sorted(value)));
  }

  private static Object sorted(Object value) {
    if (value instanceof Map<?, ?> m) {
      Map<String, Object> n = new TreeMap<>();
      m.forEach((k, v) -> n.put(k.toString(), sorted(v)));
      return n;
    }
    if (value instanceof List<?> l) return l.stream().map(Json::sorted).toList();
    return value;
  }
}
