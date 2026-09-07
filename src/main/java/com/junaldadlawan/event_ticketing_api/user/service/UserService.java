package com.junaldadlawan.event_ticketing_api.user.service;

import com.junaldadlawan.event_ticketing_api.user.dto.UserPasswordUpdateRequest;
import com.junaldadlawan.event_ticketing_api.user.dto.UserRequest;
import com.junaldadlawan.event_ticketing_api.user.dto.UserResponse;
import com.junaldadlawan.event_ticketing_api.user.dto.UserUpdateRequest;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

public interface UserService {
    User register(UserRequest request);
    List<User> getUsers();
    User update(UUID id, UserUpdateRequest request);
    User updatePassword(UUID id, UserPasswordUpdateRequest request);
    void delete(UUID id);

}
