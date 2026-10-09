package com.junaldadlawan.event_ticketing_api.user.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.common.logging.BusinessAuditLogger;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.upload.service.UploadedFileUrls;
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
    private final UploadedFileUrls uploadedFileUrls;

    @Override
    public User register(UserRequest request) {
        User newUser = User.builder().name(request.name()).email(request.email()).passwordHash(passwordEncoder.encode(request.passwordHash())).role(request.role()).build();
        User saved = userRepository.save(newUser);
        // The role is logged on purpose: self-registration currently accepts
        // any role from the request body, so this line makes that visible.
        BusinessAuditLogger.recordAs(saved.getId(), "user.registered", "User", saved.getId(),
                BusinessAuditLogger.Outcome.SUCCESS, "role=" + saved.getRole());
        return saved;
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
            BusinessAuditLogger.record("user.password_changed", "User", user.getId(),
                    BusinessAuditLogger.Outcome.FAILURE, "current password incorrect");
            throw new BadRequestException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        User saved = userRepository.save(user);
        BusinessAuditLogger.record("user.password_changed", "User", saved.getId(), BusinessAuditLogger.Outcome.SUCCESS);
        return saved;
    }

    @Override
    public void delete(UUID id) {
        User user = getOrThrow(id);
        user.markDeleted();
        userRepository.save(user);
        BusinessAuditLogger.record("user.deleted", "User", id, BusinessAuditLogger.Outcome.SUCCESS);
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
        String previousAvatar = user.getAvatarUrl();
        if (request.avatarUrl() != null) {
            if (request.avatarUrl().isBlank()) {
                user.setAvatarUrl(null);
            } else {
                // Only our own uploaded images: any other link would let a user show an arbitrary page or tracker on
                // every screen that displays their picture.
                if (uploadedFileUrls.ownFileName(request.avatarUrl()).isEmpty()) {
                    throw new BadRequestException("avatarUrl must be the URL of an image uploaded through POST /api/v1/uploads");
                }
                user.setAvatarUrl(request.avatarUrl().trim());
            }
        }
        User saved = userRepository.save(user);
        // The replaced (or removed) picture is deleted from storage unless something else still uses it.
        if (previousAvatar != null && !previousAvatar.equals(saved.getAvatarUrl())) {
            uploadedFileUrls.deleteIfUnreferenced(previousAvatar);
        }
        return saved;
    }

    public User getOrThrow(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User " + id + " not found"));
    }

}
