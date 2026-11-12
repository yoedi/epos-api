package com.yoedi.epos.auth.dto;

import com.yoedi.epos.user.dto.UserResponse;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
}
