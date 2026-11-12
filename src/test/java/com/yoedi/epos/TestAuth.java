package com.yoedi.epos;

import com.yoedi.epos.auth.AuthenticatedUser;
import com.yoedi.epos.user.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/** Authenticates a MockMvc request as a real user row, with the same principal type the JWT filter sets. */
public final class TestAuth {

    private TestAuth() {
    }

    public static RequestPostProcessor as(User user) {
        var principal = new AuthenticatedUser(user.getId(), user.getUsername(), user.getRole());
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
    }
}
