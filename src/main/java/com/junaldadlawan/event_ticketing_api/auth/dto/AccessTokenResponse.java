package com.junaldadlawan.event_ticketing_api.auth.dto;

public record AccessTokenResponse(String accessToken, long expiresIn) {
}
