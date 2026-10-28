package com.junaldadlawan.event_ticketing_api.scannerapp;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.common.TestTicketPrefixes;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.upload.storage.FileStorage;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Check-in staff can READ their organization's events (never write), and the scanner app (Android APK) is uploaded by admins and fetched by
 * check-in staff. Real database and file storage; every row and file the tests make is removed.
 */
@SpringBootTest(properties = "app.scanner-app.max-bytes=6000")
@AutoConfigureMockMvc
class ScannerAppAndStaffAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private FileStorage storage;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;
    @Autowired
    private EventRepository eventRepository;

    private final List<UUID> orgIds = new ArrayList<>();
    private final List<UUID> eventIds = new ArrayList<>();
    private final List<OrganizationMember> members = new ArrayList<>();
    private final List<UUID> releaseIds = new ArrayList<>();

    private String admin;
    private String staffA;
    private String stranger;
    private UUID staffId;
    private UUID orgA;
    private UUID orgB;
    private UUID eventA;
    private UUID draftA;
    private UUID eventB;

    @BeforeEach
    void setUp() {
        admin = token(user(Role.ADMIN));
        User staff = user(Role.CUSTOMER);
        staffId = staff.getId();
        staffA = token(staff);
        stranger = token(user(Role.CUSTOMER));
        orgA = organization();
        orgB = organization();
        member(staffId, orgA, OrganizationRole.CHECK_IN_STAFF);
        eventA = event(orgA, EventStatus.ON_SALE, "Staff Test A");
        draftA = event(orgA, EventStatus.DRAFT, "Staff Test A Draft");
        eventB = event(orgB, EventStatus.ON_SALE, "Staff Test B");
    }

    @AfterEach
    void tearDown() {
        for (UUID id : releaseIds) {
            jdbc.queryForList("select storage_key from scanner_app_releases where id = ?", String.class, id).forEach(storage::delete);
            jdbc.update("delete from scanner_app_releases where id = ?", id);
        }
        members.forEach(member -> organizationMemberRepository.findByUserIdAndOrganizationId(member.getUserId(), member.getOrganizationId())
                .ifPresent(organizationMemberRepository::delete));
        eventIds.forEach(eventRepository::deleteById);
        orgIds.forEach(organizationRepository::deleteById);
    }

    // ---- fixtures ----

    private User user(Role role) {
        return User.builder().id(UUID.randomUUID()).email("staff-it-" + UUID.randomUUID() + "@test.local").role(role).build();
    }

    private String token(User user) {
        return "Bearer " + jwtService.generateAccessToken(user);
    }

    private UUID organization() {
        UUID id = organizationRepository.save(Organization.builder().name("Staff Test Org " + UUID.randomUUID()).status(OrganizationStatus.APPROVED)
                .ownerId(UUID.randomUUID()).documents(List.of()).build()).getId();
        orgIds.add(id);
        return id;
    }

    private void member(UUID userId, UUID organizationId, OrganizationRole role) {
        OrganizationMember member = OrganizationMember.builder().userId(userId).organizationId(organizationId).build();
        member.getRoles().add(role);
        members.add(organizationMemberRepository.save(member));
    }

    private UUID event(UUID organizationId, EventStatus status, String title) {
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        UUID id = eventRepository.save(Event.builder().organizationId(organizationId).title(title).description("d").category("music").status(status)
                .ticketPrefix(TestTicketPrefixes.free(jdbc)).startAt(start).endAt(start.plus(2, ChronoUnit.HOURS))
                .timezone("UTC").build()).getId();
        eventIds.add(id);
        return id;
    }

    private static byte[] apk(String... extraEntries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("AndroidManifest.xml"));
            zip.write("manifest".getBytes());
            zip.closeEntry();
            for (String name : extraEntries) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write("x".getBytes());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private MockMultipartHttpServletRequestBuilder upload(String caller, byte[] bytes, String versionName, String versionCode) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/admin/scanner-app")
                .file(new MockMultipartFile("file", "scanner.apk", "application/octet-stream", bytes))
                .param("versionName", versionName).param("versionCode", versionCode).param("releaseNotes", "Fixes the torch");
        return caller == null ? request : request.header("Authorization", caller);
    }

    private JsonNode uploadOk(String versionName, int versionCode) throws Exception {
        MvcResult result = mockMvc.perform(upload(admin, apk("classes.dex"), versionName, String.valueOf(versionCode))).andExpect(status().isCreated()).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        releaseIds.add(UUID.fromString(body.get("id").asText()));
        return body;
    }

    // ---- check-in staff read their organization's events ----

    @Test
    void checkInStaffListOnlyTheirOrganizationsEvents_includingDrafts() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/events/managed").param("size", "100").header("Authorization", staffA))
                .andExpect(status().isOk()).andReturn();
        String ids = result.getResponse().getContentAsString();

        assertThat(ids).contains(eventA.toString()).contains(draftA.toString()).doesNotContain(eventB.toString());
        // asking for another organization's events is refused, like for an organizer
        mockMvc.perform(get("/api/v1/events/managed").param("organizationId", orgB.toString()).header("Authorization", staffA)).andExpect(status().isForbidden());
        // someone with no role anywhere is still refused
        mockMvc.perform(get("/api/v1/events/managed").header("Authorization", stranger)).andExpect(status().isForbidden());
    }

    @Test
    void checkInStaffCanReadADraftAndItsTicketTypes_butWriteNothing() throws Exception {
        mockMvc.perform(get("/api/v1/events/{id}", draftA).header("Authorization", staffA)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/events/{id}/ticket-types", draftA).header("Authorization", staffA)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/events/{id}/check-in-config", eventA).header("Authorization", staffA)).andExpect(status().isOk());
        // a stranger still cannot see a draft
        mockMvc.perform(get("/api/v1/events/{id}", draftA).header("Authorization", stranger)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/events/{id}/ticket-types", draftA).header("Authorization", stranger)).andExpect(status().isForbidden());

        // every write stays refused to check-in staff
        mockMvc.perform(patch("/api/v1/events/{id}", eventA).header("Authorization", staffA).contentType("application/json")
                .content("{\"title\":\"Hacked\"}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/events/{id}/ticket-types", eventA).header("Authorization", staffA).contentType("application/json")
                .content("{\"name\":\"X\",\"kind\":\"GENERAL_ADMISSION\",\"price\":{\"amount\":100,\"currency\":\"PHP\"},\"quantityTotal\":10,"
                        + "\"saleStartAt\":\"2030-01-01T00:00:00Z\",\"saleEndAt\":\"2030-02-01T00:00:00Z\"}")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/events/{id}", eventA).header("Authorization", staffA)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/events").header("Authorization", staffA).contentType("application/json")
                .content("{\"organizationId\":\"" + orgA + "\",\"title\":\"Nope\",\"description\":\"d\",\"category\":\"music\","
                        + "\"startAt\":\"2030-01-01T00:00:00Z\",\"endAt\":\"2030-01-01T02:00:00Z\",\"timezone\":\"UTC\"}")).andExpect(status().isForbidden());
    }

    // ---- the scanner app ----

    @Test
    void anAdminUploadsAnApk_andItIsRecordedWithItsChecksum() throws Exception {
        MvcResult result = mockMvc.perform(upload(admin, apk("classes.dex"), "1.4.2", "142")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionName").value("1.4.2")).andExpect(jsonPath("$.versionCode").value(142))
                .andExpect(jsonPath("$.releaseNotes").value("Fixes the torch")).andExpect(jsonPath("$.size").isNumber())
                .andExpect(jsonPath("$.sha256").isString()).andExpect(jsonPath("$.uploadedAt").exists()).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        releaseIds.add(UUID.fromString(body.get("id").asText()));

        assertThat(body.get("downloadUrl").asText()).isEqualTo("/api/v1/scanner-app/" + body.get("id").asText() + "/download");
        assertThat(jdbc.queryForObject("select count(*) from scanner_app_releases where id = ?", Integer.class, UUID.fromString(body.get("id").asText()))).isEqualTo(1);
    }

    @Test
    void uploadsThatAreNotAnApk_orAreTooLarge_orRepeatAVersionCode_areRefused() throws Exception {
        mockMvc.perform(upload(admin, "just text, not a zip".getBytes(), "1.0", "9001")).andExpect(status().isBadRequest());
        // a ZIP without an AndroidManifest.xml (a jar, a document...) is not an APK
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("readme.txt"));
            zip.write("hi".getBytes());
            zip.closeEntry();
        }
        mockMvc.perform(upload(admin, out.toByteArray(), "1.0", "9002")).andExpect(status().isBadRequest());
        mockMvc.perform(upload(admin, apk(), "bad name!", "9003")).andExpect(status().isBadRequest());
        mockMvc.perform(upload(admin, apk(), "1.0", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(upload(admin, new byte[7_000], "1.0", "9004")).andExpect(status().isPayloadTooLarge());

        uploadOk("2.0", 9005);
        mockMvc.perform(upload(admin, apk(), "2.0-again", "9005")).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from scanner_app_releases where version_code in (9001, 9002, 9003, 9004)", Integer.class)).isZero();
    }

    @Test
    void onlyAdminsManageUploads_andStaffAndAdminsFetchTheLatest() throws Exception {
        mockMvc.perform(upload(staffA, apk(), "1.0", "9010")).andExpect(status().isForbidden());
        mockMvc.perform(upload(stranger, apk(), "1.0", "9010")).andExpect(status().isForbidden());
        mockMvc.perform(upload(null, apk(), "1.0", "9010")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/scanner-app").header("Authorization", staffA)).andExpect(status().isForbidden());

        JsonNode newer = uploadOk("3.0", 9021);
        uploadOk("2.0", 9020);   // an older build uploaded later is not "latest": the highest version code is

        mockMvc.perform(get("/api/v1/scanner-app/latest").header("Authorization", staffA)).andExpect(status().isOk())
                .andExpect(jsonPath("$.versionCode").value(9021)).andExpect(jsonPath("$.versionName").value("3.0"))
                .andExpect(jsonPath("$.id").value(newer.get("id").asText()));
        mockMvc.perform(get("/api/v1/scanner-app/latest").header("Authorization", admin)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/scanner-app/latest").header("Authorization", stranger)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/scanner-app/latest")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/scanner-app").header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.versionCode==9020)]").exists()).andExpect(jsonPath("$[?(@.versionCode==9021)]").exists());
    }

    @Test
    void staffDownloadTheApk_asAnAndroidPackage_andADeletedUploadIsGone() throws Exception {
        JsonNode release = uploadOk("4.0", 9030);
        String url = release.get("downloadUrl").asText();

        MvcResult download = mockMvc.perform(get(url).header("Authorization", staffA)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.android.package-archive"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("event-scanner-4.0.apk")))
                .andExpect(header().string("X-Content-SHA256", release.get("sha256").asText())).andReturn();
        assertThat(download.getResponse().getContentAsByteArray()).hasSize(release.get("size").asInt());
        mockMvc.perform(get(url).header("Authorization", stranger)).andExpect(status().isForbidden());
        mockMvc.perform(get(url)).andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/v1/admin/scanner-app/{id}", release.get("id").asText()).header("Authorization", staffA)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/scanner-app/{id}", release.get("id").asText()).header("Authorization", admin)).andExpect(status().isNoContent());

        mockMvc.perform(get(url).header("Authorization", staffA)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/admin/scanner-app/{id}", release.get("id").asText()).header("Authorization", admin)).andExpect(status().isNotFound());
        // the history row stays (soft delete); the file is gone
        assertThat(jdbc.queryForObject("select deleted_at is not null from scanner_app_releases where id = ?", Boolean.class,
                UUID.fromString(release.get("id").asText()))).isTrue();
    }

    // ---- members holding SCANNER_APP publish the shared app ----

    private String holder(OrganizationRole role, java.util.Set<com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationPermission> permissions) {
        User user = user(Role.CUSTOMER);
        OrganizationMember member = OrganizationMember.builder().userId(user.getId()).organizationId(orgA).build();
        member.getRoles().add(role);
        if (permissions != null) {
            member.choosePermissions(permissions);
        }
        members.add(organizationMemberRepository.save(member));
        return token(user);
    }

    private MvcResult uploadTo(String path, String caller, String versionName, int versionCode) throws Exception {
        return mockMvc.perform(multipart(path).file(new MockMultipartFile("file", "scanner.apk", "application/octet-stream", apk()))
                .param("versionName", versionName).param("versionCode", String.valueOf(versionCode)).header("Authorization", caller)).andReturn();
    }

    @Test
    void aMemberHoldingScannerApp_publishesTheSharedApp_butOnlyRemovesTheirOwnUploads() throws Exception {
        var scannerApp = java.util.Set.of(com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationPermission.SCANNER_APP);
        String organizerHolder = holder(OrganizationRole.ORGANIZER, scannerApp);
        String staffHolder = holder(OrganizationRole.CHECK_IN_STAFF, scannerApp);
        String organizerWithout = holder(OrganizationRole.ORGANIZER, null);   // the default permissions do not include SCANNER_APP

        // without the permission: refused (also on the old admin address)
        assertThat(uploadTo("/api/v1/scanner-app/uploads", organizerWithout, "1.0", 9101).getResponse().getStatus()).isEqualTo(403);
        assertThat(uploadTo("/api/v1/admin/scanner-app", organizerWithout, "1.0", 9101).getResponse().getStatus()).isEqualTo(403);
        mockMvc.perform(get("/api/v1/scanner-app/uploads").header("Authorization", organizerWithout)).andExpect(status().isForbidden());

        // holders upload at the new address and at the old one; the uploader is recorded
        MvcResult first = uploadTo("/api/v1/scanner-app/uploads", organizerHolder, "5.0", 9102);
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        JsonNode mine = objectMapper.readTree(first.getResponse().getContentAsString());
        releaseIds.add(UUID.fromString(mine.get("id").asText()));
        MvcResult second = uploadTo("/api/v1/admin/scanner-app", staffHolder, "5.1", 9103);
        assertThat(second.getResponse().getStatus()).isEqualTo(201);
        JsonNode theirs = objectMapper.readTree(second.getResponse().getContentAsString());
        releaseIds.add(UUID.fromString(theirs.get("id").asText()));
        mockMvc.perform(get("/api/v1/scanner-app/uploads").header("Authorization", organizerHolder)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.versionCode==9102)]").exists()).andExpect(jsonPath("$[?(@.versionCode==9103)]").exists());
        // the rules are unchanged for holders: a repeated version code is a 409, a non-APK a 400
        assertThat(uploadTo("/api/v1/scanner-app/uploads", organizerHolder, "5.2", 9102).getResponse().getStatus()).isEqualTo(409);
        mockMvc.perform(multipart("/api/v1/scanner-app/uploads").file(new MockMultipartFile("file", "x.apk", "application/octet-stream", "text".getBytes()))
                .param("versionName", "9.9").param("versionCode", "9104").header("Authorization", organizerHolder)).andExpect(status().isBadRequest());

        // a holder removes only what they uploaded; an admin removes anything
        mockMvc.perform(delete("/api/v1/scanner-app/uploads/{id}", theirs.get("id").asText()).header("Authorization", organizerHolder)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/scanner-app/uploads/{id}", mine.get("id").asText()).header("Authorization", organizerHolder)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/admin/scanner-app/{id}", theirs.get("id").asText()).header("Authorization", admin)).andExpect(status().isNoContent());
        // and a holder can fetch the app they publish
        uploadOk("6.0", 9105);
        mockMvc.perform(get("/api/v1/scanner-app/latest").header("Authorization", organizerHolder)).andExpect(status().isOk());
    }
}
