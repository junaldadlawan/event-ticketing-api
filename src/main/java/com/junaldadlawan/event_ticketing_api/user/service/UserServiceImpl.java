package com.junaldadlawan.event_ticketing_api.user.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.user.dto.UserPasswordUpdateRequest;
import com.junaldadlawan.event_ticketing_api.user.dto.UserRequest;
import com.junaldadlawan.event_ticketing_api.user.dto.UserSelfUpdateRequest;
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
    private final OrganizationAccessGuard accessGuard;

    @Override
    public User register(UserRequest request) {
        User newUser = User.builder().name(request.name()).email(request.email()).passwordHash(passwordEncoder.encode(request.passwordHash())).role(request.role()).build();
        return userRepository.save(newUser);
    }

    @Override
    public List<User> getUsers() {
        return userRepository.findByDeletedAtIsNull();
    }

    @Override
    public User update(UUID id, UserUpdateRequest request) {
        User user = getOrThrow(id);

        if (request.name() != null) {
            user.setName(request.name());
        }

        if (request.email() != null) {
            user.setEmail(request.email());
        }

        if (request.role() != null) {
            user.setRole(request.role());
        }
        return userRepository.save(user);
    }

    @Override
    public User updateSelfPassword(UserPasswordUpdateRequest request) {
        User user = getOrThrow(accessGuard.currentUserId());
        if(!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        return userRepository.save(user);
    }

    @Override
    public void delete(UUID id) {
        User user = getOrThrow(id);
        user.markDeleted();
        userRepository.save(user);
    }

    @Override
    public User getSelf() {
        return getOrThrow(accessGuard.currentUserId());
    }

    @Override
    public User updateSelf(UserSelfUpdateRequest request) {
        User user = getOrThrow(accessGuard.currentUserId());
        if (request.name() != null) {
            user.setName(request.name());
        }
        if (request.email() != null) {
            user.setEmail(request.email());
        }
        return userRepository.save(user);
    }

    public User getOrThrow(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User " + id + " not found"));
    }

}
