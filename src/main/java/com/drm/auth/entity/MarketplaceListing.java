package com.drm.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "marketplace_listings")
public class MarketplaceListing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "nft_id", nullable = false)
    private Integer nftId;

    @Column(name = "seller_username", nullable = false)
    private String sellerUsername;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal price;

    // FIXED hoặc AUCTION
    @Enumerated(EnumType.STRING)
    @Column(name = "listing_type", nullable = false)
    private ListingType listingType;

    // ACTIVE, SOLD, CANCELLED
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ListingStatus status = ListingStatus.ACTIVE;

    @Column(name = "current_high_bid", precision = 18, scale = 8)
    private BigDecimal currentHighBid;

    @Column(name = "highest_bidder_username")
    private String highestBidderUsername;

    // Chỉ dùng cho auction
    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sold_at")
    private LocalDateTime soldAt;

    @Column(name = "buyer_username")
    private String buyerUsername;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum ListingType {
        FIXED, AUCTION
    }

    public enum ListingStatus {
        ACTIVE, SOLD, CANCELLED
    }
}
