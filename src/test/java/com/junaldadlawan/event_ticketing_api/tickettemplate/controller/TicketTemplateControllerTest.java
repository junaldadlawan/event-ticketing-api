package com.junaldadlawan.event_ticketing_api.tickettemplate.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.checkin.security.DeviceAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.tickettemplate.entity.TicketTemplate;
import com.junaldadlawan.event_ticketing_api.tickettemplate.entity.TicketTextField;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.BackgroundFit;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.CodeType;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TextAlign;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TextFieldKey;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TicketTemplateFormat;
import com.junaldadlawan.event_ticketing_api.tickettemplate.service.TicketTemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link TicketTemplateController}'s own behavior — mirrors
 * {@code TicketTypeControllerTest}. Security-filter enforcement (the
 * cross-org-resolved-from-persisted-eventId rule) is exercised separately in
 * the full-stack {@code TicketTemplateAccessIntegrationTest}.
 */
@WebMvcTest(TicketTemplateController.class)
@AutoConfigureMockMvc(addFilters = false)
class TicketTemplateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketTemplateService ticketTemplateService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private DeviceAuthenticationFilter deviceAuthenticationFilter;

    private TicketTemplate template(UUID id, UUID eventId, String primaryColor) {
        return TicketTemplate.builder()
                .id(id)
                .eventId(eventId)
                .ticketTypeId(null)
                .format(TicketTemplateFormat.DIGITAL)
                .logoUrl("https://example.com/logo.png")
                .backgroundImageUrl("https://example.com/bg.png")
                .primaryColor(primaryColor)
                .build();
    }

    @Test
    void update_validRequest_returns200() throws Exception {
        UUID templateId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(ticketTemplateService.update(eq(templateId), any())).thenReturn(template(templateId, eventId, "#000000"));

        mockMvc.perform(patch("/api/v1/ticket-templates/{templateId}", templateId)
                        .contentType("application/json")
                        .content("""
                                {"primaryColor":"#000000"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(templateId.toString()))
                .andExpect(jsonPath("$.primaryColor").value("#000000"));
    }

    @Test
    void update_unknownTemplate_returns404() throws Exception {
        UUID templateId = UUID.randomUUID();
        when(ticketTemplateService.update(eq(templateId), any()))
                .thenThrow(new ResourceNotFoundException("Ticket template " + templateId + " not found"));

        mockMvc.perform(patch("/api/v1/ticket-templates/{templateId}", templateId)
                        .contentType("application/json")
                        .content("""
                                {"primaryColor":"#000000"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_unauthorizedCaller_returns403() throws Exception {
        UUID templateId = UUID.randomUUID();
        when(ticketTemplateService.update(eq(templateId), any()))
                .thenThrow(new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this event's ticket templates"));

        mockMvc.perform(patch("/api/v1/ticket-templates/{templateId}", templateId)
                        .contentType("application/json")
                        .content("""
                                {"primaryColor":"#000000"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_invalidBackgroundImageUrl_returns400() throws Exception {
        UUID templateId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/ticket-templates/{templateId}", templateId)
                        .contentType("application/json")
                        .content("""
                                {"backgroundImageUrl":"not-a-url"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ---- code placement (QR / barcode) ----

    @Test
    void create_withCodePlacement_returns201_andEchoesIt() throws Exception {
        UUID eventId = UUID.randomUUID();
        TicketTemplate saved = template(UUID.randomUUID(), eventId);
        saved.setCodeType(CodeType.BARCODE);
        saved.setCodeX(10.0);
        saved.setCodeY(55.5);
        saved.setCodeWidth(40.0);
        saved.setCodeRotation(90);
        when(ticketTemplateService.create(eq(eventId), any())).thenReturn(saved);

        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-templates", eventId)
                        .contentType("application/json")
                        .content("""
                                {"format":"DIGITAL","codeType":"BARCODE","codeX":10,"codeY":55.5,"codeWidth":40,"codeRotation":90}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codeType").value("BARCODE"))
                .andExpect(jsonPath("$.codeX").value(10.0))
                .andExpect(jsonPath("$.codeY").value(55.5))
                .andExpect(jsonPath("$.codeWidth").value(40.0))
                .andExpect(jsonPath("$.codeRotation").value(90));
    }

    @Test
    void create_withoutCodePlacement_returnsNullCodeFields() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(ticketTemplateService.create(eq(eventId), any())).thenReturn(template(UUID.randomUUID(), eventId));

        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-templates", eventId)
                        .contentType("application/json")
                        .content(validCreateBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codeType").doesNotExist())
                .andExpect(jsonPath("$.codeX").doesNotExist())
                .andExpect(jsonPath("$.codeWidth").doesNotExist());
    }

    @Test
    void create_unknownCodeType_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-templates", UUID.randomUUID())
                        .contentType("application/json")
                        .content("""
                                {"format":"DIGITAL","codeType":"DATAMATRIX","codeX":10,"codeY":10,"codeWidth":20,"codeRotation":0}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_codeCoordinateOrRotationOutOfRange_returns400() throws Exception {
        for (String body : new String[] {
                """
                {"format":"DIGITAL","codeType":"QR","codeX":-1,"codeY":10,"codeWidth":20,"codeRotation":0}""",
                """
                {"format":"DIGITAL","codeType":"QR","codeX":10,"codeY":101,"codeWidth":20,"codeRotation":0}""",
                """
                {"format":"DIGITAL","codeType":"QR","codeX":10,"codeY":10,"codeWidth":101,"codeRotation":0}""",
                """
                {"format":"DIGITAL","codeType":"BARCODE","codeX":10,"codeY":10,"codeWidth":40,"codeRotation":360}""",
                """
                {"format":"DIGITAL","codeType":"BARCODE","codeX":10,"codeY":10,"codeWidth":40,"codeRotation":-1}"""}) {
            mockMvc.perform(post("/api/v1/events/{eventId}/ticket-templates", UUID.randomUUID())
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void update_codePlacementOnly_returns200() throws Exception {
        UUID templateId = UUID.randomUUID();
        TicketTemplate updated = template(templateId, UUID.randomUUID());
        updated.setCodeType(CodeType.QR);
        updated.setCodeX(5.0);
        updated.setCodeY(60.0);
        updated.setCodeWidth(30.0);
        updated.setCodeRotation(0);
        when(ticketTemplateService.update(eq(templateId), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/ticket-templates/{templateId}", templateId)
                        .contentType("application/json")
                        .content("""
                                {"codeType":"QR","codeX":5,"codeY":60,"codeWidth":30,"codeRotation":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codeType").value("QR"))
                .andExpect(jsonPath("$.codeWidth").value(30.0));
    }

    @Test
    void update_emptyBackgroundImageUrl_isAccepted() throws Exception {
        UUID templateId = UUID.randomUUID();
        when(ticketTemplateService.update(eq(templateId), any())).thenReturn(template(templateId, UUID.randomUUID()));

        mockMvc.perform(patch("/api/v1/ticket-templates/{templateId}", templateId)
                        .contentType("application/json")
                        .content("""
                                {"backgroundImageUrl":""}
                                """))
                .andExpect(status().isOk());
    }

    // ---- ticket designer: canvas, background, text fields ----

    private static final String VALID_FIELD =
            "{\"key\":\"EVENT_NAME\",\"x\":10,\"y\":50,\"fontSize\":8,\"color\":\"#112233\",\"align\":\"LEFT\"}";

    private String createWithField(String fieldJson) {
        return "{\"format\":\"DIGITAL\",\"textFields\":[" + fieldJson + "]}";
    }

    private void assertCreateRejected(String body) throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-templates", UUID.randomUUID())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withAFullDesign_returns201_andEchoesEveryField() throws Exception {
        UUID eventId = UUID.randomUUID();
        TicketTemplate saved = template(UUID.randomUUID(), eventId);
        saved.setTicketWidth(1200);
        saved.setTicketHeight(500);
        saved.setBackgroundColor("#FFEEAA");
        saved.setBackgroundFit(BackgroundFit.CUSTOM);
        saved.setBackgroundX(-10.0);
        saved.setBackgroundY(-5.0);
        saved.setBackgroundWidth(130.0);
        saved.setBackgroundHeight(120.0);
        saved.setCodeType(CodeType.NONE);
        saved.getTextFields().add(TicketTextField.builder().key(TextFieldKey.CUSTOM).x(10).y(20).fontSize(6).color("#000000")
                .bold(true).align(TextAlign.CENTER).rotation(90).text("Welcome").lineBreaks(List.of(3)).build());
        saved.getTextFields().add(TicketTextField.builder().key(TextFieldKey.SEAT).x(70).y(80).fontSize(10).color("#FF0000")
                .align(TextAlign.RIGHT).sampleLength(4).sampleText("12A").build());
        when(ticketTemplateService.create(eq(eventId), any())).thenReturn(saved);

        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-templates", eventId)
                        .contentType("application/json")
                        .content("""
                                {"format":"DIGITAL","ticketWidth":1200,"ticketHeight":500,"backgroundColor":"#FFEEAA",
                                "backgroundFit":"CUSTOM","backgroundX":-10,"backgroundY":-5,"backgroundWidth":130,"backgroundHeight":120,
                                "codeType":"NONE",
                                "textFields":[
                                 {"key":"CUSTOM","x":10,"y":20,"fontSize":6,"color":"#000000","bold":true,"align":"CENTER","rotation":90,"text":"Welcome","lineBreaks":[3]},
                                 {"key":"SEAT","x":70,"y":80,"fontSize":10,"color":"#FF0000","align":"RIGHT","sampleLength":4,"sampleText":"12A"}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketWidth").value(1200))
                .andExpect(jsonPath("$.ticketHeight").value(500))
                .andExpect(jsonPath("$.backgroundColor").value("#FFEEAA"))
                .andExpect(jsonPath("$.backgroundFit").value("CUSTOM"))
                .andExpect(jsonPath("$.backgroundX").value(-10.0))
                .andExpect(jsonPath("$.backgroundWidth").value(130.0))
                .andExpect(jsonPath("$.codeType").value("NONE"))
                .andExpect(jsonPath("$.textFields.length()").value(2))
                .andExpect(jsonPath("$.textFields[0].key").value("CUSTOM"))
                .andExpect(jsonPath("$.textFields[0].bold").value(true))
                .andExpect(jsonPath("$.textFields[0].align").value("CENTER"))
                .andExpect(jsonPath("$.textFields[0].rotation").value(90))
                .andExpect(jsonPath("$.textFields[0].text").value("Welcome"))
                .andExpect(jsonPath("$.textFields[0].lineBreaks[0]").value(3))
                .andExpect(jsonPath("$.textFields[1].key").value("SEAT"))
                .andExpect(jsonPath("$.textFields[1].sampleLength").value(4))
                .andExpect(jsonPath("$.textFields[1].sampleText").value("12A"));
    }

    @Test
    void create_withoutAnyDesignField_stillWorks_andReturnsAnEmptyTextFieldList() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(ticketTemplateService.create(eq(eventId), any())).thenReturn(template(UUID.randomUUID(), eventId));

        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-templates", eventId)
                        .contentType("application/json")
                        .content(validCreateBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.textFields.length()").value(0))
                .andExpect(jsonPath("$.ticketWidth").doesNotExist())
                .andExpect(jsonPath("$.backgroundFit").doesNotExist());
    }

    @Test
    void create_canvasSizeOutOfRange_returns400() throws Exception {
        assertCreateRejected("{\"format\":\"DIGITAL\",\"ticketWidth\":99}");
        assertCreateRejected("{\"format\":\"DIGITAL\",\"ticketWidth\":5001}");
        assertCreateRejected("{\"format\":\"DIGITAL\",\"ticketHeight\":99}");
        assertCreateRejected("{\"format\":\"DIGITAL\",\"ticketHeight\":5001}");
    }

    @Test
    void create_badBackgroundValues_return400() throws Exception {
        assertCreateRejected("{\"format\":\"DIGITAL\",\"backgroundColor\":\"red\"}");
        assertCreateRejected("{\"format\":\"DIGITAL\",\"backgroundColor\":\"#FFF\"}");
        assertCreateRejected("{\"format\":\"DIGITAL\",\"backgroundFit\":\"TILE\"}");
        assertCreateRejected("{\"format\":\"DIGITAL\",\"backgroundFit\":\"CUSTOM\",\"backgroundX\":-501,\"backgroundY\":0,\"backgroundWidth\":100,\"backgroundHeight\":100}");
        assertCreateRejected("{\"format\":\"DIGITAL\",\"backgroundFit\":\"CUSTOM\",\"backgroundX\":0,\"backgroundY\":501,\"backgroundWidth\":100,\"backgroundHeight\":100}");
        assertCreateRejected("{\"format\":\"DIGITAL\",\"backgroundFit\":\"CUSTOM\",\"backgroundX\":0,\"backgroundY\":0,\"backgroundWidth\":0,\"backgroundHeight\":100}");
        assertCreateRejected("{\"format\":\"DIGITAL\",\"backgroundFit\":\"CUSTOM\",\"backgroundX\":0,\"backgroundY\":0,\"backgroundWidth\":501,\"backgroundHeight\":100}");
    }

    @Test
    void create_aBlankBackgroundColour_isAccepted() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(ticketTemplateService.create(eq(eventId), any())).thenReturn(template(UUID.randomUUID(), eventId));

        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-templates", eventId)
                        .contentType("application/json")
                        .content("{\"format\":\"DIGITAL\",\"backgroundColor\":\"\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void create_aTextFieldOutOfRange_returns400() throws Exception {
        assertCreateRejected(createWithField(VALID_FIELD.replace("\"x\":10", "\"x\":101")));
        assertCreateRejected(createWithField(VALID_FIELD.replace("\"x\":10", "\"x\":-1")));
        assertCreateRejected(createWithField(VALID_FIELD.replace("\"y\":50", "\"y\":101")));
        assertCreateRejected(createWithField(VALID_FIELD.replace("\"fontSize\":8", "\"fontSize\":1")));
        assertCreateRejected(createWithField(VALID_FIELD.replace("\"fontSize\":8", "\"fontSize\":26")));
        assertCreateRejected(createWithField(VALID_FIELD.replace("}", ",\"rotation\":360}")));
        assertCreateRejected(createWithField(VALID_FIELD.replace("}", ",\"rotation\":-1}")));
    }

    @Test
    void create_aTextFieldWithBadValues_returns400() throws Exception {
        assertCreateRejected(createWithField(VALID_FIELD.replace("#112233", "blue")));
        assertCreateRejected(createWithField(VALID_FIELD.replace("#112233", "#12345")));
        assertCreateRejected(createWithField(VALID_FIELD.replace("\"align\":\"LEFT\"", "\"align\":\"JUSTIFY\"")));
        assertCreateRejected(createWithField(VALID_FIELD.replace("EVENT_NAME", "PRICE")));
        assertCreateRejected(createWithField("{\"x\":10,\"y\":50,\"fontSize\":8,\"color\":\"#112233\",\"align\":\"LEFT\"}"));
        assertCreateRejected(createWithField(VALID_FIELD.replace("\"align\":\"LEFT\"", "")));
    }

    @Test
    void create_aTextFieldsSampleAndTextLimits_return400() throws Exception {
        String thirtyOne = "A".repeat(31);
        String sixtyOne = "A".repeat(61);
        assertCreateRejected(createWithField(VALID_FIELD.replace("EVENT_NAME", "SEAT").replace("}", ",\"sampleLength\":0}")));
        assertCreateRejected(createWithField(VALID_FIELD.replace("EVENT_NAME", "SEAT").replace("}", ",\"sampleLength\":31}")));
        assertCreateRejected(createWithField(VALID_FIELD.replace("EVENT_NAME", "SEAT").replace("}", ",\"sampleText\":\"" + thirtyOne + "\"}")));
        assertCreateRejected(createWithField(VALID_FIELD.replace("EVENT_NAME", "SEAT").replace("}", ",\"sampleText\":\"\"}")));
        assertCreateRejected(createWithField(VALID_FIELD.replace("EVENT_NAME", "CUSTOM").replace("}", ",\"text\":\"" + sixtyOne + "\"}")));
        assertCreateRejected(createWithField(VALID_FIELD.replace("EVENT_NAME", "CUSTOM").replace("}", ",\"text\":\"\"}")));
    }

    @Test
    void create_textThatIsNotASingleLineOrHasHtml_returns400() throws Exception {
        assertCreateRejected(createWithField(VALID_FIELD.replace("EVENT_NAME", "CUSTOM").replace("}", ",\"text\":\"two\\nlines\"}")));
        assertCreateRejected(createWithField(VALID_FIELD.replace("EVENT_NAME", "CUSTOM").replace("}", ",\"text\":\"a\\rb\"}")));
        assertCreateRejected(createWithField(VALID_FIELD.replace("EVENT_NAME", "CUSTOM").replace("}", ",\"text\":\"<b>hi</b>\"}")));
        assertCreateRejected(createWithField(VALID_FIELD.replace("EVENT_NAME", "SEAT").replace("}", ",\"sampleText\":\"1\\n2\"}")));
    }

    @Test
    void create_tooManyTextFieldsOrLineBreaks_returns400() throws Exception {
        String thirtyOne = String.join(",", java.util.Collections.nCopies(31, VALID_FIELD));
        assertCreateRejected("{\"format\":\"DIGITAL\",\"textFields\":[" + thirtyOne + "]}");
        assertCreateRejected(createWithField(VALID_FIELD.replace("}", ",\"lineBreaks\":[1,2,3,4,5,6,7,8,9,10,11]}")));
        assertCreateRejected(createWithField(VALID_FIELD.replace("}", ",\"lineBreaks\":[0]}")));
        assertCreateRejected(createWithField(VALID_FIELD.replace("}", ",\"lineBreaks\":[-2]}")));
    }

    @Test
    void create_aValidTextField_returns201_atTheLimits() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(ticketTemplateService.create(eq(eventId), any())).thenReturn(template(UUID.randomUUID(), eventId));
        String atTheLimits = "{\"key\":\"CUSTOM\",\"x\":0,\"y\":100,\"fontSize\":25,\"color\":\"#abcdef\",\"bold\":true,"
                + "\"align\":\"RIGHT\",\"rotation\":359,\"text\":\"" + "A".repeat(60) + "\",\"lineBreaks\":[1,2,3,4,5,6,7,8,9,10]}";

        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-templates", eventId)
                        .contentType("application/json")
                        .content("{\"format\":\"DIGITAL\",\"ticketWidth\":100,\"ticketHeight\":5000,\"textFields\":[" + atTheLimits + "]}"))
                .andExpect(status().isCreated());
    }

    @Test
    void update_designFields_returns200_andAnEmptyListOrBlankValuesAreAccepted() throws Exception {
        UUID templateId = UUID.randomUUID();
        TicketTemplate updated = template(templateId, UUID.randomUUID());
        updated.setBackgroundFit(BackgroundFit.STRETCH);
        when(ticketTemplateService.update(eq(templateId), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/ticket-templates/{templateId}", templateId)
                        .contentType("application/json")
                        .content("{\"textFields\":[],\"backgroundColor\":\"\",\"backgroundImageUrl\":\"\",\"backgroundFit\":\"STRETCH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backgroundFit").value("STRETCH"));
    }

    @Test
    void update_invalidDesignValues_return400() throws Exception {
        for (String body : new String[] {
                "{\"ticketWidth\":50}",
                "{\"backgroundColor\":\"nope\"}",
                "{\"backgroundFit\":\"WRONG\"}",
                "{\"backgroundWidth\":-1}",
                "{\"textFields\":[" + VALID_FIELD.replace("\"fontSize\":8", "\"fontSize\":30") + "]}"}) {
            mockMvc.perform(patch("/api/v1/ticket-templates/{templateId}", UUID.randomUUID())
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    // ---- text fields leave out the members that do not apply ----

    private tools.jackson.databind.JsonNode responseJson(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return new tools.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString());
    }

    private tools.jackson.databind.JsonNode createReturning(TicketTextField... fields) throws Exception {
        UUID eventId = UUID.randomUUID();
        TicketTemplate saved = template(UUID.randomUUID(), eventId);
        saved.getTextFields().addAll(List.of(fields));
        when(ticketTemplateService.create(eq(eventId), any())).thenReturn(saved);

        return responseJson(mockMvc.perform(post("/api/v1/events/{eventId}/ticket-templates", eventId)
                        .contentType("application/json")
                        .content(validCreateBody()))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private TicketTextField.TicketTextFieldBuilder field(TextFieldKey key) {
        return TicketTextField.builder().key(key).x(10).y(20).fontSize(6).color("#112233").align(TextAlign.LEFT);
    }

    @Test
    void response_aStaticField_hasNoSampleTextOrLineBreakKeysAtAll() throws Exception {
        tools.jackson.databind.JsonNode json = createReturning(field(TextFieldKey.EVENT_NAME).build());

        tools.jackson.databind.JsonNode field = json.get("textFields").get(0);
        assertThat(field.has("sampleLength")).isFalse();
        assertThat(field.has("sampleText")).isFalse();
        assertThat(field.has("text")).isFalse();
        assertThat(field.has("lineBreaks")).isFalse();
        assertThat(field.get("key").asText()).isEqualTo("EVENT_NAME");
        assertThat(field.get("x").asDouble()).isEqualTo(10.0);
        assertThat(field.get("color").asText()).isEqualTo("#112233");
    }

    @Test
    void response_aDynamicField_hasSampleLength_andSampleTextOnlyWhenSaved() throws Exception {
        tools.jackson.databind.JsonNode json = createReturning(
                field(TextFieldKey.SEAT).sampleLength(6).build(),
                field(TextFieldKey.ROW).sampleLength(4).sampleText("12A").build());

        tools.jackson.databind.JsonNode withoutSample = json.get("textFields").get(0);
        assertThat(withoutSample.get("sampleLength").asInt()).isEqualTo(6);
        assertThat(withoutSample.has("sampleText")).isFalse();
        assertThat(withoutSample.has("text")).isFalse();
        assertThat(withoutSample.has("lineBreaks")).isFalse();

        tools.jackson.databind.JsonNode withSample = json.get("textFields").get(1);
        assertThat(withSample.get("sampleLength").asInt()).isEqualTo(4);
        assertThat(withSample.get("sampleText").asText()).isEqualTo("12A");
    }

    @Test
    void response_aCustomField_hasText_andLineBreaksOnlyWhenNotEmpty() throws Exception {
        tools.jackson.databind.JsonNode json = createReturning(
                field(TextFieldKey.CUSTOM).text("Welcome").build(),
                field(TextFieldKey.CUSTOM).text("Doors open at 7").lineBreaks(List.of()).build(),
                field(TextFieldKey.CUSTOM).text("Doors open at 7").lineBreaks(List.of(5, 10)).build());

        tools.jackson.databind.JsonNode plain = json.get("textFields").get(0);
        assertThat(plain.get("text").asText()).isEqualTo("Welcome");
        assertThat(plain.has("lineBreaks")).isFalse();
        assertThat(plain.has("sampleLength")).isFalse();

        assertThat(json.get("textFields").get(1).has("lineBreaks")).as("an empty list is left out").isFalse();

        tools.jackson.databind.JsonNode broken = json.get("textFields").get(2);
        assertThat(broken.get("lineBreaks")).hasSize(2);
        assertThat(broken.get("lineBreaks").get(1).asInt()).isEqualTo(10);
    }

    @Test
    void response_rotationAndBold_areAlwaysPresent() throws Exception {
        tools.jackson.databind.JsonNode json = createReturning(
                field(TextFieldKey.EVENT_DATE).build(),
                field(TextFieldKey.EVENT_TIME).rotation(45).bold(true).build());

        tools.jackson.databind.JsonNode unrotated = json.get("textFields").get(0);
        assertThat(unrotated.has("rotation")).isTrue();
        assertThat(unrotated.get("rotation").isInt()).isTrue();
        assertThat(unrotated.get("rotation").asInt()).isZero();
        assertThat(unrotated.get("bold").isBoolean()).isTrue();
        assertThat(unrotated.get("bold").asBoolean()).isFalse();

        tools.jackson.databind.JsonNode rotated = json.get("textFields").get(1);
        assertThat(rotated.get("rotation").asInt()).isEqualTo(45);
        assertThat(rotated.get("bold").asBoolean()).isTrue();
    }

    @Test
    void response_otherMembersOfTheTemplate_stillCarryExplicitNulls() throws Exception {
        tools.jackson.databind.JsonNode json = createReturning();

        // null means something there ("applies to every ticket type"), so it must stay on the wire
        assertThat(json.has("ticketTypeId")).isTrue();
        assertThat(json.get("ticketTypeId").isNull()).isTrue();
        assertThat(json.has("codeType")).isTrue();
        assertThat(json.get("codeType").isNull()).isTrue();
        assertThat(json.has("ticketWidth")).isTrue();
    }

    @Test
    void update_returnsTheSameTrimmedShape() throws Exception {
        UUID templateId = UUID.randomUUID();
        TicketTemplate updated = template(templateId, UUID.randomUUID());
        updated.getTextFields().add(field(TextFieldKey.VENUE).build());
        when(ticketTemplateService.update(eq(templateId), any())).thenReturn(updated);

        tools.jackson.databind.JsonNode json = responseJson(mockMvc.perform(patch("/api/v1/ticket-templates/{templateId}", templateId)
                        .contentType("application/json")
                        .content("{\"ticketWidth\":900}"))
                .andExpect(status().isOk())
                .andReturn());

        tools.jackson.databind.JsonNode field = json.get("textFields").get(0);
        assertThat(field.has("sampleLength")).isFalse();
        assertThat(field.has("text")).isFalse();
        assertThat(field.get("rotation").asInt()).isZero();
    }

    @Test
    void request_missingAndExplicitNullMembers_areBothAccepted() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(ticketTemplateService.create(eq(eventId), any())).thenReturn(template(UUID.randomUUID(), eventId));
        String missing = "{\"format\":\"DIGITAL\",\"textFields\":[{\"key\":\"EVENT_NAME\",\"x\":10,\"y\":20,\"fontSize\":6,"
                + "\"color\":\"#112233\",\"align\":\"LEFT\"}]}";
        String explicitNulls = "{\"format\":\"DIGITAL\",\"textFields\":[{\"key\":\"EVENT_NAME\",\"x\":10,\"y\":20,\"fontSize\":6,"
                + "\"color\":\"#112233\",\"align\":\"LEFT\",\"bold\":null,\"rotation\":null,\"sampleLength\":null,"
                + "\"sampleText\":null,\"text\":null,\"lineBreaks\":null}]}";

        for (String body : new String[] {missing, explicitNulls}) {
            mockMvc.perform(post("/api/v1/events/{eventId}/ticket-templates", eventId)
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isCreated());
        }
    }

    // ---- delete ----

    @Test
    void delete_existingTemplate_returns204_withNoBody() throws Exception {
        UUID templateId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/ticket-templates/{templateId}", templateId))
                .andExpect(status().isNoContent())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));

        org.mockito.Mockito.verify(ticketTemplateService).delete(templateId);
    }

    @Test
    void delete_unknownTemplate_returns404() throws Exception {
        UUID templateId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Ticket template " + templateId + " not found"))
                .when(ticketTemplateService).delete(templateId);

        mockMvc.perform(delete("/api/v1/ticket-templates/{templateId}", templateId))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_unauthorizedCaller_returns403() throws Exception {
        UUID templateId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this event's ticket templates"))
                .when(ticketTemplateService).delete(templateId);

        mockMvc.perform(delete("/api/v1/ticket-templates/{templateId}", templateId))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_aMalformedId_returns400() throws Exception {
        mockMvc.perform(delete("/api/v1/ticket-templates/{templateId}", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    private TicketTemplate template(UUID id, UUID eventId) {
        return TicketTemplate.builder()
                .id(id)
                .eventId(eventId)
                .ticketTypeId(null)
                .format(TicketTemplateFormat.DIGITAL)
                .logoUrl("https://example.com/logo.png")
                .backgroundImageUrl("https://example.com/bg.png")
                .primaryColor("#ABCDEF")
                .build();
    }

    private String validCreateBody() {
        return """
                {"format":"DIGITAL","logoUrl":"https://example.com/logo.png",
                "backgroundImageUrl":"https://example.com/bg.png","primaryColor":"#ABCDEF"}
                """;
    }

    // ---- create() ----

    @Test
    void create_validRequest_returns201_withBrandingFields() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        when(ticketTemplateService.create(eq(eventId), any())).thenReturn(template(templateId, eventId));

        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-templates", eventId)
                        .contentType("application/json")
                        .content(validCreateBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(templateId.toString()))
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.format").value("DIGITAL"))
                .andExpect(jsonPath("$.logoUrl").value("https://example.com/logo.png"))
                .andExpect(jsonPath("$.backgroundImageUrl").value("https://example.com/bg.png"))
                .andExpect(jsonPath("$.primaryColor").value("#ABCDEF"));
    }

    @Test
    void create_missingFormat_returns400() throws Exception {
        UUID eventId = UUID.randomUUID();
        String body = """
                {"logoUrl":"https://example.com/logo.png"}
                """;

        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-templates", eventId)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_invalidLogoUrl_returns400() throws Exception {
        UUID eventId = UUID.randomUUID();
        String body = """
                {"format":"DIGITAL","logoUrl":"not-a-url"}
                """;

        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-templates", eventId)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_unknownEvent_returns404() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(ticketTemplateService.create(eq(eventId), any()))
                .thenThrow(new ResourceNotFoundException("Event " + eventId + " not found"));

        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-templates", eventId)
                        .contentType("application/json")
                        .content(validCreateBody()))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_unauthorizedCaller_returns403() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(ticketTemplateService.create(eq(eventId), any()))
                .thenThrow(new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this event's ticket templates"));

        mockMvc.perform(post("/api/v1/events/{eventId}/ticket-templates", eventId)
                        .contentType("application/json")
                        .content(validCreateBody()))
                .andExpect(status().isForbidden());
    }

    // ---- list() ----

    @Test
    void list_existingEvent_returns200() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(ticketTemplateService.list(eventId)).thenReturn(List.of(template(UUID.randomUUID(), eventId)));

        mockMvc.perform(get("/api/v1/events/{eventId}/ticket-templates", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value(eventId.toString()));
    }

    @Test
    void list_unknownEvent_returns404() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(ticketTemplateService.list(eventId)).thenThrow(new ResourceNotFoundException("Event " + eventId + " not found"));

        mockMvc.perform(get("/api/v1/events/{eventId}/ticket-templates", eventId))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_unauthorizedCaller_returns403() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(ticketTemplateService.list(eventId))
                .thenThrow(new ForbiddenException("Only the organization's owner, organizer, or an admin may manage this event's ticket templates"));

        mockMvc.perform(get("/api/v1/events/{eventId}/ticket-templates", eventId))
                .andExpect(status().isForbidden());
    }
}
