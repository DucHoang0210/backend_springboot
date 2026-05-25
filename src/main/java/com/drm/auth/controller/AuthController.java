package com.drm.auth.controller;

import com.drm.auth.dto.AuthResponse;
import com.drm.auth.dto.LoginRequest;
import com.drm.auth.dto.RegisterRequest;
import com.drm.auth.service.AuthService;
import com.drm.auth.service.BlockchainService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final BlockchainService blockchainService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login-password")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
    
    @PostMapping(value = "/register-face", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> registerFace(
            @RequestParam("username") String username,
            @RequestParam("file") MultipartFile file
    ) {
        String message = authService.registerFace(username, file);
        return ResponseEntity.ok(message);
    }

    @PostMapping(value = "/login-face", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AuthResponse> loginFace(
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(authService.loginFace(file));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body("Token không hợp lệ");
        }
        String token = authHeader.substring(7);
        authService.logout(token);
        return ResponseEntity.ok("Đăng xuất thành công");
    }
    
    @PostMapping("/update-face-status")
    public ResponseEntity<String> updateFaceStatus(@RequestBody java.util.Map<String, String> payload) {
        authService.updateFaceStatus(payload.get("username"));
        return ResponseEntity.ok("Cập nhật trạng thái khuôn mặt thành công");
    }

    @PostMapping("/link-wallet")
    public ResponseEntity<String> linkWallet(@RequestBody java.util.Map<String, String> payload) {
        authService.linkWallet(payload.get("username"), payload.get("walletAddress"));
        return ResponseEntity.ok("Liên kết ví thành công");
    }

    @GetMapping("/blockchain-status")
    public ResponseEntity<java.util.Map<String, String>> getBlockchainStatus() {
        java.util.Map<String, String> status = new java.util.HashMap<>();
        status.put("url", blockchainService.getActiveUrl());
        status.put("blockNumber", blockchainService.getLatestBlockNumber());
        return ResponseEntity.ok(status);
    }

    @GetMapping("/wallet-balance/{username}")
    public ResponseEntity<java.util.Map<String, Object>> getWalletBalance(@PathVariable("username") String username) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        String walletAddress = authService.getWalletAddress(username);
        result.put("username", username);
        result.put("walletAddress", walletAddress);
        if (walletAddress != null && !walletAddress.isEmpty()) {
            result.put("balance", blockchainService.getBalance(walletAddress));
        } else {
            result.put("balance", 0);
        }
        return ResponseEntity.ok(result);
    }
}
