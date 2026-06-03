package com.drm.auth.controller;

import com.drm.auth.entity.Bid;
import com.drm.auth.entity.MarketplaceListing;
import com.drm.auth.entity.MarketplaceListing.ListingStatus;
import com.drm.auth.entity.MarketplaceListing.ListingType;
import com.drm.auth.entity.Nft;
import com.drm.auth.entity.Transaction;
import com.drm.auth.repository.BidRepository;
import com.drm.auth.repository.MarketplaceListingRepository;
import com.drm.auth.repository.NftRepository;
import com.drm.auth.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuctionController {

    private final MarketplaceListingRepository listingRepo;
    private final BidRepository bidRepo;
    private final NftRepository nftRepo;
    private final TransactionRepository transactionRepo;

    // ─── LẤY TẤT CẢ ACTIVE AUCTIONS ──────────────────────────────────────────
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getActiveAuctions() {
        List<MarketplaceListing> listings = listingRepo.findByStatus(ListingStatus.ACTIVE);
        
        List<Map<String, Object>> result = listings.stream()
                .filter(l -> l.getListingType() == ListingType.AUCTION)
                .map(this::enrichListing)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // ─── ĐẶT GIÁ (AUCTION BID) CHO /api/auctions/{listingId}/bid ─────────────
    @PostMapping("/{listingId}/bid")
    public ResponseEntity<?> placeBid(@PathVariable Integer listingId, @RequestBody Map<String, Object> body) {
        try {
            BigDecimal amount = new BigDecimal(body.get("amount").toString());
            
            // Resolve bidder username from request body or SecurityContextHolder
            String bidderUsername = null;
            if (body.containsKey("bidderUsername")) {
                bidderUsername = body.get("bidderUsername").toString();
            }
            if (bidderUsername == null || bidderUsername.trim().isEmpty() || "anonymousUser".equals(bidderUsername)) {
                var auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                    bidderUsername = auth.getName();
                }
            }

            if (bidderUsername == null || bidderUsername.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Vui lòng đăng nhập để thực hiện đặt giá."));
            }

            Optional<MarketplaceListing> listingOpt = listingRepo.findById(listingId);
            if (listingOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            MarketplaceListing listing = listingOpt.get();
            if (listing.getStatus() != ListingStatus.ACTIVE) {
                return ResponseEntity.badRequest().body(Map.of("error", "Listing không còn active"));
            }
            if (listing.getListingType() != ListingType.AUCTION) {
                return ResponseEntity.badRequest().body(Map.of("error", "NFT này không phải đấu giá (auction)"));
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
                        Map.of("error", "Giá đặt phải cao hơn giá hiện tại " + minBid + " ETH"));
            }

            // Kiểm tra đấu giá chưa kết thúc
            if (listing.getEndsAt() != null && LocalDateTime.now().isAfter(listing.getEndsAt())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Phiên đấu giá đã kết thúc"));
            }

            // Lưu bid mới vào DB
            Bid bid = Bid.builder()
                    .listingId(listingId)
                    .bidderUsername(bidderUsername)
                    .amount(amount)
                    .build();
            bidRepo.save(bid);

            // Cập nhật listing
            listing.setCurrentHighBid(amount);
            listing.setHighestBidderUsername(bidderUsername);
            MarketplaceListing savedListing = listingRepo.save(listing);

            // Lưu Transaction cho Ledger
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

            // Trả về fully enriched listing object để frontend cập nhật UI an toàn
            return ResponseEntity.ok(enrichListing(savedListing));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── HELPER: gộp thông tin NFT vào listing với các alias keys tương thích ─
    private Map<String, Object> enrichListing(MarketplaceListing listing) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", listing.getId());
        map.put("nftId", listing.getNftId());
        map.put("tokenId", listing.getNftId()); // Frontend compatibility alias
        map.put("sellerUsername", listing.getSellerUsername());
        map.put("price", listing.getPrice());
        map.put("listingType", listing.getListingType());
        map.put("status", listing.getStatus());
        map.put("currentHighBid", listing.getCurrentHighBid());
        map.put("highestBid", listing.getCurrentHighBid()); // Frontend compatibility alias
        map.put("highestBidderUsername", listing.getHighestBidderUsername());
        map.put("endsAt", listing.getEndsAt());
        map.put("createdAt", listing.getCreatedAt());
        map.put("soldAt", listing.getSoldAt());
        map.put("buyerUsername", listing.getBuyerUsername());

        // Lấy thông tin NFT bổ sung
        nftRepo.findById(listing.getNftId()).ifPresent(nft -> {
            map.put("nftName", nft.getName());
            map.put("title", nft.getName()); // Frontend compatibility alias
            map.put("nftImage", nft.getImageUrl());
            map.put("image", nft.getImageUrl()); // Frontend compatibility alias
            map.put("nftDescription", nft.getDescription());
            map.put("description", nft.getDescription()); // Frontend compatibility alias
            map.put("nftCreatorUsername", nft.getCreatorUsername());
            map.put("sellerWalletAddress", nft.getOwnerAddress());
        });

        return map;
    }
}
