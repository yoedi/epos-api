package com.yoedi.epos.user.dto;

import com.yoedi.epos.user.Role;

/**
 * One row of {@code GET /users} (docs/api-spec.md §8). Separate from {@link UserResponse}
 * because the login/me user object does not carry {@code is_active}.
 */
public record UserListItemResponse(
        long id,
        String name,
        String username,
        Role role,
        boolean isActive
) {
}
