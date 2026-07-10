package com.billing.invoicehub.service;

import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final AppUserRepository userRepository;

    public UserService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<AppUser> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public List<AppUser> findVendors() {
        return userRepository.findByRoles_NameOrderByIdDesc("ROLE_VENDOR");
    }

    public Optional<AppUser> findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email);
    }

    public Optional<AppUser> findById(Long id) {
        return userRepository.findById(id);
    }
}
