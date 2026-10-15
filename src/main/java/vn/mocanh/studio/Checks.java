package vn.mocanh.studio;

import java.time.*;
import java.util.*;

public final class Checks {
  public static Map<String, Object> defaults(
      Map<String, Object> raw, Map<String, Object> defaults, Set<String> fields) {
    Map<String, Object> b = new LinkedHashMap<>(defaults);
    for (String k : raw.keySet())
      if (!fields.contains(k)) throw new ApiError(422, "Trường không hợp lệ: " + k);
    b.putAll(raw);
    return b;
  }

  public static void string(Map<String, Object> b, String k, int min, int max) {
    Object v = b.get(k);
    if (!(v instanceof String s) || s.length() < min || s.length() > max)
      throw new ApiError(422, "Thông tin không hợp lệ: " + k);
  }

  public static void nonblank(Map<String, Object> b, String k, int max) {
    string(b, k, 1, max);
    if (Json.str(b, k).isBlank()) throw new ApiError(422, "Thông tin chưa đủ: " + k);
  }

  public static void range(Map<String, Object> b, String k, long min, long max) {
    long n = Json.num(b, k);
    if (!(b.get(k) instanceof Number value)
        || !Double.isFinite(value.doubleValue())
        || value.doubleValue() != n
        || n < min
        || n > max) throw new ApiError(422, "Giá trị không hợp lệ: " + k);
  }

  public static void decimal(Map<String, Object> b, String k, double min, double max) {
    double n = Json.dec(b, k);
    if (!Double.isFinite(n) || n < min || n > max)
      throw new ApiError(422, "Giá trị không hợp lệ: " + k);
  }

  public static void choice(Map<String, Object> b, String k, String... values) {
    if (!Arrays.asList(values).contains(Json.str(b, k)))
      throw new ApiError(422, "Lựa chọn không hợp lệ: " + k);
  }

  public static void phone(Map<String, Object> b) {
    string(b, "phone", 8, 25);
    if (!Json.str(b, "phone").matches("[+0-9 ()-]+"))
      throw new ApiError(422, "Số điện thoại không hợp lệ.");
  }

  public static void date(Map<String, Object> b, String k, boolean future) {
    try {
      LocalDate d = LocalDate.parse(Json.str(b, k));
      if (future && d.isBefore(LocalDate.now())) throw new IllegalArgumentException();
    } catch (Exception e) {
      throw new ApiError(422, "Ngày nhận cần từ hôm nay trở đi.");
    }
  }
}
