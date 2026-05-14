package com.drm.auth.dto;
import lombok.Data;
@Data
public class AuthResponse {
    private String token;
    private String username;
    private boolean hasFaceData;
    
    @lombok.Builder
    public AuthResponse(String token, String username, boolean hasFaceData) {
        this.token = token;
        this.username = username;
        this.hasFaceData = hasFaceData;
    }
}
