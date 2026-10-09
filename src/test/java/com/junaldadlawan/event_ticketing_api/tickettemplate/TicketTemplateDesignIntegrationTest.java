package com.junaldadlawan.event_ticketing_api.tickettemplate;

import com.junaldadlawan.event_ticketing_api.auth.service.JwtService;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.entity.Organization;
import com.junaldadlawan.event_ticketing_api.organization.entity.OrganizationMember;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationRole;
import com.junaldadlawan.event_ticketing_api.organization.enums.OrganizationStatus;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationMemberRepository;
import com.junaldadlawan.event_ticketing_api.organization.repository.OrganizationRepository;
import com.junaldadlawan.event_ticketing_api.tickettemplate.entity.TicketTemplate;
import com.junaldadlawan.event_ticketing_api.tickettemplate.entity.TicketTextField;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.BackgroundFit;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.CodeType;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TextAlign;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TextFieldKey;
import com.junaldadlawan.event_ticketing_api.tickettemplate.repository.TicketTemplateRepository;
import com.junaldadlawan.event_ticketing_api.user.entity.User;
import com.junaldadlawan.event_ticketing_api.user.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real security chain + real Postgres (the V27 migration) for the ticket designer's saved design: create,
 * list and PATCH must hand back every field the web app saved, in order, and bad designs must be refused
 * before anything is stored. Also covers the unknown-URL status code.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TicketTemplateDesignIntegrationTest {

    private static final String FULL_DESIGN = """
            {"format":"DIGITAL","backgroundImageUrl":"https://example.com/bg.png",
            "ticketWidth":1200,"ticketHeight":500,"backgroundColor":"#FFEEAA",
            "backgroundFit":"CUSTOM","backgroundX":-10.5,"backgroundY":-5,"backgroundWidth":130,"backgroundHeight":120.25,
            "codeType":"BARCODE","codeX":5,"codeY":60,"codeWidth":40,"codeRotation":90,
            "textFields":[
              {"key":"EVENT_NAME","x":50,"y":12,"fontSize":9,"color":"#112233","bold":true,"align":"CENTER","lineBreaks":[6,12]},
              {"key":"ATTENDEE_NAME","x":5,"y":40,"fontSize":6,"color":"#000000","align":"LEFT","rotation":15,"sampleLength":18,"sampleText":"Jamie Q. Cruz-Santos"},
              {"key":"CUSTOM","x":95,"y":90,"fontSize":4,"color":"#FF0000","align":"RIGHT","text":"Doors open at 7pm","lineBreaks":[5]},
              {"key":"SEAT","x":70,"y":70,"fontSize":5,"color":"#00FF00","align":"RIGHT"}
            ]}
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private TicketTemplateRepository ticketTemplateRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private final List<UUID> createdOrgIds = new ArrayList<>();
    private final List<OrganizationMember> createdMembers = new ArrayList<>();
    private final List<UUID> createdEventIds = new ArrayList<>();
    private final List<UUID> createdTemplateIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        createdTemplateIds.forEach(ticketTemplateRepository::deleteById);
        createdTemplateIds.clear();
        createdEventIds.forEach(eventRepository::deleteById);
        createdEventIds.clear();
        createdMembers.forEach(member -> organizationMemberRepository
                .findByUserIdAndOrganizationId(member.getUserId(), member.getOrganizationId())
                .ifPresent(organizationMemberRepository::delete));
        createdMembers.clear();
        createdOrgIds.forEach(organizationRepository::deleteById);
        createdOrgIds.clear();
    }

    private User ownerUser() {
        return User.builder().id(UUID.randomUUID()).email("owner-" + UUID.randomUUID() + "@test.local").role(Role.CUSTOMER).build();
    }

    /** An approved organization owned by {@code owner}, with one DRAFT event; returns the event id. */
    private UUID eventOwnedBy(User owner) {
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Design Test Org " + UUID.randomUUID()).status(OrganizationStatus.APPROVED)
                .ownerId(owner.getId()).documents(List.of()).build());
        createdOrgIds.add(organization.getId());
        OrganizationMember member = OrganizationMember.builder().userId(owner.getId()).organizationId(organization.getId()).build();
        member.getRoles().add(OrganizationRole.OWNER);
        createdMembers.add(organizationMemberRepository.save(member));
        Instant startAt = Instant.now().plus(10, ChronoUnit.DAYS);
        Event event = eventRepository.save(Event.builder().organizationId(organization.getId()).title("Design Test Event")
                .description("desc").category("Music").status(EventStatus.DRAFT)
                .ticketPrefix("D" + UUID.randomUUID().toString().substring(0, 2).toUpperCase())
                .startAt(startAt).endAt(startAt.plus(2, ChronoUnit.HOURS)).timezone("UTC").build());
        createdEventIds.add(event.getId());
        return event.getId();
    }

    private MvcResult create(UUID eventId, String token, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/events/{eventId}/ticket-templates", eventId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andReturn();
    }

    private UUID createdId(MvcResult result) throws Exception {
        UUID id = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        createdTemplateIds.add(id);
        return id;
    }

    // ---- create + reload ----

    @Test
    void create_aFullDesign_isStoredAndReturnedWithEveryField() throws Exception {
        User owner = ownerUser();
        UUID eventId = eventOwnedBy(owner);
        String token = jwtService.generateAccessToken(owner);

        MvcResult created = create(eventId, token, FULL_DESIGN);

        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        UUID templateId = createdId(created);
        JsonNode json = objectMapper.readTree(created.getResponse().getContentAsString());
        assertThat(json.get("ticketWidth").asInt()).isEqualTo(1200);
        assertThat(json.get("ticketHeight").asInt()).isEqualTo(500);
        assertThat(json.get("backgroundColor").asText()).isEqualTo("#FFEEAA");
        assertThat(json.get("backgroundFit").asText()).isEqualTo("CUSTOM");
        assertThat(json.get("backgroundX").asDouble()).isEqualTo(-10.5);
        assertThat(json.get("backgroundHeight").asDouble()).isEqualTo(120.25);
        assertThat(json.get("codeType").asText()).isEqualTo("BARCODE");
        assertThat(json.get("codeRotation").asInt()).isEqualTo(90);
        assertThat(json.get("textFields")).hasSize(4);

        // reload from the database: every column, and the list in its saved order
        TicketTemplate stored = ticketTemplateRepository.findByIdAndDeletedAtIsNull(templateId).orElseThrow();
        assertThat(stored.getTicketWidth()).isEqualTo(1200);
        assertThat(stored.getBackgroundFit()).isEqualTo(BackgroundFit.CUSTOM);
        assertThat(stored.getBackgroundY()).isEqualTo(-5.0);
        assertThat(stored.getCodeType()).isEqualTo(CodeType.BARCODE);
        assertThat(stored.getTextFields()).extracting(TicketTextField::getKey)
                .containsExactly(TextFieldKey.EVENT_NAME, TextFieldKey.ATTENDEE_NAME, TextFieldKey.CUSTOM, TextFieldKey.SEAT);
        TicketTextField name = stored.getTextFields().get(0);
        assertThat(name.isBold()).isTrue();
        assertThat(name.getAlign()).isEqualTo(TextAlign.CENTER);
        assertThat(name.getLineBreaks()).containsExactly(6, 12);
        TicketTextField attendee = stored.getTextFields().get(1);
        assertThat(attendee.getRotation()).isEqualTo(15);
        assertThat(attendee.getSampleLength()).isEqualTo(18);
        assertThat(attendee.getSampleText()).isEqualTo("Jamie Q. Cruz-Santos");
        assertThat(stored.getTextFields().get(2).getText()).isEqualTo("Doors open at 7pm");
        assertThat(stored.getTextFields().get(3).getLineBreaks()).isEmpty();
    }

    @Test
    void list_returnsTheDesignInTheSameShape() throws Exception {
        User owner = ownerUser();
        UUID eventId = eventOwnedBy(owner);
        String token = jwtService.generateAccessToken(owner);
        createdId(create(eventId, token, FULL_DESIGN));

        mockMvc.perform(get("/api/v1/events/{eventId}/ticket-templates", eventId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].textFields[0].key").value("EVENT_NAME"))
                .andExpect(jsonPath("$[0].textFields[1].sampleText").value("Jamie Q. Cruz-Santos"))
                .andExpect(jsonPath("$[0].textFields[2].text").value("Doors open at 7pm"))
                .andExpect(jsonPath("$[0].textFields[3].key").value("SEAT"))
                .andExpect(jsonPath("$[0].backgroundWidth").value(130.0));
    }

    @Test
    void create_withoutAnyDesignField_isAnOldStyleTemplate_withAnEmptyList() throws Exception {
        User owner = ownerUser();
        UUID eventId = eventOwnedBy(owner);
        MvcResult created = create(eventId, jwtService.generateAccessToken(owner),
                "{\"format\":\"DIGITAL\",\"primaryColor\":\"#ABCDEF\"}");

        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        createdId(created);
        JsonNode json = objectMapper.readTree(created.getResponse().getContentAsString());
        assertThat(json.get("textFields")).isEmpty();
        assertThat(json.get("ticketWidth").isNull()).isTrue();
        assertThat(json.get("codeType").isNull()).isTrue();
    }

    // ---- refused designs store nothing ----

    @Test
    void create_aDesignThatBreaksARule_is400_andNothingIsStored() throws Exception {
        User owner = ownerUser();
        UUID eventId = eventOwnedBy(owner);
        String token = jwtService.generateAccessToken(owner);

        for (String body : new String[] {
                // duplicate non-CUSTOM key
                """
                {"format":"DIGITAL","textFields":[
                 {"key":"SEAT","x":1,"y":1,"fontSize":5,"color":"#000000","align":"LEFT"},
                 {"key":"SEAT","x":2,"y":2,"fontSize":5,"color":"#000000","align":"LEFT"}]}""",
                // CUSTOM background without its rectangle
                "{\"format\":\"DIGITAL\",\"backgroundFit\":\"CUSTOM\"}",
                // a rectangle for a COVER background
                "{\"format\":\"DIGITAL\",\"backgroundFit\":\"COVER\",\"backgroundX\":0,\"backgroundY\":0,\"backgroundWidth\":100,\"backgroundHeight\":100}",
                // no code, yet a placement
                "{\"format\":\"DIGITAL\",\"codeType\":\"NONE\",\"codeX\":10,\"codeY\":10,\"codeWidth\":20}",
                // line breaks on a per-ticket field
                """
                {"format":"DIGITAL","textFields":[{"key":"ROW","x":1,"y":1,"fontSize":5,"color":"#000000","align":"LEFT","lineBreaks":[2]}]}"""}) {
            assertThat(create(eventId, token, body).getResponse().getStatus()).as(body).isEqualTo(400);
        }
        assertThat(ticketTemplateRepository.findByEventIdAndDeletedAtIsNull(eventId)).isEmpty();
    }

    // ---- PATCH ----

    @Test
    void patch_changesOnlyWhatIsSent_andTheListIsReplacedAsAWhole() throws Exception {
        User owner = ownerUser();
        UUID eventId = eventOwnedBy(owner);
        String token = jwtService.generateAccessToken(owner);
        UUID templateId = createdId(create(eventId, token, FULL_DESIGN));

        mockMvc.perform(patch("/api/v1/ticket-templates/{id}", templateId)
                        .header("Authorization", "Bearer " + token).contentType("application/json")
                        .content("""
                                {"ticketWidth":900,"textFields":[
                                 {"key":"VENUE","x":10,"y":10,"fontSize":5,"color":"#123456","align":"LEFT"},
                                 {"key":"EVENT_DATE","x":10,"y":20,"fontSize":5,"color":"#123456","align":"LEFT"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketWidth").value(900))
                .andExpect(jsonPath("$.ticketHeight").value(500))
                .andExpect(jsonPath("$.backgroundColor").value("#FFEEAA"))
                .andExpect(jsonPath("$.textFields.length()").value(2))
                .andExpect(jsonPath("$.textFields[0].key").value("VENUE"))
                .andExpect(jsonPath("$.textFields[1].key").value("EVENT_DATE"));

        TicketTemplate stored = ticketTemplateRepository.findByIdAndDeletedAtIsNull(templateId).orElseThrow();
        assertThat(stored.getTextFields()).extracting(TicketTextField::getKey).containsExactly(TextFieldKey.VENUE, TextFieldKey.EVENT_DATE);
    }

    @Test
    void patch_aTextFieldsOmission_leavesThemAlone_andAnEmptyListRemovesThemAll() throws Exception {
        User owner = ownerUser();
        UUID eventId = eventOwnedBy(owner);
        String token = jwtService.generateAccessToken(owner);
        UUID templateId = createdId(create(eventId, token, FULL_DESIGN));

        mockMvc.perform(patch("/api/v1/ticket-templates/{id}", templateId)
                        .header("Authorization", "Bearer " + token).contentType("application/json")
                        .content("{\"ticketHeight\":400}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.textFields.length()").value(4));

        mockMvc.perform(patch("/api/v1/ticket-templates/{id}", templateId)
                        .header("Authorization", "Bearer " + token).contentType("application/json")
                        .content("{\"textFields\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.textFields.length()").value(0));
        assertThat(ticketTemplateRepository.findByIdAndDeletedAtIsNull(templateId).orElseThrow().getTextFields()).isEmpty();
    }

    @Test
    void patch_blankValuesClearTheBackgroundColourAndImage() throws Exception {
        User owner = ownerUser();
        UUID eventId = eventOwnedBy(owner);
        String token = jwtService.generateAccessToken(owner);
        UUID templateId = createdId(create(eventId, token, FULL_DESIGN));

        mockMvc.perform(patch("/api/v1/ticket-templates/{id}", templateId)
                        .header("Authorization", "Bearer " + token).contentType("application/json")
                        .content("{\"backgroundColor\":\"\",\"backgroundImageUrl\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backgroundColor").doesNotExist())
                .andExpect(jsonPath("$.backgroundImageUrl").doesNotExist());
    }

    @Test
    void patch_switchingFromCustomToCover_dropsTheRectangle_andNoneDropsThePlacement() throws Exception {
        User owner = ownerUser();
        UUID eventId = eventOwnedBy(owner);
        String token = jwtService.generateAccessToken(owner);
        UUID templateId = createdId(create(eventId, token, FULL_DESIGN));

        mockMvc.perform(patch("/api/v1/ticket-templates/{id}", templateId)
                        .header("Authorization", "Bearer " + token).contentType("application/json")
                        .content("{\"backgroundFit\":\"COVER\",\"codeType\":\"NONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backgroundFit").value("COVER"))
                .andExpect(jsonPath("$.backgroundX").doesNotExist())
                .andExpect(jsonPath("$.backgroundWidth").doesNotExist())
                .andExpect(jsonPath("$.codeType").value("NONE"))
                .andExpect(jsonPath("$.codeX").doesNotExist())
                .andExpect(jsonPath("$.codeRotation").doesNotExist());
    }

    @Test
    void patch_anInvalidMergedDesign_is400_andTheStoredTemplateIsUntouched() throws Exception {
        User owner = ownerUser();
        UUID eventId = eventOwnedBy(owner);
        String token = jwtService.generateAccessToken(owner);
        UUID templateId = createdId(create(eventId, token, FULL_DESIGN));

        mockMvc.perform(patch("/api/v1/ticket-templates/{id}", templateId)
                        .header("Authorization", "Bearer " + token).contentType("application/json")
                        .content("{\"codeType\":\"NONE\",\"codeX\":10}"))
                .andExpect(status().isBadRequest());

        TicketTemplate stored = ticketTemplateRepository.findByIdAndDeletedAtIsNull(templateId).orElseThrow();
        assertThat(stored.getCodeType()).isEqualTo(CodeType.BARCODE);
        assertThat(stored.getCodeX()).isEqualTo(5.0);
    }

    // ---- delete ----

    @Test
    void delete_owner_returns204_andTheTemplateDisappearsFromTheList() throws Exception {
        User owner = ownerUser();
        UUID eventId = eventOwnedBy(owner);
        String token = jwtService.generateAccessToken(owner);
        UUID templateId = createdId(create(eventId, token, FULL_DESIGN));
        UUID otherTemplateId = createdId(create(eventId, token, "{\"format\":\"PHYSICAL\",\"primaryColor\":\"#ABCDEF\"}"));

        mockMvc.perform(delete("/api/v1/ticket-templates/{id}", templateId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // gone from the list - the other template stays
        mockMvc.perform(get("/api/v1/events/{eventId}/ticket-templates", eventId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(otherTemplateId.toString()));
        // soft delete: the row is kept, stamped as deleted
        assertThat(ticketTemplateRepository.findById(templateId).orElseThrow().getDeletedAt()).isNotNull();
        assertThat(ticketTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).isEmpty();
    }

    @Test
    void delete_aSecondTime_isA404() throws Exception {
        User owner = ownerUser();
        UUID eventId = eventOwnedBy(owner);
        String token = jwtService.generateAccessToken(owner);
        UUID templateId = createdId(create(eventId, token, FULL_DESIGN));

        mockMvc.perform(delete("/api/v1/ticket-templates/{id}", templateId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/ticket-templates/{id}", templateId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_aDeletedTemplate_cannotBeUpdatedAnyMore() throws Exception {
        User owner = ownerUser();
        UUID eventId = eventOwnedBy(owner);
        String token = jwtService.generateAccessToken(owner);
        UUID templateId = createdId(create(eventId, token, FULL_DESIGN));
        mockMvc.perform(delete("/api/v1/ticket-templates/{id}", templateId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(patch("/api/v1/ticket-templates/{id}", templateId)
                        .header("Authorization", "Bearer " + token).contentType("application/json")
                        .content("{\"ticketWidth\":800}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_aStrangerAndACrossOrgOwner_getTheirOwn403_andTheTemplateSurvives() throws Exception {
        User owner = ownerUser();
        UUID eventId = eventOwnedBy(owner);
        String ownerToken = jwtService.generateAccessToken(owner);
        UUID templateId = createdId(create(eventId, ownerToken, FULL_DESIGN));
        User stranger = ownerUser();
        User otherOrgOwner = ownerUser();
        eventOwnedBy(otherOrgOwner);

        mockMvc.perform(delete("/api/v1/ticket-templates/{id}", templateId)
                        .header("Authorization", "Bearer " + jwtService.generateAccessToken(stranger)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/ticket-templates/{id}", templateId)
                        .header("Authorization", "Bearer " + jwtService.generateAccessToken(otherOrgOwner)))
                .andExpect(status().isForbidden());

        assertThat(ticketTemplateRepository.findByIdAndDeletedAtIsNull(templateId)).isPresent();
    }

    @Test
    void delete_anAdminWithNoOrganizationRole_isAllowed() throws Exception {
        User owner = ownerUser();
        UUID eventId = eventOwnedBy(owner);
        UUID templateId = createdId(create(eventId, jwtService.generateAccessToken(owner), FULL_DESIGN));
        User admin = User.builder().id(UUID.randomUUID()).email("admin-" + UUID.randomUUID() + "@test.local").role(Role.ADMIN).build();

        mockMvc.perform(delete("/api/v1/ticket-templates/{id}", templateId)
                        .header("Authorization", "Bearer " + jwtService.generateAccessToken(admin)))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_withoutSigningIn_is401() throws Exception {
        mockMvc.perform(delete("/api/v1/ticket-templates/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void delete_anUnknownId_is404() throws Exception {
        String token = jwtService.generateAccessToken(ownerUser());

        mockMvc.perform(delete("/api/v1/ticket-templates/{id}", UUID.randomUUID()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ---- real error statuses ----

    @Test
    void anUnknownUrl_forASignedInCaller_is404_notAMisleading401() throws Exception {
        String token = jwtService.generateAccessToken(ownerUser());

        mockMvc.perform(get("/api/v1/this-route-does-not-exist").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void anUnknownUrl_forAnAnonymousCaller_isStill401() throws Exception {
        mockMvc.perform(get("/api/v1/this-route-does-not-exist")).andExpect(status().isUnauthorized());
    }
}
