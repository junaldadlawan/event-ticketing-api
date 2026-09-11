package com.junaldadlawan.event_ticketing_api.checkin.security;

import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Device-authenticated counterpart to {@code OrganizationAccessGuard.currentUserId()}
 * - only ever called from the three {@code deviceAuth}-only endpoints
 * ({@code SecurityConfig} already restricts those to {@code
 * ROLE_SCANNER_DEVICE}, so by the time a request reaches those services,
 * the authenticated principal IS a device id, not a user id). Deliberately
 * a separate component rather than overloading {@code
 * OrganizationAccessGuard} - conflating "current user" and "current
 * device" into one method would make it too easy for a future caller to
 * misinterpret a device id as a user id, or vice versa.
 */
@Component
public class DeviceAccessGuard {

    public UUID currentDeviceId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ForbiddenException("Device authentication required");
        }
        return UUID.fromString(authentication.getName());
    }
}
