package com.yoedi.epos.user;

import com.yoedi.epos.common.exception.NotFoundException;
import com.yoedi.epos.user.dto.UserListItemResponse;
import com.yoedi.epos.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** User lookups for other features, plus the user list for the cashier filter. */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    /** All users, inactive ones included so their past sales stay filterable. Sorted by name. */
    @Transactional(readOnly = true)
    public List<UserListItemResponse> findAll(Role role) {
        Sort byName = Sort.by("name", "id");
        List<User> users = role == null ? userRepository.findAll(byName) : userRepository.findByRole(role, byName);
        return users.stream().map(userMapper::toListItem).toList();
    }

    @Transactional(readOnly = true)
    public User getById(long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Pengguna dengan id " + id + " tidak ditemukan"));
    }

    @Transactional(readOnly = true)
    public Optional<User> findById(long id) {
        return userRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
}
