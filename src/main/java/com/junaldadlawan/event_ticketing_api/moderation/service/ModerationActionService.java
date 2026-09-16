package com.junaldadlawan.event_ticketing_api.moderation.service;

import com.junaldadlawan.event_ticketing_api.moderation.dto.ModerationActionCreateRequest;
import com.junaldadlawan.event_ticketing_api.moderation.dto.ModerationActionResponse;
import com.junaldadlawan.event_ticketing_api.moderation.enums.ModerationTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ModerationActionService {

    /** {@code POST /admin/moderation-actions} (BR-ADMIN-002) - admin only. */
    ModerationActionResponse create(ModerationActionCreateRequest request);

    /** {@code GET /admin/moderation-actions?targetType=&targetId=} - admin only, both filters optional. */
    Page<ModerationActionResponse> list(ModerationTargetType targetTypeFilter, UUID targetIdFilter, Pageable pageable);
}
