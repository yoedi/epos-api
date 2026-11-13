package com.yoedi.epos.user;

import com.yoedi.epos.user.dto.UserListItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class UserController {

    private final UserService userService;

    @GetMapping
    public List<UserListItemResponse> findAll(@RequestParam(name = "role", required = false) Role role) {
        return userService.findAll(role);
    }
}
