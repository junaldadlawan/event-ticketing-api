package com.junaldadlawan.event_ticketing_api.user.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.user.dto.UserPasswordUpdateRequest;
import com.junaldadlawan.event_ticketing_api.user.dto.UserRequest;
import com.junaldadlawan.event_ticketing_api.user.dto.UserResponse;
import com.junaldadlawan.event_ticketing_api.user.dto.UserSelfUpdateRequest;
import com.junaldadlawan.event_ticketing_api.user.dto.UserUpdateRequest;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
        applyName(user, request.name());
        applyEmail(user, request.email());
        if (request.role() != null) {
            user.setRole(request.role());
        }
        return saveOrConflict(user);
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

    @Override
    public User getSelf() {
        return getOrThrow(accessGuard.currentUserId());
    }

    @Override
    public User updateSelf(UserSelfUpdateRequest request) {
        User user = getOrThrow(accessGuard.currentUserId());
        applyName(user, request.name());
        applyEmail(user, request.email());
        return saveOrConflict(user);
    }

    public User getOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User " + id + " not found"));
    }

    private void applyName(User user, String name) {
        if (name == null) {
            return;
        }
        if (name.isBlank()) {
            throw new BadRequestException("Name must not be blank");
        }
        user.setName(name);
    }

    private void applyEmail(User user, String email) {
        if (email == null) {
            return;
        }
        if (email.isBlank()) {
            throw new BadRequestException("Email must not be blank");
        }
        requireEmailNotTaken(email, user.getId());
        user.setEmail(email);
    }

    /**
     * Best-effort, checked before the write - catches the common case with
     * a clean message. Not itself race-proof: two concurrent updates to the
     * same new email could both pass this check before either saves.
     * {@link #saveOrConflict} is the actual backstop for that race, same
     * "app-level check + DB-constraint-violation-to-ConflictException"
     * idiom used elsewhere in this codebase (e.g. {@code
     * ResalePolicyServiceImpl}, {@code RefundPolicyServiceImpl}).
     */
    private void requireEmailNotTaken(String email, UUID excludingUserId) {
        userRepository.findByEmail(email)
                .filter(existing -> !existing.getId().equals(excludingUserId))
                .ifPresent(existing -> {
                    throw new ConflictException("Email already in use");
                });
    }

    /** {@code saveAndFlush} so the DB's {@code uq_users_email} constraint (the actual race backstop) is checked synchronously, here, not at some later unrelated flush. */
    private User saveOrConflict(User user) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Email already in use");
        }
    }
}
