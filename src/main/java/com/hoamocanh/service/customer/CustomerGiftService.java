package com.hoamocanh.service.customer;

import com.hoamocanh.core.entity.Order;
import com.hoamocanh.dto.customer.GiftRes;
import com.hoamocanh.repository.GiftOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CustomerGiftService {

    private static final Pattern TOKEN = Pattern.compile("^[a-f0-9]{16}$");
    private final GiftOrderRepository giftOrderRepository;

    @Transactional(readOnly = true)
    public GiftRes getGift(String code) {
        if (code == null || !TOKEN.matcher(code).matches()) {
            throw new IllegalArgumentException("Đường link quà tặng không hợp lệ.");
        }

        Order o = giftOrderRepository.findByQrCodeUrlEndingWith("code=" + code)
                .filter(x -> "ONLINE".equals(x.getDeliveryType()) && !"CANCELLED".equals(x.getStatus()))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy món quà này hoặc quà đã bị hủy."));

        // Mở thẳng quà tặng theo token trong URL (16 ký tự hex ngẫu nhiên), không cần mật khẩu mở quà
        return buildGiftRes(o);
    }

    private GiftRes buildGiftRes(Order o) {
        List<GiftRes.Item> items = o.getOrderDetails().stream()
                .map(d -> new GiftRes.Item(d.getMaterial().getName(), d.getQuantity(), d.getMaterial().getImageUrl()))
                .toList();

        return new GiftRes(
                "MA-" + o.getId(),
                o.getReceiverName(), o.getGiftMessage(), o.getGiftImageUrl(), o.getGiftVideoUrl(),
                o.getBouquetImageUrl(),
                o.getProductType() != null ? o.getProductType().getName() : null,
                o.getStyle(), o.getThemeColor(), items
        );
    }
}