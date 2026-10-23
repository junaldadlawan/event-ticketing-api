package com.junaldadlawan.event_ticketing_api.user.repository;

import com.junaldadlawan.event_ticketing_api.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /** Ids of the live users with one of these account roles (who to tell about something for the admins). */
    @Query("select u.id from User u where u.role in :roles and u.deletedAt is null")
    List<UUID> findIdsByRoleIn(@Param("roles") java.util.Collection<com.junaldadlawan.event_ticketing_api.user.enums.Role> roles);

    /** Locks the row so two requests for the same user create one Stripe Customer, not two. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);
}