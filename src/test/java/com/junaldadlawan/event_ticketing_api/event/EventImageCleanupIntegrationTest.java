package com.junaldadlawan.event_ticketing_api.event;

import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.event.service.DraftEventCleanupService;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.upload.storage.FileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The picture files of an event that is deleted go away with it, unless another event still uses the same file. Uses the draft auto-delete job
 * (the scheduler is off in tests) limited to this test's own events, and the configured storage with files named like real uploads.
 */
@SpringBootTest
class EventImageCleanupIntegrationTest {

    @Autowired
    private DraftEventCleanupService cleanup;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private FileStorage storage;
    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> eventIds = new ArrayList<>();
    private final List<String> keys = new ArrayList<>();
    private UUID org;

    @BeforeEach
    void setUp() {
        org = organizationRepository.save(Organization.builder().name("Image Cleanup Org " + UUID.randomUUID()).status(OrganizationStatus.APPROVED)
                .ownerId(UUID.randomUUID()).documents(List.of()).build()).getId();
    }

    @AfterEach
    void tearDown() {
        keys.forEach(storage::delete);
        eventIds.forEach(id -> {
            jdbc.update("delete from notifications where event_id = ?", id);
            jdbc.update("delete from audit_log_entries where event_id = ?", id);
            eventRepository.deleteById(id);
        });
        organizationRepository.deleteById(org);
    }

    private String upload() {
        String key = "events/" + UUID.randomUUID() + ".png";
        storage.put(key, new byte[] {1, 2, 3}, "image/png");
        keys.add(key);
        return "http://localhost/api/v1/uploads/files/" + key;
    }

    private UUID pastDraft(String... images) {
        Instant start = Instant.now().minus(Duration.ofHours(3));
        UUID id = eventRepository.save(Event.builder().organizationId(org).title("Image Cleanup " + UUID.randomUUID()).description("d").category("music")
                .status(EventStatus.DRAFT).ticketPrefix(freePrefix()).startAt(start).endAt(start.plus(2, ChronoUnit.HOURS)).timezone("UTC")
                .images(new ArrayList<>(List.of(images))).build()).getId();
        eventIds.add(id);
        return id;
    }

    private String freePrefix() {
        String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        java.util.Random random = new java.util.Random();
        while (true) {
            String candidate = "" + letters.charAt(random.nextInt(26)) + letters.charAt(random.nextInt(26)) + letters.charAt(random.nextInt(26));
            if (jdbc.queryForObject("select count(*) from events where ticket_prefix = ?", Integer.class, candidate) == 0) {
                return candidate;
            }
        }
    }

    private static String key(String url) {
        return url.substring(url.indexOf("events/"));
    }

    @Test
    void aDeletedEventsPictureIsRemoved_butAFileAnotherEventStillUsesStays() {
        String own = upload();
        String shared = upload();
        UUID first = pastDraft(own, shared);
        UUID second = pastDraft(shared);

        cleanup.run(Instant.now(), new HashSet<>(List.of(first)));

        assertThat(storage.exists(key(own))).isFalse();
        assertThat(storage.exists(key(shared))).isTrue();

        cleanup.run(Instant.now(), new HashSet<>(List.of(second)));

        assertThat(storage.exists(key(shared))).isFalse();
    }

    @Test
    void aLinkToAnotherServer_isNeverTouched() {
        String foreign = "https://example.com/other/events/" + UUID.randomUUID() + ".png";
        UUID id = pastDraft(foreign);

        DraftEventCleanupService.Result result = cleanup.run(Instant.now(), new HashSet<>(List.of(id)));

        assertThat(result.deleted()).isEqualTo(1);
    }
}
