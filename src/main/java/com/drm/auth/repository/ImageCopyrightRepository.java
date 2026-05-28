package com.drm.auth.repository;

import com.drm.auth.entity.ImageCopyright;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ImageCopyrightRepository extends JpaRepository<ImageCopyright, Integer> {
    Optional<ImageCopyright> findByHash(String hash);
}
