package com.junaldadlawan.event_ticketing_api.user.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.user.dto.UserPasswordUpdateRequest;
import com.junaldadlawan.event_ticketing_api.user.dto.UserRequest;
import com.junaldadlawan.event_ticketing_api.user.dto.UserSelfUpdateRequest;
import com.junaldadlawan.event_ticketing_api.user.dto.UserUpdateRequest;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private OrganizationAccessGuard accessGuard;

    private UserServiceImpl userService;

    private UUID userId;
    private User existingUser;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, passwordEncoder, accessGuard);
        userId = UUID.randomUUID();
        existingUser = User.builder()
                .id(userId)
                .name("Jane Doe")
                .email("jane@example.com")
                .passwordHash("old-hash")
                .role(Role.CUSTOMER)
                .build();
        lenient().when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void register_hashesPasswordBeforeSaving() {
        UserRequest request = new UserRequest(
                "Jane Doe", "jane@example.com", "plainTextPassword", Role.CUSTOMER,
                OffsetDateTime.now(), OffsetDateTime.now(), null, null);
        when(passwordEncoder.encode("plainTextPassword")).thenReturn("hashed-value");

        User saved = userService.register(request);

        assertThat(saved.getPasswordHash()).isEqualTo("hashed-value");
        assertThat(saved.getPasswordHash()).isNotEqualTo("plainTextPassword");
    }

    @Test
    void register_mapsNameEmailRole() {
        UserRequest request = new UserRequest(
                "Jane Doe", "jane@example.com", "plainTextPassword", Role.ADMIN,
                OffsetDateTime.now(), OffsetDateTime.now(), null, null);
        when(passwordEncoder.encode(any())).thenReturn("hashed-value");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        userService.register(request);

        User toSave = captor.getValue();
        assertThat(toSave.getName()).isEqualTo("Jane Doe");
        assertThat(toSave.getEmail()).isEqualTo("jane@example.com");
        assertThat(toSave.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void getUsers_excludesSoftDeletedRows() {
        when(userRepository.findByDeletedAtIsNull()).thenReturn(List.of(existingUser));

        List<User> result = userService.getUsers();

        assertThat(result).containsExactly(existingUser);
        verify(userRepository, times(1)).findByDeletedAtIsNull();
        verify(userRepository, never()).findAll();
    }

    // ---- update(): partial-update semantics (admin path) ----

    @Test
    void update_allFieldsPresent_updatesNameEmailRole() {
        UserUpdateRequest request = new UserUpdateRequest("New Name", "new@example.com", Role.ADMIN);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));

        User updated = userService.update(userId, request);

        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getEmail()).isEqualTo("new@example.com");
        assertThat(updated.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void update_nameOnly_leavesEmailAndRoleUnchanged() {
        UserUpdateRequest request = new UserUpdateRequest("New Name", null, null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));

        User updated = userService.update(userId, request);

        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getEmail()).isEqualTo("jane@example.com");
        assertThat(updated.getRole()).isEqualTo(Role.CUSTOMER);
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void update_emailOnly_leavesNameAndRoleUnchanged() {
        UserUpdateRequest request = new UserUpdateRequest(null, "new@example.com", null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));

        User updated = userService.update(userId, request);

        assertThat(updated.getName()).isEqualTo("Jane Doe");
        assertThat(updated.getEmail()).isEqualTo("new@example.com");
        assertThat(updated.getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void update_roleOnly_leavesNameAndEmailUnchanged() {
        UserUpdateRequest request = new UserUpdateRequest(null, null, Role.ADMIN);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));

        User updated = userService.update(userId, request);

        assertThat(updated.getName()).isEqualTo("Jane Doe");
        assertThat(updated.getEmail()).isEqualTo("jane@example.com");
        assertThat(updated.getRole()).isEqualTo(Role.ADMIN);
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void update_unknownId_throwsResourceNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        UserUpdateRequest request = new UserUpdateRequest("New Name", "new@example.com", Role.ADMIN);
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.update(unknownId, request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(userRepository, never()).save(any());
    }

    // ---- getSelf() / updateSelf(): self-service path ----

    @Test
    void getSelf_returnsCurrentUser() {
        when(accessGuard.currentUserId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));

        User result = userService.getSelf();

        assertThat(result).isEqualTo(existingUser);
    }

    @Test
    void updateSelf_nameOnly_leavesEmailUnchanged() {
        when(accessGuard.currentUserId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        UserSelfUpdateRequest request = new UserSelfUpdateRequest("New Name", null);

        User updated = userService.updateSelf(request);

        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getEmail()).isEqualTo("jane@example.com");
        assertThat(updated.getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void updateSelf_emailOnly_leavesNameUnchanged() {
        when(accessGuard.currentUserId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        UserSelfUpdateRequest request = new UserSelfUpdateRequest(null, "new@example.com");

        User updated = userService.updateSelf(request);

        assertThat(updated.getName()).isEqualTo("Jane Doe");
        assertThat(updated.getEmail()).isEqualTo("new@example.com");
    }

    // ---- updateSelfPassword(): self-service password change ----

    @Test
    void updateSelfPassword_correctCurrentPassword_encodesAndSavesNewPassword() {
        when(accessGuard.currentUserId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("oldPlainTextPassword", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("newPlainTextPassword")).thenReturn("new-hashed-value");
        UserPasswordUpdateRequest request = new UserPasswordUpdateRequest("oldPlainTextPassword", "newPlainTextPassword");

        User updated = userService.updateSelfPassword(request);

        assertThat(updated.getPasswordHash()).isEqualTo("new-hashed-value");
        verify(userRepository).save(existingUser);
    }

    @Test
    void updateSelfPassword_incorrectCurrentPassword_throwsBadRequest_doesNotSave() {
        when(accessGuard.currentUserId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("wrongPassword", "old-hash")).thenReturn(false);
        UserPasswordUpdateRequest request = new UserPasswordUpdateRequest("wrongPassword", "newPlainTextPassword");

        assertThatThrownBy(() -> userService.updateSelfPassword(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Current password is incorrect");
        verify(userRepository, never()).save(any());
    }

    @Test
    void delete_marksUserDeletedAndSaves() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));

        userService.delete(userId);

        assertThat(existingUser.getDeletedAt()).isNotNull();
        verify(userRepository).save(existingUser);
    }

    @Test
    void delete_unknownId_throwsResourceNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.delete(unknownId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void getOrThrow_existingId_returnsUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));

        User found = userService.getOrThrow(userId);

        assertThat(found).isEqualTo(existingUser);
    }

    @Test
    void getOrThrow_unknownId_throwsResourceNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getOrThrow(unknownId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }
}
