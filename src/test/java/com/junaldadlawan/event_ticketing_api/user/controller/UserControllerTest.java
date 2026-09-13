package com.junaldadlawan.event_ticketing_api.user.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.checkin.security.DeviceAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ConflictException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import com.junaldadlawan.event_ticketing_api.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for the controller's own behavior (request validation,
 * response mapping, status codes). Security-filter enforcement (401/403)
 * is exercised separately in {@code UserSecurityIntegrationTest}, since a
 * WebMvcTest slice would otherwise need the whole JWT/security bean graph
 * just to stand up.
 */
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private DeviceAuthenticationFilter deviceAuthenticationFilter;

    @Test
    void getUsers_returnsMappedList() throws Exception {
        User user = User.builder()
                .id(UUID.randomUUID())
                .name("Jane Doe")
                .email("jane@example.com")
                .passwordHash("hash")
                .role(Role.CUSTOMER)
                .build();
        when(userService.getUsers()).thenReturn(List.of(user));

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("jane@example.com"))
                .andExpect(jsonPath("$[0].role").value("CUSTOMER"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void update_validRequest_returnsUpdatedUser() throws Exception {
        UUID id = UUID.randomUUID();
        User updated = User.builder()
                .id(id)
                .name("New Name")
                .email("new@example.com")
                .passwordHash("hash")
                .role(Role.ADMIN)
                .build();
        when(userService.update(eq(id), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/users/{id}", id)
                        .contentType("application/json")
                        .content("""
                                {"name":"New Name","email":"new@example.com","role":"ADMIN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    /**
     * {@code UserUpdateRequest} is a partial update - every field is
     * optional (null means leave unchanged), so omitting {@code name} is
     * NOT a validation error. Confirms the request reaches the service with
     * a null name rather than being rejected at the {@code @Valid} layer.
     */
    @Test
    void update_omittedField_returns200_notValidationError() throws Exception {
        UUID id = UUID.randomUUID();
        User updated = User.builder()
                .id(id).name("Jane Doe").email("new@example.com").passwordHash("hash").role(Role.ADMIN).build();
        when(userService.update(eq(id), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/users/{id}", id)
                        .contentType("application/json")
                        .content("""
                                {"email":"new@example.com","role":"ADMIN"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void update_emptyBody_returns200_leavesEverythingUnchanged() throws Exception {
        UUID id = UUID.randomUUID();
        User unchanged = User.builder()
                .id(id).name("Jane Doe").email("jane@example.com").passwordHash("hash").role(Role.CUSTOMER).build();
        when(userService.update(eq(id), any())).thenReturn(unchanged);

        mockMvc.perform(patch("/api/v1/users/{id}", id)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("jane@example.com"));
    }

    @Test
    void update_blankName_returns400() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.update(eq(id), any())).thenThrow(new BadRequestException("Name must not be blank"));

        mockMvc.perform(patch("/api/v1/users/{id}", id)
                        .contentType("application/json")
                        .content("""
                                {"name":"   "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_duplicateEmail_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.update(eq(id), any())).thenThrow(new ConflictException("Email already in use"));

        mockMvc.perform(patch("/api/v1/users/{id}", id)
                        .contentType("application/json")
                        .content("""
                                {"email":"taken@example.com"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void update_unknownId_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.update(eq(id), any()))
                .thenThrow(new ResourceNotFoundException("User " + id + " not found"));

        mockMvc.perform(patch("/api/v1/users/{id}", id)
                        .contentType("application/json")
                        .content("""
                                {"name":"New Name","email":"new@example.com","role":"ADMIN"}
                                """))
                .andExpect(status().isNotFound());
    }

    // ---- GET /users/me, PATCH /users/me ----

    @Test
    void getSelf_returnsCallersOwnProfile() throws Exception {
        User self = User.builder()
                .id(UUID.randomUUID()).name("Jane Doe").email("jane@example.com").passwordHash("hash").role(Role.CUSTOMER).build();
        when(userService.getSelf()).thenReturn(self);

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void updateSelf_nameOnly_returnsUpdatedProfile() throws Exception {
        User updated = User.builder()
                .id(UUID.randomUUID()).name("New Name").email("jane@example.com").passwordHash("hash").role(Role.CUSTOMER).build();
        when(userService.updateSelf(any())).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType("application/json")
                        .content("""
                                {"name":"New Name"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"));
    }

    @Test
    void updateSelf_duplicateEmail_returns409() throws Exception {
        when(userService.updateSelf(any())).thenThrow(new ConflictException("Email already in use"));

        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType("application/json")
                        .content("""
                                {"email":"taken@example.com"}
                                """))
                .andExpect(status().isConflict());
    }

    /**
     * {@code UserSelfUpdateRequest} has no {@code role} field at all - a
     * client sending one is simply ignored by Jackson deserialization, not
     * an error, and it can never reach {@code userService.updateSelf}.
     */
    @Test
    void updateSelf_roleFieldInBody_isIgnored_notAnError() throws Exception {
        User updated = User.builder()
                .id(UUID.randomUUID()).name("Jane Doe").email("jane@example.com").passwordHash("hash").role(Role.CUSTOMER).build();
        when(userService.updateSelf(any())).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType("application/json")
                        .content("""
                                {"role":"ADMIN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void updatePassword_validRequest_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        User updated = User.builder()
                .id(id)
                .name("Jane Doe")
                .email("jane@example.com")
                .passwordHash("new-hash")
                .role(Role.CUSTOMER)
                .build();
        when(userService.updatePassword(eq(id), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/users/{id}/password", id)
                        .contentType("application/json")
                        .content("""
                                {"passwordHash":"newPlainTextPassword"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void updatePassword_missingPasswordField_returns400() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/users/{id}/password", id)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Known gap, not a regression: {@code UserPasswordUpdateRequest.passwordHash}
     * is only {@code @NotNull}, not {@code @NotBlank} — an empty string
     * currently passes validation and reaches the service. Documenting the
     * real behavior rather than asserting the (currently false) ideal.
     */
    @Test
    void updatePassword_blankPassword_currentlyAcceptedByValidation() throws Exception {
        UUID id = UUID.randomUUID();
        User updated = User.builder()
                .id(id)
                .name("Jane Doe")
                .email("jane@example.com")
                .passwordHash("hash-of-empty-string")
                .role(Role.CUSTOMER)
                .build();
        when(userService.updatePassword(eq(id), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/users/{id}/password", id)
                        .contentType("application/json")
                        .content("""
                                {"passwordHash":""}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void delete_existingUser_returns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/users/{id}", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_unknownId_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("User " + id + " not found"))
                .when(userService).delete(id);

        mockMvc.perform(delete("/api/v1/users/{id}", id))
                .andExpect(status().isNotFound());
    }
}
