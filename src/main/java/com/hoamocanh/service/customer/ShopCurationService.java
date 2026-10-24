package com.hoamocanh.service.customer;

import com.hoamocanh.core.entity.OrderItem;
import com.hoamocanh.core.entity.SystemConfig;
import com.hoamocanh.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Danh sách mẫu nổi bật (bán chạy, theo dịp) và chương trình Flash sale do quản trị viên chỉnh ở trang quản trị.
 * Toàn bộ lưu thành một dòng văn bản đơn giản (key=value) trong system_configs, khóa shop.curation, không cần thêm bảng
 * và không phụ thuộc thư viện JSON (Spring Boot 4 dùng Jackson 3, đổi tên gói).
 * Flash sale: mỗi ngày trong khung giờ [start, end) các mẫu trong flashIds được giảm percent% (làm tròn xuống 1.000đ).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopCurationService {

    public static final String KEY = "shop.curation";
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> OCC = Set.of("birthday", "partner", "lover", "women", "teacher", "wedding",
            "vase", "funeral", "orchid", "xmas", "tet");
    private static final Set<String> TONES = Set.of("pink", "white", "purple", "red", "yellow", "mixed");
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_.\\-]{1,60}");
    private static final int MAX_PER_LIST = 100;

    public record Flash(boolean enabled, int percent, String start, String end) { }
    public record Lists(List<String> bestseller, Map<String, List<String>> occasions, List<String> flashIds, Map<String, List<String>> tones) { }
    public record State(List<String> bestseller, Map<String, List<String>> occasions, List<String> flashIds, Flash flash, Map<String, List<String>> tones) { }

    private final SystemConfigRepository repository;

    private State empty() {
        return new State(List.of(), Map.of(), List.of(), new Flash(false, 10, "09:00", "12:00"), Map.of());
    }

    @Transactional(readOnly = true)
    public State get() {
        return repository.findById(KEY).map(c -> {
            try {
                State s = parse(c.getConfigValue());
                return new State(s.bestseller() == null ? List.of() : s.bestseller(),
                        s.occasions() == null ? Map.<String, List<String>>of() : s.occasions(),
                        s.flashIds() == null ? List.of() : s.flashIds(),
                        s.flash() == null ? empty().flash() : s.flash(),
                        s.tones() == null ? Map.<String, List<String>>of() : s.tones());
            } catch (Exception e) {
                log.warn("Không đọc được {}: {}", KEY, e.getMessage());
                return empty();
            }
        }).orElseGet(this::empty);
    }

    @Transactional
    public State saveLists(Lists l) {
        State cur = get();
        Map<String, List<String>> occ = new LinkedHashMap<>();
        if (l.occasions() != null) l.occasions().forEach((k, v) -> {
            if (!OCC.contains(k)) throw new IllegalArgumentException("Dịp không hợp lệ: " + k);
            occ.put(k, clean(v));
        });
        Map<String, List<String>> tones = new LinkedHashMap<>();
        if (l.tones() == null) tones.putAll(cur.tones());
        else l.tones().forEach((k, v) -> {
            if (!TONES.contains(k)) throw new IllegalArgumentException("Tone màu không hợp lệ: " + k);
            tones.put(k, clean(v, 1000));   // tone màu có thể gắn cho cả trăm mẫu nên giới hạn rộng hơn
        });
        State n = new State(clean(l.bestseller()), occ, clean(l.flashIds()), cur.flash(), tones);
        write(n);
        return n;
    }

    @Transactional
    public State saveFlash(Flash f) {
        if (f == null || f.percent() < 1 || f.percent() > 70)
            throw new IllegalArgumentException("Mức giảm Flash sale từ 1% đến 70%.");
        LocalTime s, e;
        try { s = LocalTime.parse(f.start()); e = LocalTime.parse(f.end()); }
        catch (Exception ex) { throw new IllegalArgumentException("Giờ bắt đầu và kết thúc chưa đúng định dạng HH:mm."); }
        if (!e.isAfter(s)) throw new IllegalArgumentException("Giờ kết thúc phải sau giờ bắt đầu trong cùng một ngày.");
        State cur = get();
        State n = new State(cur.bestseller(), cur.occasions(), cur.flashIds(), new Flash(f.enabled(), f.percent(), s.toString(), e.toString()), cur.tones());
        write(n);
        return n;
    }

    private static List<String> clean(List<String> in) {
        return clean(in, MAX_PER_LIST);
    }

    private static List<String> clean(List<String> in, int max) {
        if (in == null) return List.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String s : in) {
            if (s == null || !ID.matcher(s).matches()) throw new IllegalArgumentException("Mã mẫu không hợp lệ.");
            out.add(s);
        }
        if (out.size() > max) throw new IllegalArgumentException("Mỗi danh sách tối đa " + max + " mẫu.");
        return new ArrayList<>(out);
    }

    private void write(State s) {
        SystemConfig c = repository.findById(KEY).orElseGet(() -> SystemConfig.builder().configKey(KEY)
                .description("Mẫu nổi bật theo mục và chương trình Flash sale").build());
        c.setConfigValue(serialize(s));
        repository.save(c);
    }

    // Định dạng lưu: mỗi dòng key=value. Mã mẫu chỉ gồm chữ, số, _ . - nên dấu phẩy và xuống dòng là an toàn.
    //   flash=1|10|09:00|12:00   (bật|phần trăm|giờ bắt đầu|giờ kết thúc)
    //   bestseller=ma-a,ma-b     flashIds=ma-c     occ.birthday=ma-d,ma-e
    private static String serialize(State s) {
        StringBuilder b = new StringBuilder();
        b.append("flash=").append(s.flash().enabled() ? "1" : "0").append('|').append(s.flash().percent())
                .append('|').append(s.flash().start()).append('|').append(s.flash().end()).append('\n');
        b.append("bestseller=").append(String.join(",", s.bestseller())).append('\n');
        b.append("flashIds=").append(String.join(",", s.flashIds())).append('\n');
        s.occasions().forEach((k, v) -> b.append("occ.").append(k).append('=').append(String.join(",", v)).append('\n'));
        s.tones().forEach((k, v) -> b.append("tone.").append(k).append('=').append(String.join(",", v)).append('\n'));
        return b.toString();
    }

    private State parse(String text) {
        List<String> best = List.of(), fids = List.of();
        Map<String, List<String>> occ = new LinkedHashMap<>(), tones = new LinkedHashMap<>();
        Flash flash = empty().flash();
        for (String line : text.split("\\R")) {
            int i = line.indexOf('=');
            if (i < 1) continue;
            String k = line.substring(0, i).trim(), v = line.substring(i + 1).trim();
            if (k.equals("bestseller")) best = ids(v);
            else if (k.equals("flashIds")) fids = ids(v);
            else if (k.startsWith("occ.")) occ.put(k.substring(4), ids(v));
            else if (k.startsWith("tone.")) tones.put(k.substring(5), ids(v));
            else if (k.equals("flash")) {
                String[] f = v.split("\\|");
                flash = new Flash("1".equals(f[0]), Integer.parseInt(f[1]), f[2], f[3]);
            }
        }
        return new State(best, occ, fids, flash, tones);
    }

    private static List<String> ids(String v) {
        return v.isEmpty() ? List.of() : Arrays.stream(v.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    /** Flash sale đang diễn ra lúc này không: bật, trong khung giờ hằng ngày. */
    public boolean active(Flash f) {
        if (f == null || !f.enabled()) return false;
        try {
            LocalTime now = LocalTime.now(ZONE);
            return !now.isBefore(LocalTime.parse(f.start())) && now.isBefore(LocalTime.parse(f.end()));
        } catch (Exception e) { return false; }
    }

    /** Dữ liệu công khai cho trang shop. */
    @Transactional(readOnly = true)
    public Map<String, Object> publicView() {
        State s = get();
        Map<String, Object> flash = new LinkedHashMap<>();
        flash.put("enabled", s.flash().enabled());
        flash.put("active", active(s.flash()));
        flash.put("percent", s.flash().percent());
        flash.put("start", s.flash().start());
        flash.put("end", s.flash().end());
        flash.put("ids", s.flash().enabled() ? s.flashIds() : List.of());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("bestseller", s.bestseller());
        out.put("occasions", s.occasions());
        out.put("tones", s.tones());
        out.put("flash", flash);
        return out;
    }

    /** Số tiền giảm Flash sale cho các dòng của đơn (0 nếu không diễn ra). Server tính lại, không tin giao diện. */
    @Transactional(readOnly = true)
    public BigDecimal flashDiscount(List<OrderItem> lines) {
        State s = get();
        if (!active(s.flash()) || s.flashIds().isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (OrderItem i : lines)
            if (s.flashIds().contains(i.getSampleId())) sum = sum.add(i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())));
        BigDecimal off = sum.multiply(BigDecimal.valueOf(s.flash().percent())).divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
        return off.divide(BigDecimal.valueOf(1000), 0, RoundingMode.DOWN).multiply(BigDecimal.valueOf(1000));
    }
}