package com.junaldadlawan.event_ticketing_api.auth.service;

import com.junaldadlawan.event_ticketing_api.auth.dto.AccessTokenResponse;
import com.junaldadlawan.event_ticketing_api.auth.dto.LoginRequest;
import com.junaldadlawan.event_ticketing_api.auth.dto.LogoutRequest;
import com.junaldadlawan.event_ticketing_api.auth.dto.RefreshRequest;
import com.junaldadlawan.event_ticketing_api.auth.dto.TokenPairResponse;

public interface AuthService {
    TokenPairResponse login(LoginRequest request);
    AccessTokenResponse refresh(RefreshRequest request);
    void logout(LogoutRequest request);
}
