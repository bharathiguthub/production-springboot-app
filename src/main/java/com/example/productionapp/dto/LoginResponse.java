package com.example.productionapp.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
