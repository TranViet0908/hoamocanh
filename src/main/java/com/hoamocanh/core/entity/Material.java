package com.hoamocanh.core.entity;

import com.hoamocanh.core.entity.enums.MaterialStatus;
import com.hoamocanh.core.entity.enums.MaterialType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "materials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Material {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MaterialType type;

    @Column(name = "supported_product_types", nullable = false, length = 50)
    private String supportedProductTypes;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private MaterialStatus status = MaterialStatus.AVAILABLE;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(length = 50)
    private String color;

    @Version
    @Builder.Default
    private Integer version = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}