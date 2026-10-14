package com.junaldadlawan.event_ticketing_api.user.enums;

public enum Role {
    CUSTOMER,
    ADMIN,
    /** The one highest account: every admin power, and the only one who may suspend, remove or demote an ADMIN. Exactly one exists (V38). */
    SUPER_ADMIN
}
