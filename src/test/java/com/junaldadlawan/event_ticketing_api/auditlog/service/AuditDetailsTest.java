package com.junaldadlawan.event_ticketing_api.auditlog.service;

import com.junaldadlawan.event_ticketing_api.common.entity.Money;
import com.junaldadlawan.event_ticketing_api.event.dto.EventUpdateRequest;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** BR-EVENT-006: what goes into an audit entry's details. */
class AuditDetailsTest {

    private static Event event(String title, String category, String description, List<String> images) {
        return Event.builder().title(title).category(category).description(description).images(new ArrayList<>(images)).build();
    }

    @Test
    void changes_listOnlyTheFieldsThatDiffer_withFromAndTo() {
        Event event = event("Event Sample", "Music", "same", List.of("a"));
        Map<String, Object> before = AuditDetails.snapshot(event, EventUpdateRequest.class);
        event.setTitle("Event Sample 2026");
        event.setCategory("Charity");

        Map<String, Object> changes = AuditDetails.changes(before, AuditDetails.snapshot(event, EventUpdateRequest.class));

        assertThat(changes).isEqualTo(Map.of("changes", List.of(
                Map.of("field", "title", "from", "Event Sample", "to", "Event Sample 2026"),
                Map.of("field", "category", "from", "Music", "to", "Charity"))));
    }

    @Test
    void changes_nothingChanged_isNull() {
        Event event = event("Same", "Music", "same", List.of("a"));
        Map<String, Object> before = AuditDetails.snapshot(event, EventUpdateRequest.class);

        assertThat(AuditDetails.changes(before, AuditDetails.snapshot(event, EventUpdateRequest.class))).isNull();
    }

    @Test
    void listsAreCounts_longTextIsCapped_andTheSnapshotDoesNotFollowLaterEdits() {
        Event event = event("T", "Music", "short", List.of("a"));
        Map<String, Object> before = AuditDetails.snapshot(event, EventUpdateRequest.class);
        event.getImages().add("b");
        event.getImages().add("c");
        event.setDescription("x".repeat(2_000));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> changes = (List<Map<String, Object>>) AuditDetails.changes(before,
                AuditDetails.snapshot(event, EventUpdateRequest.class)).get("changes");

        assertThat(changes).anySatisfy(change -> assertThat(change)
                .containsEntry("field", "images").containsEntry("from", 1).containsEntry("to", 3));
        assertThat(changes).anySatisfy(change -> {
            assertThat(change).containsEntry("field", "description").containsEntry("from", "short");
            assertThat((String) change.get("to")).hasSize(AuditDetails.MAX_TEXT);
        });
    }

    @Test
    void moneyBecomesAmountAndCurrency_datesIsoStrings_decimalsCompareByValue() {
        assertThat(AuditDetails.normalize(Money.builder().amount(1500).currency("PHP").build()))
                .isEqualTo(Map.of("amount", 1500L, "currency", "PHP"));
        assertThat(AuditDetails.normalize(Instant.parse("2026-10-20T08:00:00Z"))).isEqualTo("2026-10-20T08:00:00Z");
        assertThat(AuditDetails.normalize(new BigDecimal("10.00"))).isEqualTo(AuditDetails.normalize(new BigDecimal("10")));
    }

    /** Field names that look secret are never recorded, whatever the request declares. */
    record SecretishRequest(String name, String password, String accessToken, String cardNumber, String securityCode) {
    }

    @Test
    void detailsNeverContainASecretField() {
        SecretishRequest source = new SecretishRequest("VIP", "hunter2", "tok_123", "4242424242424242", "123");

        assertThat(AuditDetails.snapshot(source, SecretishRequest.class)).containsOnlyKeys("name");
    }
}
