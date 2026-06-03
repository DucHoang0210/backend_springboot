package com.drm.auth.repository;

import com.drm.auth.entity.MarketplaceListing;
import com.drm.auth.entity.MarketplaceListing.ListingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MarketplaceListingRepository extends JpaRepository<MarketplaceListing, Integer> {

    List<MarketplaceListing> findByStatus(ListingStatus status);

    List<MarketplaceListing> findBySellerUsernameAndStatus(String sellerUsername, ListingStatus status);

    List<MarketplaceListing> findBySellerUsername(String sellerUsername);

    List<MarketplaceListing> findByNftId(Integer nftId);
}
