package com.junaldadlawan.event_ticketing_api.user.entity;

import com.junaldadlawan.event_ticketing_api.common.entity.Auditable;
import com.junaldadlawan.event_ticketing_api.user.enums.AccountStatus;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "users")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Size(max = 150)
    @NotNull
    @Column(name = "first_name", nullable = false, length = 150)
    private String firstName;

    @Size(max = 150)
    @Column(name = "middle_name", length = 150)
    private String middleName;

    /** Empty (never null) for an account that had a single-word name before the name was split. */
    @Size(max = 150)
    @NotNull
    @Column(name = "last_name", nullable = false, length = 150)
    private String lastName;

    /** First, middle and last name joined with single spaces, for places that print one name (the ticket artifact). */
    public String fullName() {
        return java.util.stream.Stream.of(firstName, middleName, lastName)
                .filter(part -> part != null && !part.isBlank())
                .collect(java.util.stream.Collectors.joining(" "));
    }

    /** Null for accounts created before the date of birth was collected. */
    @Column(name = "birth_date")
    private java.time.LocalDate birthDate;

    /** International format (+<digits>); null when none was given. */
    @Size(max = 20)
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Size(max = 255)
    @NotNull
    @Column(name = "email", nullable = false)
    private String email;

    @Size(max = 255)
    @NotNull
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    /** Phase 12 (BR-ADMIN-002) - real, enforced account state; see {@code AuthServiceImpl.login}. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    /** URL of the profile picture (an image uploaded through POST /api/v1/uploads); null = none. */
    @Size(max = 500)
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

}