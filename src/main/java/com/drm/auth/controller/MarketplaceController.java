package com.drm.auth.controller;

import com.drm.auth.entity.Bid;
import com.drm.auth.entity.MarketplaceListing;
import com.drm.auth.entity.MarketplaceListing.ListingStatus;
import com.drm.auth.entity.MarketplaceListing.ListingType;
import com.drm.auth.entity.Nft;
import com.drm.auth.repository.BidRepository;
import com.drm.auth.repository.MarketplaceListingRepository;
import com.drm.auth.repository.NftRepository;
import com.drm.auth.entity.Transaction;
import com.drm.auth.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/marketplace")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MarketplaceController {

    private final MarketplaceListingRepository listingRepo;
    private final BidRepository bidRepo;
    private final NftRepository nftRepo;
    private final TransactionRepository transactionRepo;

    // ─── LIST NFT LÊN MARKETPLACE ────────────────────────────────────────────
    @PostMapping("/list")
    public ResponseEntity<?> listNft(@RequestBody Map<String, Object> body) {
        try {
            Integer nftId = Integer.valueOf(body.get("nftId").toString());
            String sellerUsername = body.get("sellerUsername").toString();
            BigDecimal price = new BigDecimal(body.get("price").toString());
            String listingTypeStr = body.get("listingType").toString();
            ListingType listingType = ListingType.valueOf(listingTypeStr);

            Optional<Nft> nftOpt = nftRepo.findById(nftId);
            if (nftOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "NFT không tồn tại"));
            }
            Nft nft = nftOpt.get();

            // Kiểm tra NFT chưa được đang bán
            List<MarketplaceListing> existing = listingRepo.findByNftId(nftId);
            boolean alreadyListed = existing.stream().anyMatch(l -> l.getStatus() == ListingStatus.ACTIVE);
            if (alreadyListed) {
                return ResponseEntity.badRequest().body(Map.of("error", "NFT này đã được đăng bán"));
            }

            MarketplaceListing.MarketplaceListingBuilder builder = MarketplaceListing.builder()
                    .nftId(nftId)
                    .sellerUsername(sellerUsername)
                    .price(price)
                    .listingType(listingType)
                    .status(ListingStatus.ACTIVE);

            // Nếu là auction thì có thêm endsAt
            if (listingType == ListingType.AUCTION && body.containsKey("endsAt")) {
                String endsAtStr = body.get("endsAt").toString();
                LocalDateTime endsAt;
                try {
                    endsAt = java.time.OffsetDateTime.parse(endsAtStr).toLocalDateTime();
                } catch (Exception e) {
                    try {
                        endsAt = java.time.Instant.parse(endsAtStr).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
                    } catch (Exception ex) {
                        try {
                            endsAt = LocalDateTime.parse(endsAtStr);
                        } catch (Exception ex2) {
                            endsAt = LocalDateTime.parse(endsAtStr.replace("Z", ""));
                        }
                    }
                }
                builder.endsAt(endsAt);
            }

            MarketplaceListing saved = listingRepo.save(builder.build());

            // Cập nhật trạng thái NFT
            nft.setIsListed(true);
            nft.setPrice(price);
            nft.setListingType(listingTypeStr);
            nftRepo.save(nft);

            // Lưu Transaction
            Transaction tx = Transaction.builder()
                    .senderUsername(sellerUsername)
                    .senderWallet(nft.getOwnerAddress())
                    .amount(price)
                    .type("LIST")
                    .nftId(nftId)
                    .nftName(nft.getName())
                    .build();
            transactionRepo.save(tx);

            return ResponseEntity.ok(enrichListing(saved));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── LẤY TẤT CẢ LISTING ĐANG ACTIVE (cho marketplace) ──────────────────
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllActiveListings(
            @RequestParam(value = "excludeUsername", required = false) String excludeUsername) {
        List<MarketplaceListing> listings = listingRepo.findByStatus(ListingStatus.ACTIVE);

        List<Map<String, Object>> result = listings.stream()
                .filter(l -> excludeUsername == null || !l.getSellerUsername().equalsIgnoreCase(excludeUsername))
                .map(this::enrichListing)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // ─── LISTING CỦA MÌNH ────────────────────────────────────────────────────
    @GetMapping("/mine")
    public ResponseEntity<List<Map<String, Object>>> getMyListings(@RequestParam String username) {
        List<MarketplaceListing> listings = listingRepo.findBySellerUsername(username);
        List<Map<String, Object>> result = listings.stream()
                .map(this::enrichListing)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // ─── MUA NGAY (FIXED PRICE) ──────────────────────────────────────────────
    @PostMapping("/{listingId}/buy")
    public ResponseEntity<?> buyNow(@PathVariable Integer listingId, @RequestBody Map<String, Object> body) {
        try {
            String buyerUsername = body.get("buyerUsername").toString();

            Optional<MarketplaceListing> listingOpt = listingRepo.findById(listingId);
            if (listingOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            MarketplaceListing listing = listingOpt.get();
            if (listing.getStatus() != ListingStatus.ACTIVE) {
                return ResponseEntity.badRequest().body(Map.of("error", "Listing không còn active"));
            }
            if (listing.getListingType() != ListingType.FIXED) {
                return ResponseEntity.badRequest().body(Map.of("error", "NFT này là auction, không thể mua ngay"));
            }
            if (listing.getSellerUsername().equalsIgnoreCase(buyerUsername)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Bạn không thể mua NFT của chính mình"));
            }

            // Cập nhật listing
            listing.setStatus(ListingStatus.SOLD);
            listing.setBuyerUsername(buyerUsername);
            listing.setSoldAt(LocalDateTime.now());
            listingRepo.save(listing);

            // Chuyển quyền sở hữu NFT
            String nftName = "";
            String sellerWallet = "";
            Optional<Nft> nftOpt = nftRepo.findById(listing.getNftId());
            if (nftOpt.isPresent()) {
                Nft nft = nftOpt.get();
                nftName = nft.getName();
                sellerWallet = nft.getOwnerAddress();
                nft.setCreatorUsername(buyerUsername);
                nft.setIsListed(false);
                nft.setPrice(null);
                nft.setListingType(null);
                
                // Cập nhật địa chỉ ví của chủ sở hữu mới
                if (body.containsKey("buyerWalletAddress")) {
                    nft.setOwnerAddress(body.get("buyerWalletAddress").toString());
                }
                nftRepo.save(nft);
            }

            // Lưu Transaction
            String txHash = body.containsKey("txHash") ? body.get("txHash").toString() : null;
            String buyerWallet = body.containsKey("buyerWalletAddress") ? body.get("buyerWalletAddress").toString() : "";
            
            Transaction tx = Transaction.builder()
                    .txHash(txHash)
                    .senderUsername(buyerUsername)
                    .receiverUsername(listing.getSellerUsername())
                    .senderWallet(buyerWallet)
                    .receiverWallet(sellerWallet)
                    .amount(listing.getPrice())
                    .type("BUY")
                    .nftId(listing.getNftId())
                    .nftName(nftName)
                    .build();
            transactionRepo.save(tx);

            return ResponseEntity.ok(Map.of(
                    "message", "Mua thành công",
                    "listingId", listingId,
                    "buyerUsername", buyerUsername
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── ĐẶT GIÁ (AUCTION BID) ───────────────────────────────────────────────
    @PostMapping("/{listingId}/bid")
    public ResponseEntity<?> placeBid(@PathVariable Integer listingId, @RequestBody Map<String, Object> body) {
        try {
            String bidderUsername = body.get("bidderUsername").toString();
            BigDecimal amount = new BigDecimal(body.get("amount").toString());

            Optional<MarketplaceListing> listingOpt = listingRepo.findById(listingId);
            if (listingOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            MarketplaceListing listing = listingOpt.get();
            if (listing.getStatus() != ListingStatus.ACTIVE) {
                return ResponseEntity.badRequest().body(Map.of("error", "Listing không còn active"));
            }
            if (listing.getListingType() != ListingType.AUCTION) {
                return ResponseEntity.badRequest().body(Map.of("error", "NFT này không phải auction"));
            }
            if (listing.getSellerUsername().equalsIgnoreCase(bidderUsername)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Bạn không thể đặt giá NFT của chính mình"));
            }

            // Kiểm tra bid phải cao hơn giá hiện tại
            BigDecimal minBid = listing.getCurrentHighBid() != null
                    ? listing.getCurrentHighBid()
                    : listing.getPrice();

            if (amount.compareTo(minBid) <= 0) {
                return ResponseEntity.badRequest().body(
                        Map.of("error", "Giá đặt phải cao hơn " + minBid + " ETH"));
            }

            // Kiểm tra auction chưa hết hạn
            if (listing.getEndsAt() != null && LocalDateTime.now().isAfter(listing.getEndsAt())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Auction đã kết thúc"));
            }

            // Lưu bid
            Bid bid = Bid.builder()
                    .listingId(listingId)
                    .bidderUsername(bidderUsername)
                    .amount(amount)
                    .build();
            bidRepo.save(bid);

            // Cập nhật highest bid
            listing.setCurrentHighBid(amount);
            listing.setHighestBidderUsername(bidderUsername);
            listingRepo.save(listing);

            // Lưu Transaction
            String nftName = "";
            String sellerWallet = "";
            Optional<Nft> nftOpt = nftRepo.findById(listing.getNftId());
            if (nftOpt.isPresent()) {
                nftName = nftOpt.get().getName();
                sellerWallet = nftOpt.get().getOwnerAddress();
            }

            Transaction tx = Transaction.builder()
                    .senderUsername(bidderUsername)
                    .receiverUsername(listing.getSellerUsername())
                    .receiverWallet(sellerWallet)
                    .amount(amount)
                    .type("BID")
                    .nftId(listing.getNftId())
                    .nftName(nftName)
                    .build();
            transactionRepo.save(tx);

            return ResponseEntity.ok(Map.of(
                    "message", "Đặt giá thành công",
                    "listingId", listingId,
                    "amount", amount,
                    "bidderUsername", bidderUsername
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── XEM LỊCH SỬ BID ────────────────────────────────────────────────────
    @GetMapping("/{listingId}/bids")
    public ResponseEntity<List<Bid>> getBids(@PathVariable Integer listingId) {
        return ResponseEntity.ok(bidRepo.findByListingIdOrderByAmountDesc(listingId));
    }

    // ─── HOÀN TẤT ĐẤU GIÁ (FINALIZE AUCTION) ──────────────────────────────────
    @PostMapping("/{listingId}/finalize")
    public ResponseEntity<?> finalizeAuction(@PathVariable Integer listingId, @RequestBody Map<String, Object> body) {
        try {
            Optional<MarketplaceListing> listingOpt = listingRepo.findById(listingId);
            if (listingOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            MarketplaceListing listing = listingOpt.get();
            if (listing.getStatus() != ListingStatus.ACTIVE) {
                return ResponseEntity.badRequest().body(Map.of("error", "Listing không còn active"));
            }
            if (listing.getListingType() != ListingType.AUCTION) {
                return ResponseEntity.badRequest().body(Map.of("error", "NFT này không phải dạng đấu giá"));
            }

            String winnerUsername = listing.getHighestBidderUsername();
            BigDecimal finalAmount = listing.getCurrentHighBid();
            String txHash = body.containsKey("txHash") ? body.get("txHash").toString() : null;

            // Load thông tin NFT
            Optional<Nft> nftOpt = nftRepo.findById(listing.getNftId());
            if (nftOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "NFT không tồn tại"));
            }
            Nft nft = nftOpt.get();
            String nftName = nft.getName();
            String sellerWallet = nft.getOwnerAddress();

            if (winnerUsername != null && !winnerUsername.trim().isEmpty()) {
                // Có người thắng cuộc
                listing.setStatus(ListingStatus.SOLD);
                listing.setBuyerUsername(winnerUsername);
                listing.setSoldAt(LocalDateTime.now());
                listingRepo.save(listing);

                // Chuyển quyền sở hữu NFT sang cho người thắng
                nft.setCreatorUsername(winnerUsername); // Trong context của đồ án này, creatorUsername được dùng làm owner Web2
                nft.setIsListed(false);
                nft.setPrice(null);
                nft.setListingType(null);

                // Lấy ví của người thắng cuộc
                String winnerWallet = "";
                Optional<User> winnerUserOpt = userRepo.findByUsername(winnerUsername);
                if (winnerUserOpt.isPresent()) {
                    winnerWallet = winnerUserOpt.get().getWalletAddress();
                    if (winnerWallet != null && !winnerWallet.trim().isEmpty()) {
                        nft.setOwnerAddress(winnerWallet);
                    }
                }
                
                // Nếu FE truyền ví trực tiếp
                if (body.containsKey("winnerWalletAddress")) {
                    winnerWallet = body.get("winnerWalletAddress").toString();
                    nft.setOwnerAddress(winnerWallet);
                }
                nftRepo.save(nft);

                // Ghi Transaction SOLD
                Transaction tx = Transaction.builder()
                        .txHash(txHash)
                        .senderUsername(winnerUsername)
                        .receiverUsername(listing.getSellerUsername())
                        .senderWallet(winnerWallet)
                        .receiverWallet(sellerWallet)
                        .amount(finalAmount)
                        .type("SOLD")
                        .nftId(listing.getNftId())
                        .nftName(nftName)
                        .build();
                transactionRepo.save(tx);

                return ResponseEntity.ok(Map.of(
                        "message", "Đấu giá hoàn tất thành công. Người chiến thắng: " + winnerUsername,
                        "winner", winnerUsername,
                        "amount", finalAmount,
                        "status", "SOLD"
                ));
            } else {
                // Không có ai bid, trả lại NFT cho người bán
                listing.setStatus(ListingStatus.CANCELLED);
                listingRepo.save(listing);

                nft.setIsListed(false);
                nft.setPrice(null);
                nft.setListingType(null);
                nftRepo.save(nft);

                // Ghi Transaction CANCEL
                Transaction tx = Transaction.builder()
                        .txHash(txHash)
                        .senderUsername(listing.getSellerUsername())
                        .senderWallet(sellerWallet)
                        .amount(BigDecimal.ZERO)
                        .type("CANCEL")
                        .nftId(listing.getNftId())
                        .nftName(nftName)
                        .build();
                transactionRepo.save(tx);

                return ResponseEntity.ok(Map.of(
                        "message", "Đấu giá kết thúc không có người tham gia. Trả lại NFT cho người bán.",
                        "status", "CANCELLED"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── HỦY LISTING ─────────────────────────────────────────────────────────
    @DeleteMapping("/{listingId}")
    public ResponseEntity<?> cancelListing(@PathVariable Integer listingId,
                                            @RequestParam String username) {
        Optional<MarketplaceListing> listingOpt = listingRepo.findById(listingId);
        if (listingOpt.isEmpty()) return ResponseEntity.notFound().build();

        MarketplaceListing listing = listingOpt.get();
        if (!listing.getSellerUsername().equalsIgnoreCase(username)) {
            return ResponseEntity.status(403).body(Map.of("error", "Bạn không có quyền hủy listing này"));
        }

        listing.setStatus(ListingStatus.CANCELLED);
        listingRepo.save(listing);

        // Cập nhật trạng thái NFT
        String nftName = "";
        String sellerWallet = "";
        Optional<Nft> nftOpt = nftRepo.findById(listing.getNftId());
        if (nftOpt.isPresent()) {
            Nft nft = nftOpt.get();
            nftName = nft.getName();
            sellerWallet = nft.getOwnerAddress();
            nft.setIsListed(false);
            nft.setPrice(null);
            nft.setListingType(null);
            nftRepo.save(nft);
        }

        // Lưu Transaction
        Transaction tx = Transaction.builder()
                .senderUsername(username)
                .senderWallet(sellerWallet)
                .amount(BigDecimal.ZERO)
                .type("CANCEL")
                .nftId(listing.getNftId())
                .nftName(nftName)
                .build();
        transactionRepo.save(tx);

        return ResponseEntity.ok(Map.of("message", "Đã hủy listing"));
    }

    // ─── HELPER: gộp thông tin NFT vào listing ───────────────────────────────
    private Map<String, Object> enrichListing(MarketplaceListing listing) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", listing.getId());
        map.put("nftId", listing.getNftId());
        map.put("sellerUsername", listing.getSellerUsername());
        map.put("price", listing.getPrice());
        map.put("listingType", listing.getListingType());
        map.put("status", listing.getStatus());
        map.put("currentHighBid", listing.getCurrentHighBid());
        map.put("highestBidderUsername", listing.getHighestBidderUsername());
        map.put("endsAt", listing.getEndsAt());
        map.put("createdAt", listing.getCreatedAt());
        map.put("soldAt", listing.getSoldAt());
        map.put("buyerUsername", listing.getBuyerUsername());

        // Lấy thông tin NFT
        nftRepo.findById(listing.getNftId()).ifPresent(nft -> {
            map.put("nftName", nft.getName());
            map.put("nftImage", nft.getImageUrl());
            map.put("nftDescription", nft.getDescription());
            map.put("nftCreatorUsername", nft.getCreatorUsername());
            map.put("sellerWalletAddress", nft.getOwnerAddress());
        });

        return map;
    }
}
