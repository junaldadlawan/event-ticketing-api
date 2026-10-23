package com.junaldadlawan.event_ticketing_api.organization.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * BR-ORG-008: what an ORGANIZER may do in their organization, chosen per member by the owner (or a TEAM organizer).
 * The OWNER implicitly holds all of them; CHECK_IN_STAFF is unaffected. Held permissions only count while the member
 * holds ORGANIZER.
 */
public enum OrganizationPermission {
    /** create/edit/publish/cancel events, ticket types, seat maps, venues, ticket designs */
    EVENTS,
    /** create/edit/pause/delete promo codes */
    PROMO_CODES,
    /** check-in config and scanner devices */
    CHECK_IN,
    /** view orders, handle refunds and refund/resale policies */
    ORDERS_REFUNDS,
    /** payout accounts, payout cards and payouts */
    FINANCE,
    /** sales/audience analytics and activity logs */
    ANALYTICS,
    /** add members, change member roles/permissions, remove members */
    TEAM,
    /**
     * publish the scanner app (upload and remove versions). The app is shared by every organization: whoever holds this in ANY organization can
     * publish the version all check-in staff download. Grantable to an ORGANIZER or to CHECK_IN_STAFF; the owner always holds it.
     */
    SCANNER_APP;

    /** An organizer's permissions when none are chosen: everything but TEAM and SCANNER_APP, i.e. what an organizer could always do. */
    public static Set<OrganizationPermission> organizerDefaults() {
        return EnumSet.complementOf(EnumSet.of(TEAM, SCANNER_APP));
    }
}
