package com.junaldadlawan.event_ticketing_api.user.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.common.logging.BusinessAuditLogger;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.upload.service.UploadedFileUrls;
import com.junaldadlawan.event_ticketing_api.user.dto.UserPasswordUpdateRequest;
import com.junaldadlawan.event_ticketing_api.user.dto.RegisterRequest;
import com.junaldadlawan.event_ticketing_api.user.dto.UserSelfUpdateRequest;
import com.junaldadlawan.event_ticketing_api.user.dto.UserUpdateRequest;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
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

    private static final String EMAIL_TAKEN = "An account with this email already exists";

    @Override
    public User register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException(EMAIL_TAKEN);
        }
        User newUser = User.builder().firstName(request.firstName())
                .middleName(blankToNull(request.middleName())).lastName(request.lastName()).birthDate(request.birthDate())
                .phoneNumber(blankToNull(request.phoneNumber())).email(request.email()).passwordHash(passwordEncoder.encode(request.passwordHash())).role(Role.CUSTOMER).build();
        User saved = userRepository.save(newUser);
        // Self-registration always creates a CUSTOMER; the role is logged so a change to that stays visible.
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

        applyNames(user, request.firstName(), request.middleName(), request.lastName());
        if (request.birthDate() != null) {
            user.setBirthDate(request.birthDate());
        }
        if (request.phoneNumber() != null) {
            user.setPhoneNumber(blankToNull(request.phoneNumber()));
        }

        if (request.email() != null) {
            requireEmailFree(request.email(), user);
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
        applyNames(user, request.firstName(), request.middleName(), request.lastName());
        if (request.birthDate() != null) {
            user.setBirthDate(request.birthDate());
        }
        if (request.phoneNumber() != null) {
            user.setPhoneNumber(blankToNull(request.phoneNumber()));
        }
        if (request.email() != null) {
            requireEmailFree(request.email(), user);
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

    /** null = unchanged; for the middle name an empty value removes it (first and last names are validated non-empty). */
    private static void applyNames(User user, String firstName, String middleName, String lastName) {
        if (firstName != null) {
            user.setFirstName(firstName.trim());
        }
        if (middleName != null) {
            user.setMiddleName(blankToNull(middleName));
        }
        if (lastName != null) {
            user.setLastName(lastName.trim());
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Keeping your own current email is fine; another account's (any case) is a 409. */
    private void requireEmailFree(String email, User user) {
        if (!email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmailIgnoreCaseAndIdNot(email, user.getId())) {
            throw new ConflictException(EMAIL_TAKEN);
        }
    }

    public User getOrThrow(UUID id) {
        // A soft-deleted account no longer exists as far as the API is concerned (404, not a live profile).
        return userRepository.findById(id).filter(user -> user.getDeletedAt() == null).orElseThrow(() -> new ResourceNotFoundException("User " + id + " not found"));
    }

}
