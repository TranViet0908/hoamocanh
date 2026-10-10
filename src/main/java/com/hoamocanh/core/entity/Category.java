package com.hoamocanh.core.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    // MỚI: nhóm định mức của danh mục (MAIN, SUB, LEAF, PACKAGING, ACCESSORY, OTHER)
    @Enumerated(EnumType.STRING)
    @Column(name = "design_group", length = 20)
    private DesignGroup designGroup;

    @Column(name = "is_deleted", columnDefinition = "tinyint(1) default 0")
    private Boolean isDeleted;
}