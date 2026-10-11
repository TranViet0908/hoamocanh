package com.hoamocanh.service.admin;

import com.hoamocanh.core.entity.SystemConfig;
import com.hoamocanh.dto.admin.ShopSettings;
import com.hoamocanh.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Thông tin tiệm lưu trong bảng system_configs. Chưa có trong DB thì dùng giá trị shop.* của application.properties. */
@Service
@RequiredArgsConstructor
public class ShopSettingsService {

    private static final String HOTLINE = "shop.hotline", ZALO = "shop.zalo", BANK_CODE = "shop.bank-code",
            BANK_ACCOUNT = "shop.bank-account", BANK_HOLDER = "shop.bank-holder";

    private final SystemConfigRepository repository;

    @Value("${shop.hotline:}") private String defHotline;
    @Value("${shop.zalo:}") private String defZalo;
    @Value("${shop.bank-code:}") private String defBankCode;
    @Value("${shop.bank-account:}") private String defBankAccount;
    @Value("${shop.bank-holder:}") private String defBankHolder;

    @Transactional(readOnly = true)
    public ShopSettings get() {
        Map<String, String> db = repository.findAllById(List.of(HOTLINE, ZALO, BANK_CODE, BANK_ACCOUNT, BANK_HOLDER))
                .stream().collect(Collectors.toMap(SystemConfig::getConfigKey, SystemConfig::getConfigValue));
        return new ShopSettings(
                db.getOrDefault(HOTLINE, nz(defHotline)), db.getOrDefault(ZALO, nz(defZalo)),
                db.getOrDefault(BANK_CODE, nz(defBankCode)), db.getOrDefault(BANK_ACCOUNT, nz(defBankAccount)),
                db.getOrDefault(BANK_HOLDER, nz(defBankHolder)));
    }

    @Transactional
    public ShopSettings save(ShopSettings s) {
        put(HOTLINE, s.hotline(), "Hotline của tiệm");
        put(ZALO, s.zalo(), "Số Zalo của tiệm");
        put(BANK_CODE, s.bankCode(), "Mã ngân hàng nhận chuyển khoản");
        put(BANK_ACCOUNT, s.bankAccount(), "Số tài khoản nhận chuyển khoản");
        put(BANK_HOLDER, s.bankHolder(), "Chủ tài khoản nhận chuyển khoản");
        return get();
    }

    private void put(String key, String value, String description) {
        SystemConfig c = repository.findById(key)
                .orElseGet(() -> SystemConfig.builder().configKey(key).description(description).build());
        c.setConfigValue(nz(value).trim());
        repository.save(c);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
