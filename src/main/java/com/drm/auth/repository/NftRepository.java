package com.drm.auth.repository;

import com.drm.auth.entity.Nft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NftRepository extends JpaRepository<Nft, Integer> {
    List<Nft> findByOwnerAddressIgnoreCase(String ownerAddress);
    List<Nft> findByCreatorUsername(String creatorUsername);
}
