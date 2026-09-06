package com.junaldadlawan.event_ticketing_api.user.service;

import com.junaldadlawan.event_ticketing_api.user.dto.UserRequest;
import com.junaldadlawan.event_ticketing_api.user.dto.UserResponse;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public User register(UserRequest request) {
        User newUser = User.builder()
                .name(request.name())
                .email(request.email())
                .passwordHash(request.passwordHash())
                .role(request.role())
                .build();
        return userRepository.save(newUser);
    }

    @Override
    public UserResponse login(UserRequest request) {
        return null;
    }

    @Override
    public List<User> getUsers() {
        return userRepository.findAll();
    }
}
