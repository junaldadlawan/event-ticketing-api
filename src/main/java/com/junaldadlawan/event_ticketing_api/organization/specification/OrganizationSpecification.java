package com.junaldadlawan.event_ticketing_api.organization.specification;

import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

@NoArgsConstructor
public final class OrganizationSpecification {

    public static Specification<Organization> hasStatus(OrganizationStatus status) {
        return (root, query, criteriaBuilder) -> status != null
                ? criteriaBuilder.equal(root.get("status"), status)
                : null;
    }

    public static Specification<Organization> notDeleted() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.isNull(root.get("deletedAt"));
    }
}
