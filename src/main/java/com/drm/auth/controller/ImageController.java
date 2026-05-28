package com.drm.auth.controller;

import com.drm.auth.entity.ImageCopyright;
import com.drm.auth.entity.User;
import com.drm.auth.repository.ImageCopyrightRepository;
import com.drm.auth.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ImageController {

    private final ImageCopyrightRepository imageCopyrightRepository;
    private final UserRepository userRepository;

    private static final String UPLOAD_DIR = "uploads";
    private static final String PYTHON_RECOGNIZE_URL = "http://localhost:5678/api/face/recognize";

    @PostConstruct
    public void init() {
        File dir = new File(UPLOAD_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadImage(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (file.isEmpty()) {
                result.put("success", false);
                result.put("message", "File trống");
                return ResponseEntity.badRequest().body(result);
            }

            byte[] bytes = file.getBytes();
            String hash = calculateSha256(bytes);
            
            // Lưu file vào thư mục uploads sử dụng hash làm tên file
            File dest = new File(UPLOAD_DIR + File.separator + hash);
            if (!dest.exists()) {
                file.transferTo(dest);
            }

            // Kiểm tra trong DB xem đã được đăng ký chưa
            Optional<ImageCopyright> existing = imageCopyrightRepository.findByHash(hash);
            
            result.put("success", true);
            result.put("hash", hash);
            result.put("url", "http://localhost:8765/api/images/raw/" + hash);
            result.put("filePath", dest.getAbsolutePath());
            
            if (existing.isPresent()) {
                result.put("registered", true);
                result.put("owner", existing.get().getUser().getUsername());
            } else {
                result.put("registered", false);
                result.put("owner", null);
            }
            
            return ResponseEntity.ok(result);

        } catch (IOException e) {
            result.put("success", false);
            result.put("message", "Lỗi lưu file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    @GetMapping("/raw/{hash}")
    public ResponseEntity<byte[]> getRawImage(@PathVariable String hash) {
        try {
            File file = new File(UPLOAD_DIR + File.separator + hash);
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }
            byte[] bytes = Files.readAllBytes(file.toPath());
            
            String contentType = Files.probeContentType(file.toPath());
            if (contentType == null) {
                contentType = "image/jpeg";
            }
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(bytes);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerCopyright(
            @RequestParam("hash") String hash,
            @RequestParam("username") String username,
            @RequestParam("faceFile") MultipartFile faceFile
    ) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 1. Kiểm tra ảnh đã đăng ký chưa
            Optional<ImageCopyright> existing = imageCopyrightRepository.findByHash(hash);
            if (existing.isPresent()) {
                result.put("success", false);
                result.put("message", "Ảnh đã được đăng ký bản quyền bởi " + existing.get().getUser().getUsername());
                return ResponseEntity.badRequest().body(result);
            }

            // 2. Kiểm tra tài khoản người dùng
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                result.put("success", false);
                result.put("message", "Người dùng không tồn tại");
                return ResponseEntity.badRequest().body(result);
            }
            User user = userOpt.get();

            // 3. Gọi Python Face Recognition nhận dạng
            Map<String, Object> faceResult = callFaceRecognizeApi(faceFile);
            if (faceResult == null || !Boolean.TRUE.equals(faceResult.get("success"))) {
                String errMsg = faceResult != null ? (String) faceResult.get("message") : "Nhận diện thất bại";
                result.put("success", false);
                result.put("message", "Xác thực KYC thất bại: " + errMsg);
                return ResponseEntity.badRequest().body(result);
            }

            String recognizedUser = (String) faceResult.get("username");
            if (recognizedUser == null || !recognizedUser.equalsIgnoreCase(username)) {
                result.put("success", false);
                result.put(
                    "message", 
                    "Khuôn mặt nhận diện không khớp với tài khoản! (Nhận diện được: " + 
                    (recognizedUser != null ? recognizedUser : "Không xác định") + ")"
                );
                return ResponseEntity.badRequest().body(result);
            }

            // 4. Lưu bản quyền vào DB
            ImageCopyright copyright = ImageCopyright.builder()
                    .user(user)
                    .hash(hash)
                    .filePath(UPLOAD_DIR + File.separator + hash)
                    .build();
            imageCopyrightRepository.save(copyright);

            result.put("success", true);
            result.put("message", "Đăng ký bản quyền ảnh thành công cho " + username);
            result.put("hash", hash);
            result.put("owner", username);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Lỗi xử lý đăng ký: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    private String calculateSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi hash SHA-256", e);
        }
    }

    private Map<String, Object> callFaceRecognizeApi(MultipartFile faceFile) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            byte[] fileBytes = faceFile.getBytes();
            String originalFilename = faceFile.getOriginalFilename();
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
            ResponseEntity<Map> response = restTemplate.postForEntity(PYTHON_RECOGNIZE_URL, requestEntity, Map.class);
            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException("Không thể kết nối đến máy chủ Python Face Recognition: " + e.getMessage(), e);
        }
    }
}
