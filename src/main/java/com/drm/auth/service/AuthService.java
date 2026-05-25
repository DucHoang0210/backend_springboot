package com.drm.auth.service;

import com.drm.auth.dto.AuthResponse;
import com.drm.auth.dto.LoginRequest;
import com.drm.auth.dto.RegisterRequest;
import com.drm.auth.entity.Token;
import com.drm.auth.entity.User;
import com.drm.auth.repository.TokenRepository;
import com.drm.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final TokenRepository tokenRepository;

    private static final String PYTHON_AI_URL = "http://localhost:5678/api/face";

    public AuthResponse register(RegisterRequest request) {
        var user = User.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .hasFaceData(false)
                .build();
        var savedUser = repository.save(user);
        var jwtToken = jwtService.generateToken(user);
        
        // Save token in DB for session tracking
        saveUserToken(savedUser, jwtToken);
        
        return AuthResponse.builder()
                .token(jwtToken)
                .username(user.getUsername())
                .hasFaceData(user.isHasFaceData())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );
        var user = repository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        var jwtToken = jwtService.generateToken(user);
        
        // Single Active Session: revoke all other sessions before saving new one
        revokeAllUserTokens(user);
        saveUserToken(user, jwtToken);
        
        return AuthResponse.builder()
                .token(jwtToken)
                .username(user.getUsername())
                .hasFaceData(user.isHasFaceData())
                .build();
    }

    public void saveUserToken(User user, String jwtToken) {
        var token = Token.builder()
                .user(user)
                .token(jwtToken)
                .expired(false)
                .revoked(false)
                .build();
        tokenRepository.save(token);
    }

    public void revokeAllUserTokens(User user) {
        var validUserTokens = tokenRepository.findAllValidTokenByUser(user.getId());
        if (validUserTokens.isEmpty()) {
            return;
        }
        validUserTokens.forEach(token -> {
            token.setExpired(true);
            token.setRevoked(true);
        });
        tokenRepository.saveAll(validUserTokens);
    }

    public void logout(String jwtToken) {
        var storedToken = tokenRepository.findByToken(jwtToken)
                .orElseThrow(() -> new RuntimeException("Token not found"));
        storedToken.setExpired(true);
        storedToken.setRevoked(true);
        tokenRepository.save(storedToken);
    }
    
    public void updateFaceStatus(String username) {
        var user = repository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setHasFaceData(true);
        repository.save(user);
    }

    public void linkWallet(String username, String walletAddress) {
        var user = repository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setWalletAddress(walletAddress);
        repository.save(user);
    }

    // ──────────────────────────────────────────────
    // Các phương thức giao tiếp với Python Face AI
    // ──────────────────────────────────────────────

    public String registerFace(String username, MultipartFile file) {
        var user = repository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        
        // Gửi ảnh sang Python FastAPI
        Map<String, Object> response = callPythonApi(PYTHON_AI_URL + "/register", username, file);
        
        if (response != null && Boolean.TRUE.equals(response.get("success"))) {
            user.setHasFaceData(true);
            repository.save(user);
            return (String) response.get("message");
        } else {
            String errMsg = response != null ? (String) response.get("message") : "Không có phản hồi từ máy chủ AI";
            throw new RuntimeException("Đăng ký khuôn mặt thất bại: " + errMsg);
        }
    }

    public AuthResponse loginFace(MultipartFile file) {
        // Gửi ảnh sang Python FastAPI để nhận diện
        Map<String, Object> response = callPythonApi(PYTHON_AI_URL + "/recognize", null, file);
        
        if (response != null && Boolean.TRUE.equals(response.get("success"))) {
            String recognizedUsername = (String) response.get("username");
            if (recognizedUsername == null) {
                throw new RuntimeException("Không tìm thấy tên người dùng trong kết quả nhận diện");
            }
            
            var user = repository.findByUsername(recognizedUsername)
                    .orElseThrow(() -> new RuntimeException("Tài khoản '" + recognizedUsername + "' khớp với khuôn mặt nhưng không tồn tại trên hệ thống"));
            
            var jwtToken = jwtService.generateToken(user);
            
            // Hỗ trợ Single Active Session cho đăng nhập khuôn mặt
            revokeAllUserTokens(user);
            saveUserToken(user, jwtToken);
            
            return AuthResponse.builder()
                    .token(jwtToken)
                    .username(user.getUsername())
                    .hasFaceData(user.isHasFaceData())
                    .build();
        } else {
            String errMsg = response != null ? (String) response.get("message") : "Nhận diện khuôn mặt không thành công";
            throw new RuntimeException("Xác thực khuôn mặt thất bại: " + errMsg);
        }
    }

    private Map<String, Object> callPythonApi(String url, String username, MultipartFile file) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            if (username != null) {
                body.add("username", username);
            }
            
            byte[] fileBytes = file.getBytes();
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isEmpty()) {
                originalFilename = "capture.jpg";
            }
            final String finalFilename = originalFilename;
            ByteArrayResource resource = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return finalFilename;
                }
            };
            body.add("file", resource);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi kết nối hoặc xử lý với máy chủ Python AI: " + e.getMessage(), e);
        }
    }

    public String getWalletAddress(String username) {
        return repository.findByUsername(username)
                .map(User::getWalletAddress)
                .orElse(null);
    }
}

