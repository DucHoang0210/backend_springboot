package com.drm.auth.repository;

import com.drm.auth.entity.Bid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BidRepository extends JpaRepository<Bid, Integer> {

    List<Bid> findByListingIdOrderByAmountDesc(Integer listingId);

    List<Bid> findByBidderUsername(String bidderUsername);
}
