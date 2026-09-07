package com.junaldadlawan.event_ticketing_api.user.controller;

import com.junaldadlawan.event_ticketing_api.user.dto.UserPasswordUpdateRequest;
import com.junaldadlawan.event_ticketing_api.user.dto.UserRequest;
import com.junaldadlawan.event_ticketing_api.user.dto.UserResponse;
import com.junaldadlawan.event_ticketing_api.user.dto.UserUpdateRequest;
import com.junaldadlawan.event_ticketing_api.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@RequestBody UserRequest request) {
        return UserResponse.from(userService.register(request));
    }

    @GetMapping
    public List<UserResponse> getUsers() {
        return userService.getUsers().stream()
                .map(UserResponse::from)
                .toList();
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UserUpdateRequest request) {
        return UserResponse.from(userService.update(id, request));
    }

    @PutMapping("/{id}/password")
    public UserResponse updatePassword(@PathVariable UUID id, @Valid @RequestBody UserPasswordUpdateRequest request) {
        return UserResponse.from(userService.updatePassword(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
