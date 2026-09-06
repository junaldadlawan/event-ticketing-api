package com.junaldadlawan.event_ticketing_api.user.controller;

import com.junaldadlawan.event_ticketing_api.user.dto.UserRequest;
import com.junaldadlawan.event_ticketing_api.user.dto.UserResponse;
import com.junaldadlawan.event_ticketing_api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
}
