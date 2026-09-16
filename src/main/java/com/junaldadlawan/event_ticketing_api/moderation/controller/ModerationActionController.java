package com.junaldadlawan.event_ticketing_api.moderation.controller;

import com.junaldadlawan.event_ticketing_api.common.dto.PageResponse;
import com.junaldadlawan.event_ticketing_api.moderation.dto.ModerationActionCreateRequest;
import com.junaldadlawan.event_ticketing_api.moderation.dto.ModerationActionResponse;
import com.junaldadlawan.event_ticketing_api.moderation.enums.ModerationTargetType;
import com.junaldadlawan.event_ticketing_api.moderation.service.ModerationActionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * {@code /api/v1/admin/moderation-actions} - restricted at the HTTP layer by
 * SecurityConfig's {@code /api/v1/admin/**} {@code hasRole("ADMIN")}
 * matcher; {@link ModerationActionService} additionally calls {@code
 * accessGuard.requireAdmin()} itself, same defense-in-depth idiom already
 * used elsewhere in this codebase (e.g. scanner-device endpoints).
 */
@RestController
@RequestMapping("/api/v1/admin/moderation-actions")
@RequiredArgsConstructor
public class ModerationActionController {

    private final ModerationActionService moderationActionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ModerationActionResponse create(@Valid @RequestBody ModerationActionCreateRequest request) {
        return moderationActionService.create(request);
    }

    @GetMapping
    public PageResponse<ModerationActionResponse> list(@RequestParam(required = false) ModerationTargetType targetType,
                                                         @RequestParam(required = false) UUID targetId,
                                                         @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(moderationActionService.list(targetType, targetId, pageable));
    }
}
