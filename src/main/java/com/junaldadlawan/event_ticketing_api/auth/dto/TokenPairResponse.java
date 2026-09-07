package com.junaldadlawan.event_ticketing_api.auth.dto;

public record TokenPairResponse(String accessToken, String refreshToken, long expiresIn) {
}
