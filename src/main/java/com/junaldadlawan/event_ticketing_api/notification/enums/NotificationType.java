package com.junaldadlawan.event_ticketing_api.notification.enums;

/** BR-NOTIFY-001's full list. Not every value has a wired trigger yet — see the roadmap for which. */
public enum NotificationType {
    ORDER_CONFIRMATION,
    PAYMENT_RECEIPT,
    EVENT_REMINDER,
    EVENT_CHANGE,
    EVENT_CANCELLATION,
    WAITLIST_AVAILABILITY,
    REFUND_CONFIRMATION,
    DISPUTE_RESOLVED,
    ACCOUNT_MODERATION_ACTION,
    // BR-NOTIFY-002
    GATE_OPEN,
    EVENT_STARTING,
    REVIEW_REQUEST,
    // BR-EVENT-007: an unpublished draft is about to be deleted / was deleted
    EVENT_DRAFT_WARNING,
    EVENT_DRAFT_DELETED,
    // BR-ORG-009: an organization's change request (to the admins) and its decision (to the requester)
    ORGANIZATION_CHANGE_REQUESTED,
    ORGANIZATION_CHANGE_DECIDED,
    // BR-ORG-010: an admin asked the applicant to revise a pending application (to the applicant) / the applicant revised it (to the admins)
    ORGANIZATION_REVISION_REQUESTED,
    ORGANIZATION_APPLICATION_REVISED
}
