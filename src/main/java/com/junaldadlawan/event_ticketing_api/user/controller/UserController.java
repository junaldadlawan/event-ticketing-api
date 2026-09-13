package com.junaldadlawan.event_ticketing_api.user.controller;

import com.junaldadlawan.event_ticketing_api.user.dto.UserPasswordUpdateRequest;
import com.junaldadlawan.event_ticketing_api.user.dto.UserResponse;
import com.junaldadlawan.event_ticketing_api.user.dto.UserSelfUpdateRequest;
import com.junaldadlawan.event_ticketing_api.user.dto.UserUpdateRequest;
import com.junaldadlawan.event_ticketing_api.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public List<UserResponse> getUsers() {
        return userService.getUsers().stream()
                .map(UserResponse::from)
                .toList();
    }

    /** Must precede {@code /{id}} in {@code SecurityConfig} matcher order - any authenticated caller's own profile, not admin-only. */
    @GetMapping("/me")
    public UserResponse getSelf() {
        return UserResponse.from(userService.getSelf());
    }

    /** Same "any authenticated caller" gate as {@link #getSelf} - deliberately has no {@code role} field to set (see {@link UserSelfUpdateRequest}). */
    @PatchMapping("/me")
    public UserResponse updateSelf(@Valid @RequestBody UserSelfUpdateRequest request) {
        return UserResponse.from(userService.updateSelf(request));
    }

    @PatchMapping("/{id}")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UserUpdateRequest request) {
        return UserResponse.from(userService.update(id, request));
    }

    @PatchMapping("/{id}/password")
    public UserResponse updatePassword(@PathVariable UUID id, @Valid @RequestBody UserPasswordUpdateRequest request) {
        return UserResponse.from(userService.updatePassword(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
