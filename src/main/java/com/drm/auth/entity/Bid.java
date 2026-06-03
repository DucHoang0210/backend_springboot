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
@Table(name = "bids")
public class Bid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "listing_id", nullable = false)
    private Integer listingId;

    @Column(name = "bidder_username", nullable = false)
    private String bidderUsername;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal amount;

    @Column(name = "bid_at", updatable = false)
    private LocalDateTime bidAt;

    @PrePersist
    protected void onCreate() {
        bidAt = LocalDateTime.now();
    }
}
