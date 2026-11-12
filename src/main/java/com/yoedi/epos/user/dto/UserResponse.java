package com.yoedi.epos.user.dto;

import com.yoedi.epos.user.Role;

/** Public view of a user (docs/api-spec.md §1). Never carries the password hash. */
public record UserResponse(
        long id,
        String name,
        String username,
        Role role
) {
}
