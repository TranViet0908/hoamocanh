package com.hoamocanh.core.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(nullable = false, length = 15, unique = true)
    private String phone;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(length = 100)
    private String email;

    @Column(name = "is_deleted", columnDefinition = "tinyint(1) default 0")
    private Boolean isDeleted;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}