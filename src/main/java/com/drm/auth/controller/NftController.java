package com.drm.auth.controller;

import com.drm.auth.entity.Nft;
import com.drm.auth.repository.NftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/nfts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class NftController {

    private final NftRepository nftRepository;

    @PostMapping
    public ResponseEntity<Nft> createNft(@RequestBody Nft nft) {
        Nft savedNft = nftRepository.save(nft);
        return ResponseEntity.ok(savedNft);
    }

    @GetMapping
    public ResponseEntity<List<Nft>> getAllNfts() {
        List<Nft> nfts = nftRepository.findAll();
        return ResponseEntity.ok(nfts);
    }

    @GetMapping("/owner/{address}")
    public ResponseEntity<List<Nft>> getNftsByOwner(@PathVariable("address") String address) {
        List<Nft> nfts = nftRepository.findByOwnerAddressIgnoreCase(address);
        return ResponseEntity.ok(nfts);
    }

    @GetMapping("/creator/{username}")
    public ResponseEntity<List<Nft>> getNftsByCreator(@PathVariable("username") String username) {
        List<Nft> nfts = nftRepository.findByCreatorUsername(username);
        return ResponseEntity.ok(nfts);
    }
}
