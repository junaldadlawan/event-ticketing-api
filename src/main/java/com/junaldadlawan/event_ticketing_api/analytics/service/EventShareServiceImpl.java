package com.junaldadlawan.event_ticketing_api.analytics.service;

import com.junaldadlawan.event_ticketing_api.analytics.dto.ShareLinkOpenResponse;
import com.junaldadlawan.event_ticketing_api.analytics.dto.ShareLinkResponse;
import com.junaldadlawan.event_ticketing_api.analytics.repository.ShareCounterRepository;
import com.junaldadlawan.event_ticketing_api.analytics.repository.ShareCounterRepository.Counter;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

/**
 * Share tracking. Only events a visitor can open count or get a link: PUBLISHED, ON_SALE and SOLD_OUT. Every call counts (no deduplication), and
 * nothing about the caller is read or stored. The counters are only ever changed by the repository's atomic {@code count + 1} updates.
 */
@Service
@RequiredArgsConstructor
public class EventShareServiceImpl implements EventShareService {

    /** The statuses as stored (text) in {@code events.status}. */
    static final List<String> SHAREABLE_STATUSES = List.of("PUBLISHED", "ON_SALE", "SOLD_OUT");
    static final int CODE_LENGTH = 8;
    private static final int MAX_CODE_ATTEMPTS = 5;
    /** URL-safe and easy to read out loud: no 0/o, 1/l/i confusion. */
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";

    private final EventRepository eventRepository;
    private final ShareCounterRepository counters;
    private final SecureRandom random = new SecureRandom();

    @Override
    public void recordClick(UUID eventId, String action) {
        counters.incrementIfVisible(eventId, switch (normalized(action)) {
            case "COPY_LINK" -> Counter.COPY_LINK;
            case "QR_GENERATE" -> Counter.QR_GENERATE;
            default -> Counter.SHARE_BUTTON;
        }, SHAREABLE_STATUSES);
    }

    @Override
    public void recordBuyClick(UUID eventId, String source) {
        switch (normalized(source)) {
            case "LINK" -> counters.incrementIfVisible(eventId, Counter.LINK_BUY, SHAREABLE_STATUSES);
            case "QR" -> counters.incrementIfVisible(eventId, Counter.QR_BUY, SHAREABLE_STATUSES);
            default -> { }   // an unknown source counts nothing
        }
    }

    @Override
    public ShareLinkResponse shareLink(UUID eventId) {
        Event event = shareableEvent(eventId);
        if (event.getShareCode() != null) {
            return ShareLinkResponse.of(event.getShareCode());
        }
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            try {
                eventRepository.assignShareCode(eventId, newCode());
                break;
            } catch (DataIntegrityViolationException e) {
                // the code is taken by another event: draw another
            }
        }
        // whoever won the (conditional) assignment, the stored code is the one to hand out
        String code = eventRepository.findShareCode(eventId)
                .orElseThrow(() -> new IllegalStateException("Could not create a share code for event " + eventId));
        return ShareLinkResponse.of(code);
    }

    @Override
    public ShareLinkOpenResponse open(String code, String source) {
        Event event = eventRepository.findByShareCodeAndDeletedAtIsNull(code)
                .filter(e -> SHAREABLE_STATUSES.contains(e.getStatus().name()))
                .orElseThrow(() -> new ResourceNotFoundException("Share link not found"));
        counters.increment(event.getId(), "QR".equals(normalized(source)) ? Counter.QR_SCAN : Counter.LINK_OPEN);
        return new ShareLinkOpenResponse(event.getId());
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private Event shareableEvent(UUID eventId) {
        return eventRepository.findByIdAndDeletedAtIsNull(eventId)
                .filter(e -> SHAREABLE_STATUSES.contains(e.getStatus().name()))
                .orElseThrow(() -> new ResourceNotFoundException("Event " + eventId + " not found"));
    }

    private String newCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
