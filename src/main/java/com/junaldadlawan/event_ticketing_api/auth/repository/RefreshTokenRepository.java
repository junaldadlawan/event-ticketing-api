package com.junaldadlawan.event_ticketing_api.auth.repository;

import com.junaldadlawan.event_ticketing_api.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
}
