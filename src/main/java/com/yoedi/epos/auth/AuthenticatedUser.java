package com.yoedi.epos.auth;

import com.yoedi.epos.user.Role;

/**
 * The principal placed in the SecurityContext for a valid JWT. Built from the token claims only,
 * so authenticating a request never touches the database.
 */
public record AuthenticatedUser(long id, String username, Role role) {
}
