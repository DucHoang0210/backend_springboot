package com.drm.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "nfts")
public class Nft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", nullable = false, columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "owner_address", nullable = false)
    private String ownerAddress;

    @Column(name = "creator_username")
    private String creatorUsername;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(precision = 18, scale = 8)
    private java.math.BigDecimal price;

    @Column(name = "is_listed")
    @Builder.Default
    private Boolean isListed = false;

    @Column(name = "listing_type")
    private String listingType; // FIXED hoặc AUCTION

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
