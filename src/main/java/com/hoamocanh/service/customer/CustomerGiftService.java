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
@Transactional(readOnly = true)
public class CustomerGiftService {

    // Khớp token sinh trong CustomerOrderService: 16 ký tự hex
    private static final Pattern TOKEN = Pattern.compile("^[a-f0-9]{16}$");

    private final GiftOrderRepository giftOrderRepository;

    public GiftRes getGift(String code) {
        if (code == null || !TOKEN.matcher(code).matches()) {
            throw new IllegalArgumentException("Đường link quà tặng không hợp lệ.");
        }

        Order o = giftOrderRepository.findByQrCodeUrlEndingWith("code=" + code)
                .filter(x -> "ONLINE".equals(x.getDeliveryType()) && !"CANCELLED".equals(x.getStatus()))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy món quà này hoặc quà đã bị hủy."));

        List<GiftRes.Item> items = o.getOrderDetails().stream()
                .map(d -> new GiftRes.Item(d.getMaterial().getName(), d.getQuantity(), d.getMaterial().getImageUrl()))
                .toList();

        return new GiftRes(
                "MA-" + o.getId(),
                o.getReceiverName(),   // với đơn gửi tặng online đây là tên người đặt (người gửi quà)
                o.getGiftMessage(),
                o.getGiftImageUrl(),
                o.getGiftVideoUrl(),
                o.getProductType() != null ? o.getProductType().getName() : null,
                o.getStyle(),
                o.getThemeColor(),
                items);
    }
}