package com.yoedi.epos.user;

import com.yoedi.epos.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Minimal for now: lookups other features need. Login and user listing arrive with auth. */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User getById(long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Pengguna dengan id " + id + " tidak ditemukan"));
    }
}
