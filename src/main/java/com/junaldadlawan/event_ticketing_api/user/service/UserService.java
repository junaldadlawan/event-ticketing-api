package com.junaldadlawan.event_ticketing_api.user.service;

import com.junaldadlawan.event_ticketing_api.user.dto.UserPasswordUpdateRequest;
import com.junaldadlawan.event_ticketing_api.user.dto.UserRequest;
import com.junaldadlawan.event_ticketing_api.user.dto.UserResponse;
import com.junaldadlawan.event_ticketing_api.user.dto.UserSelfUpdateRequest;
import com.junaldadlawan.event_ticketing_api.user.dto.UserUpdateRequest;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

public interface UserService {
    User register(UserRequest request);
    List<User> getUsers();
    User getSelf();
    User update(UUID id, UserUpdateRequest request);
    User updateSelfPassword(UserPasswordUpdateRequest request);
    void delete(UUID id);
    User updateSelf(UserSelfUpdateRequest request);

}
