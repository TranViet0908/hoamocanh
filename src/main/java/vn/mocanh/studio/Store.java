package vn.mocanh.studio;

import java.sql.*;
import java.util.*;

public class Store implements AutoCloseable {
  private final Connection connection;

  public Store(Settings cfg) {
    try {
      connection = DriverManager.getConnection("jdbc:sqlite:" + cfg.data.resolve("state.sqlite3"));
      exec("PRAGMA journal_mode=WAL");
      exec("PRAGMA busy_timeout=5000");
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  public synchronized int exec(String sql, Object... args) {
    try (PreparedStatement p = connection.prepareStatement(sql)) {
      bind(p, args);
      p.execute();
      return p.getUpdateCount();
    } catch (SQLException e) {
      throw new IllegalStateException("Database operation failed", e);
    }
  }

  public synchronized List<Map<String, Object>> all(String sql, Object... args) {
    try (PreparedStatement p = connection.prepareStatement(sql)) {
      bind(p, args);
      try (ResultSet r = p.executeQuery()) {
        List<Map<String, Object>> out = new ArrayList<>();
        ResultSetMetaData md = r.getMetaData();
        while (r.next()) {
          Map<String, Object> m = new LinkedHashMap<>();
          for (int i = 1; i <= md.getColumnCount(); i++)
            m.put(md.getColumnLabel(i), r.getObject(i));
          out.add(m);
        }
        return out;
      }
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  public Map<String, Object> one(String sql, Object... args) {
    var l = all(sql, args);
    return l.isEmpty() ? null : l.getFirst();
  }

  private void bind(PreparedStatement p, Object[] args) throws SQLException {
    for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
  }

  public synchronized void close() {
    try {
      connection.close();
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }
}
