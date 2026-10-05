package com.junaldadlawan.event_ticketing_api.event.specification;

import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

@NoArgsConstructor
public final class EventSpecification {

    public static Specification<Event> hasStatus(EventStatus status) {
        return (root, criteriaQuery, criteriaBuilder) -> status != null
                ? criteriaBuilder.equal(root.get("status"), status)
                : null;
    }

    public static Specification<Event> hasCategory(String category) {
        return (root, query, criteriaBuilder) -> StringUtils.hasText(category)
                ? criteriaBuilder.equal(criteriaBuilder.lower(root.get("category")), category.toLowerCase())
                :null;
    }

    public static Specification<Event> titleContains(String keyword) {
        return (root, query, criteriaBuilder) -> StringUtils.hasText(keyword)
                ? criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), "%" + keyword.toLowerCase() + "%")
                :null;
    }

    public static Specification<Event> startsAfter(Instant from) {
        return (root, query, criteriaBuilder) -> from != null ? criteriaBuilder.greaterThanOrEqualTo(root.get("startAt"), from) : null;
    }

    public static Specification<Event> startBefore(Instant to) {
        return (root, query, criteriaBuilder) -> to != null ? criteriaBuilder.lessThanOrEqualTo(root.get("startAt"), to) : null;
    }

    /** null = no restriction; empty = matches nothing. */
    public static Specification<Event> inOrganizations(Collection<UUID> organizationIds) {
        return (root, query, criteriaBuilder) -> {
            if (organizationIds == null) {
                return null;
            }
            return organizationIds.isEmpty()
                    ? criteriaBuilder.disjunction()
                    : root.get("organizationId").in(organizationIds);
        };
    }

    public static Specification<Event> notDeleted() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.isNull(root.get("deletedAt"));
    }
}
