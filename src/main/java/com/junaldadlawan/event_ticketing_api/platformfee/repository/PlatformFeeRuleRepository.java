package com.junaldadlawan.event_ticketing_api.platformfee.repository;

import com.junaldadlawan.event_ticketing_api.platformfee.entity.PlatformFeeRule;
import com.junaldadlawan.event_ticketing_api.platformfee.enums.FeeScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlatformFeeRuleRepository extends JpaRepository<PlatformFeeRule, UUID> {

    /** The live rule of an organization or event. */
    Optional<PlatformFeeRule> findByScopeAndScopeIdAndDeletedAtIsNull(FeeScope scope, UUID scopeId);

    /** The live platform-wide default (its scope id is null). */
    Optional<PlatformFeeRule> findByScopeAndScopeIdIsNullAndDeletedAtIsNull(FeeScope scope);

    List<PlatformFeeRule> findByDeletedAtIsNullOrderByScopeAscCreatedAtAsc();

    List<PlatformFeeRule> findByScopeAndDeletedAtIsNullOrderByCreatedAtAsc(FeeScope scope);
}
