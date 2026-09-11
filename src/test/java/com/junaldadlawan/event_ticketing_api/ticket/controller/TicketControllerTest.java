package com.junaldadlawan.event_ticketing_api.ticket.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.ticket.artifact.RenderedTicketArtifact;
import com.junaldadlawan.event_ticketing_api.ticket.artifact.TicketArtifactService;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import com.junaldadlawan.event_ticketing_api.ticket.service.TicketService;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TicketTemplateFormat;
import com.junaldadlawan.event_ticketing_api.tickettransfer.entity.TicketTransfer;
import com.junaldadlawan.event_ticketing_api.tickettransfer.enums.TransferSource;
import com.junaldadlawan.event_ticketing_api.tickettransfer.service.TicketTransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link TicketController}'s own behavior (response mapping,
 * status codes) — mirrors {@code TicketTypeControllerTest}. Security-filter
 * enforcement (401/403, cross-org rejection) is exercised separately in the
 * full-stack {@code TicketAccessIntegrationTest}. The one assertion that
 * matters most here — {@code credential} never appears in the JSON body —
 * is checked explicitly, not just implied by other fields being present.
 */
@WebMvcTest(TicketController.class)
@AutoConfigureMockMvc(addFilters = false)
class TicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketService ticketService;

    @MockitoBean
    private TicketArtifactService ticketArtifactService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private TicketTransferService ticketTransferService;

    private Ticket ticket(UUID id) {
        return Ticket.builder()
                .id(id)
                .orderId(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .ticketTypeId(UUID.randomUUID())
                .seatId(null)
                .ownerId(UUID.randomUUID())
                .ticketNumber("ABC-A2B3C4")
                .credential("super-secret-hmac-signed-value-must-never-leak")
                .status(TicketStatus.VALID)
                .build();
    }

    @Test
    void get_existingTicket_returns200_withoutCredential() throws Exception {
        UUID ticketId = UUID.randomUUID();
        when(ticketService.getTicket(ticketId)).thenReturn(ticket(ticketId));

        mockMvc.perform(get("/api/v1/tickets/{ticketId}", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketId.toString()))
                .andExpect(jsonPath("$.ticketNumber").value("ABC-A2B3C4"))
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.credential").doesNotExist());
    }

    @Test
    void get_unknownTicket_returns404() throws Exception {
        UUID ticketId = UUID.randomUUID();
        when(ticketService.getTicket(ticketId))
                .thenThrow(new ResourceNotFoundException("Ticket " + ticketId + " not found"));

        mockMvc.perform(get("/api/v1/tickets/{ticketId}", ticketId))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_unauthorizedCaller_returns403() throws Exception {
        UUID ticketId = UUID.randomUUID();
        when(ticketService.getTicket(ticketId))
                .thenThrow(new ForbiddenException(
                        "Only the ticket's owning buyer, the event's organizer/owner, or an admin may view this ticket"));

        mockMvc.perform(get("/api/v1/tickets/{ticketId}", ticketId))
                .andExpect(status().isForbidden());
    }

    @Test
    void getArtifact_digitalFormat_returns200_withPngContentType() throws Exception {
        UUID ticketId = UUID.randomUUID();
        byte[] pngBytes = {1, 2, 3};
        when(ticketArtifactService.render(ticketId, TicketTemplateFormat.DIGITAL))
                .thenReturn(new RenderedTicketArtifact(pngBytes, "image/png", "ticket-ABC-A2B3C4.png"));

        mockMvc.perform(get("/api/v1/tickets/{ticketId}/artifact", ticketId).param("format", "digital"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
    }

    @Test
    void getArtifact_physicalFormat_returns200_withPdfContentType() throws Exception {
        UUID ticketId = UUID.randomUUID();
        byte[] pdfBytes = {1, 2, 3};
        when(ticketArtifactService.render(ticketId, TicketTemplateFormat.PHYSICAL))
                .thenReturn(new RenderedTicketArtifact(pdfBytes, "application/pdf", "ticket-ABC-A2B3C4.pdf"));

        mockMvc.perform(get("/api/v1/tickets/{ticketId}/artifact", ticketId).param("format", "physical"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
    }

    @Test
    void getArtifact_invalidFormat_returns400() throws Exception {
        UUID ticketId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/tickets/{ticketId}/artifact", ticketId).param("format", "xyz"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Regression test for the {@code GlobalExceptionHandler} fix shipped in
     * this same dispatch ({@code MissingServletRequestParameterException} ->
     * 400): {@code format} is a required query param with no default, so
     * omitting it entirely must still 400 (not fall through to a 500 or,
     * pre-fix, surface as an unhandled exception). No test for this existed
     * anywhere in the suite before this pass, despite it being a generic,
     * app-wide fix.
     */
    @Test
    void getArtifact_missingFormatParam_returns400() throws Exception {
        UUID ticketId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/tickets/{ticketId}/artifact", ticketId))
                .andExpect(status().isBadRequest());
    }

    // ---- POST /tickets/{ticketId}/transfer (Phase 7) ----

    @Test
    void transfer_validRequest_returns200_withUpdatedOwnerAndWithoutCredential() throws Exception {
        UUID ticketId = UUID.randomUUID();
        UUID toUserId = UUID.randomUUID();
        Ticket transferred = ticket(ticketId);
        transferred.setOwnerId(toUserId);
        transferred.setCredentialVersion(1);
        when(ticketTransferService.transfer(ticketId, toUserId)).thenReturn(transferred);

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/transfer", ticketId)
                        .contentType("application/json")
                        .content("{\"toUserId\":\"" + toUserId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(toUserId.toString()))
                .andExpect(jsonPath("$.credential").doesNotExist());
    }

    @Test
    void transfer_missingToUserId_returns400() throws Exception {
        UUID ticketId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/transfer", ticketId)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_unknownTicket_returns404() throws Exception {
        UUID ticketId = UUID.randomUUID();
        when(ticketTransferService.transfer(any(), any()))
                .thenThrow(new ResourceNotFoundException("Ticket " + ticketId + " not found"));

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/transfer", ticketId)
                        .contentType("application/json")
                        .content("{\"toUserId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void transfer_nonOwningCaller_returns403() throws Exception {
        UUID ticketId = UUID.randomUUID();
        when(ticketTransferService.transfer(any(), any()))
                .thenThrow(new ForbiddenException("Only the ticket's owning buyer may transfer it"));

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/transfer", ticketId)
                        .contentType("application/json")
                        .content("{\"toUserId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void transfer_ticketNotValid_returns409() throws Exception {
        UUID ticketId = UUID.randomUUID();
        when(ticketTransferService.transfer(any(), any()))
                .thenThrow(new com.junaldadlawan.event_ticketing_api.common.exception.ConflictException("Only a valid ticket may be transferred"));

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/transfer", ticketId)
                        .contentType("application/json")
                        .content("{\"toUserId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void transfer_recipientIsCurrentOwner_returns400() throws Exception {
        UUID ticketId = UUID.randomUUID();
        when(ticketTransferService.transfer(any(), any()))
                .thenThrow(new com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException("Cannot transfer a ticket to its own current owner"));

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/transfer", ticketId)
                        .contentType("application/json")
                        .content("{\"toUserId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---- GET /tickets/{ticketId}/transfers (Phase 7) ----

    @Test
    void listTransfers_existingTicket_returns200_withOrderedHistory() throws Exception {
        UUID ticketId = UUID.randomUUID();
        TicketTransfer transfer = TicketTransfer.builder()
                .id(UUID.randomUUID()).ticketId(ticketId).fromUserId(UUID.randomUUID()).toUserId(UUID.randomUUID())
                .source(TransferSource.DIRECT_TRANSFER).transferredAt(Instant.now()).build();
        when(ticketTransferService.listTransfers(ticketId)).thenReturn(List.of(transfer));

        mockMvc.perform(get("/api/v1/tickets/{ticketId}/transfers", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticketId").value(ticketId.toString()))
                .andExpect(jsonPath("$[0].source").value("DIRECT_TRANSFER"));
    }

    @Test
    void listTransfers_unauthorizedCaller_returns403() throws Exception {
        UUID ticketId = UUID.randomUUID();
        when(ticketTransferService.listTransfers(ticketId))
                .thenThrow(new ForbiddenException("Only the ticket's owning buyer, the event's organizer/owner, or an admin may view this ticket"));

        mockMvc.perform(get("/api/v1/tickets/{ticketId}/transfers", ticketId))
                .andExpect(status().isForbidden());
    }

    @Test
    void listTransfers_unknownTicket_returns404() throws Exception {
        UUID ticketId = UUID.randomUUID();
        when(ticketTransferService.listTransfers(ticketId))
                .thenThrow(new ResourceNotFoundException("Ticket " + ticketId + " not found"));

        mockMvc.perform(get("/api/v1/tickets/{ticketId}/transfers", ticketId))
                .andExpect(status().isNotFound());
    }
}
