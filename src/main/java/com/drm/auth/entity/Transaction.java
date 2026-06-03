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
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "tx_hash", length = 255)
    private String txHash;

    @Column(name = "sender_username")
    private String senderUsername;

    @Column(name = "receiver_username")
    private String receiverUsername;

    @Column(name = "sender_wallet")
    private String senderWallet;

    @Column(name = "receiver_wallet")
    private String receiverWallet;

    @Column(name = "amount", precision = 38, scale = 18)
    private BigDecimal amount;

    @Column(name = "type") // e.g. "BUY", "LIST", "BID", "CANCEL"
    private String type;

    @Column(name = "nft_id")
    private Integer nftId;

    @Column(name = "nft_name")
    private String nftName;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
