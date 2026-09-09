package com.junaldadlawan.event_ticketing_api.user.service;

import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.user.dto.UserPasswordUpdateRequest;
import com.junaldadlawan.event_ticketing_api.user.dto.UserRequest;
import com.junaldadlawan.event_ticketing_api.user.dto.UserResponse;
import com.junaldadlawan.event_ticketing_api.user.dto.UserUpdateRequest;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public User register(UserRequest request) {
        User newUser = User.builder()
                .name(request.name())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.passwordHash()))
                .role(request.role())
                .build();
        return userRepository.save(newUser);
    }

    @Override
    public List<User> getUsers() {
        return userRepository.findByDeletedAtIsNull();
    }

    @Override
    public User update(UUID id, UserUpdateRequest request) {
        User user = getOrThrow(id);
        user.setName(request.name());
        user.setEmail(request.email());
        user.setRole(request.role());
        return userRepository.save(user);
    }

    @Override
    public User updatePassword(UUID id, UserPasswordUpdateRequest request) {
        User user = getOrThrow(id);
        user.setPasswordHash(passwordEncoder.encode(request.passwordHash()));
        return userRepository.save(user);
    }

    @Override
    public void delete(UUID id) {
        User user = getOrThrow(id);
        user.markDeleted();
        userRepository.save(user);
    }

    public User getOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User " + id + " not found"));
    }
}
