package com.drm.auth.controller;

import com.drm.auth.dto.AuthResponse;
import com.drm.auth.dto.LoginRequest;
import com.drm.auth.dto.RegisterRequest;
import com.drm.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login-password")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
    
    @PostMapping("/update-face-status")
    public ResponseEntity<String> updateFaceStatus(@RequestBody java.util.Map<String, String> payload) {
        authService.updateFaceStatus(payload.get("username"));
        return ResponseEntity.ok("Cập nhật trạng thái khuôn mặt thành công");
    }
}
