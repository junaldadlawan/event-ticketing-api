package com.junaldadlawan.event_ticketing_api.auditlog.service;

import com.junaldadlawan.event_ticketing_api.auditlog.entity.AuditLogEntry;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;

/**
 * BR-ADMIN-007: the management tabs that have an activity log, which actions each one shows, and who may read
 * it. {@code organization} = an organization's own tab (GET /activity?organizationId=...), {@code platform} = the
 * admin's platform-wide tab (no organizationId). A null filter means the area has no such tab (400).
 */
public enum ActivityArea {

    /** Everything about events: every entry tagged with an event. */
    EVENTS(Reader.OWNER_OR_ORGANIZER,
            (root, cb) -> cb.isNotNull(root.get("eventId")),
            (root, cb) -> cb.isNotNull(root.get("eventId"))),
    TEAM(Reader.OWNER_OR_ORGANIZER,
            (root, cb) -> actions(root, cb, "organization_member.", "organization."),
            null),
    FINANCE(Reader.OWNER,
            (root, cb) -> cb.or(actions(root, cb, "payout."), cb.equal(root.get("action"), "refund.issued")),
            (root, cb) -> actions(root, cb, "payout.", "platform_fee.", "gateway_fee.")),
    SETTINGS(Reader.OWNER_OR_ORGANIZER,
            (root, cb) -> actions(root, cb, "payout_card.", "payout_account."),
            (root, cb) -> actions(root, cb, "category.", "platform_fee.", "gateway_fee.", "ticket_sale_settings.")),
    POSTS(null, null,
            (root, cb) -> actions(root, cb, "post.")),
    ORGANIZATIONS(null, null,
            (root, cb) -> cb.or(actions(root, cb, "organization.", "organization_member."), moderationOf(root, cb, "ORGANIZATION"))),
    USERS(null, null,
            (root, cb) -> cb.or(actions(root, cb, "user."), moderationOf(root, cb, "USER"))),
    DISPUTES(null, null,
            (root, cb) -> actions(root, cb, "dispute."));

    /** Who besides an admin reads an organization's tab. */
    public enum Reader { OWNER, OWNER_OR_ORGANIZER }

    @FunctionalInterface
    interface Filter {
        Predicate on(Root<AuditLogEntry> root, CriteriaBuilder cb);
    }

    private final Reader organizationReader;
    private final Filter organizationFilter;
    private final Filter platformFilter;

    ActivityArea(Reader organizationReader, Filter organizationFilter, Filter platformFilter) {
        this.organizationReader = organizationReader;
        this.organizationFilter = organizationFilter;
        this.platformFilter = platformFilter;
    }

    public boolean hasOrganizationTab() {
        return organizationFilter != null;
    }

    public boolean hasPlatformTab() {
        return platformFilter != null;
    }

    public Reader organizationReader() {
        return organizationReader;
    }

    /** This area's entries for one organization (an indexed organization_id match), or platform-wide when null. */
    public Specification<AuditLogEntry> entries(java.util.UUID organizationId) {
        if (organizationId == null) {
            return (root, query, cb) -> platformFilter.on(root, cb);
        }
        return (root, query, cb) -> cb.and(cb.equal(root.get("organizationId"), organizationId), organizationFilter.on(root, cb));
    }

    private static Predicate actions(Root<AuditLogEntry> root, CriteriaBuilder cb, String... prefixes) {
        // prefixes end in '.', so "organization." never matches "organization_member.x"; escape '_' (a LIKE wildcard)
        return cb.or(Arrays.stream(prefixes)
                .map(prefix -> cb.like(root.get("action"), prefix.replace("_", "\\_") + "%", '\\'))
                .toArray(Predicate[]::new));
    }

    private static Predicate moderationOf(Root<AuditLogEntry> root, CriteriaBuilder cb, String targetType) {
        return cb.and(cb.like(root.get("action"), "moderation.%"), cb.equal(root.get("targetType"), targetType));
    }
}
