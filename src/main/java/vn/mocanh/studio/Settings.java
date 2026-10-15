package vn.mocanh.studio;

import java.nio.file.*;
import java.util.*;

public class Settings {
  public final Path root, data, media;
  final Map<String, String> values = new HashMap<>();

  public Settings() {
    this(Path.of(System.getProperty("mocanh.root", ".")), Map.of());
  }

  public Settings(Path root, Map<String, String> overrides) {
    this.root = root.toAbsolutePath().normalize();
    Path env = this.root.resolve(".env");
    if (Files.isRegularFile(env))
      try {
        for (String line : Files.readAllLines(env)) {
          String s = line.trim();
          if (s.isEmpty() || s.startsWith("#")) continue;
          int eq = s.indexOf('=');
          if (eq < 1) continue;
          String v = s.substring(eq + 1).trim();
          if (v.length() > 1
              && ((v.startsWith("\"") && v.endsWith("\""))
                  || (v.startsWith("'") && v.endsWith("'")))) v = v.substring(1, v.length() - 1);
          values.put(s.substring(0, eq).trim(), v);
        }
      } catch (Exception e) {
        throw new IllegalStateException("Could not read .env");
      }
    values.putAll(System.getenv());
    values.putAll(overrides);
    if (testUi() && integration().isBlank()) {
      String localKey = Json.token(24);
      values.put("INTEGRATION_API_KEY", localKey);
      try {
        Files.writeString(
            env,
            "\nINTEGRATION_API_KEY=" + localKey + "\n",
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND);
      } catch (Exception e) {
        throw new IllegalStateException("Could not save local integration key");
      }
    }
    data = this.root.resolve(get("DATA_DIR", "data")).normalize();
    media = data.resolve("media");
    try {
      Files.createDirectories(media);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public String get(String k, String d) {
    return values.getOrDefault(k, d);
  }

  public double number(String k, double d) {
    return Double.parseDouble(get(k, "" + d));
  }

  public int integer(String k, int d) {
    return (int) number(k, d);
  }

  public boolean testUi() {
    return Boolean.parseBoolean(get("ENABLE_TEST_UI", "true"));
  }

  public String key() {
    return get("OPENAI_API_KEY", "");
  }

  public String integration() {
    return get("INTEGRATION_API_KEY", "");
  }

  public String model() {
    return get("IMAGE_MODEL", "gpt-image-2");
  }

  public String provider() {
    return get("IMAGE_PROVIDER", "browser");
  }

  public double cost() {
    return Math.max(0, number("ESTIMATED_IMAGE_COST_USD", .05));
  }

  public double budget() {
    return Math.max(0, number("DAILY_BUDGET_USD", 5));
  }

  public String baseUrl() {
    return get("PUBLIC_BASE_URL", "http://localhost:8005").replaceAll("/$", "");
  }
}
