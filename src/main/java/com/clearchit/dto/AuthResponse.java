package com.clearchit.dto;

public class AuthResponse {

    private Long userId;
    private Long memberId;
    private String email;
    private String role;
    private String token;

    public AuthResponse() {
    }

    public AuthResponse(
            Long userId,
            Long memberId,
            String email,
            String role,
            String token) {

        this.userId = userId;
        this.memberId = memberId;
        this.email = email;
        this.role = role;
        this.token = token;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}