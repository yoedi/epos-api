package com.yoedi.epos.auth;

import com.yoedi.epos.auth.dto.LoginRequest;
import com.yoedi.epos.auth.dto.LoginResponse;
import com.yoedi.epos.user.User;
import com.yoedi.epos.user.UserService;
import com.yoedi.epos.user.dto.UserResponse;
import com.yoedi.epos.user.mapper.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    // Compared against when the username does not exist, so "unknown user" takes as long as
    // "wrong password" and response time does not reveal which usernames exist.
    private final String dummyHash;

    public AuthService(UserService userService, PasswordEncoder passwordEncoder,
                       JwtService jwtService, UserMapper userMapper) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userService.findByUsername(request.username().trim()).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        // Same error for unknown user, wrong password, and inactive user (no account-status leak).
        if (user == null || !passwordMatches || !user.isActive()) {
            throw new InvalidCredentialsException();
        }
        return new LoginResponse(
                jwtService.generateToken(user),
                TOKEN_TYPE,
                jwtService.expirationSeconds(),
                userMapper.toResponse(user));
    }

    /**
     * Current user for a valid token. Reads the database so a user deactivated after the token
     * was issued is rejected here and the name is always current.
     */
    @Transactional(readOnly = true)
    public UserResponse me(long userId) {
        User user = userService.findById(userId).filter(User::isActive).orElseThrow(UnauthorizedException::new);
        return userMapper.toResponse(user);
    }
}
