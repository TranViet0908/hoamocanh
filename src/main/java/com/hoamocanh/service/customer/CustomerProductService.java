package com.hoamocanh.service.customer;

import com.hoamocanh.core.entity.ProductType;
import com.hoamocanh.repository.ProductTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerProductService {

    private final ProductTypeRepository productTypeRepository;

    /**
     * API Load danh sách 6 form dáng sản phẩm cho màn hình Home
     */
    public List<ProductType> getAllActiveProductTypes() {
        return productTypeRepository.findAll();
    }

    /**
     * Lấy cấu hình chi tiết để Validate khi khách hàng bắt đầu chọn ngân sách
     */
    public ProductType getProductTypeById(Integer id) {
        return productTypeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Loại sản phẩm không tồn tại."));
    }
}