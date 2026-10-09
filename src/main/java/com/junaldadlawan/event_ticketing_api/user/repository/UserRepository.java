package com.junaldadlawan.event_ticketing_api.user.repository;

import com.junaldadlawan.event_ticketing_api.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    /** Emails are compared ignoring case, so Jane@x.com cannot be registered next to jane@x.com. Soft-deleted rows count: the database key still holds them. */
    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);
    List<User> findByDeletedAtIsNull();
}