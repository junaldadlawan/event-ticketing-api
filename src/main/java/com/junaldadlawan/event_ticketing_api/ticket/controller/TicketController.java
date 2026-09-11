package com.junaldadlawan.event_ticketing_api.ticket.controller;

import com.junaldadlawan.event_ticketing_api.checkin.dto.CheckInRecordResponse;
import com.junaldadlawan.event_ticketing_api.checkin.service.CheckInService;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.ticket.artifact.RenderedTicketArtifact;
import com.junaldadlawan.event_ticketing_api.ticket.artifact.TicketArtifactService;
import com.junaldadlawan.event_ticketing_api.ticket.dto.TicketResponse;
import com.junaldadlawan.event_ticketing_api.ticket.service.TicketService;
import com.junaldadlawan.event_ticketing_api.tickettemplate.enums.TicketTemplateFormat;
import com.junaldadlawan.event_ticketing_api.tickettransfer.dto.TicketTransferRequest;
import com.junaldadlawan.event_ticketing_api.tickettransfer.dto.TicketTransferResponse;
import com.junaldadlawan.event_ticketing_api.tickettransfer.service.TicketTransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final TicketArtifactService ticketArtifactService;
    private final TicketTransferService ticketTransferService;
    private final CheckInService checkInService;

    @GetMapping("/{ticketId}")
    public TicketResponse get(@PathVariable UUID ticketId) {
        return TicketResponse.from(ticketService.getTicket(ticketId));
    }

    @PostMapping("/{ticketId}/transfer")
    public TicketResponse transfer(@PathVariable UUID ticketId, @Valid @RequestBody TicketTransferRequest request) {
        return TicketResponse.from(ticketTransferService.transfer(ticketId, request.toUserId()));
    }

    @GetMapping("/{ticketId}/transfers")
    public List<TicketTransferResponse> listTransfers(@PathVariable UUID ticketId) {
        return ticketTransferService.listTransfers(ticketId).stream().map(TicketTransferResponse::from).toList();
    }

    @GetMapping("/{ticketId}/artifact")
    public ResponseEntity<byte[]> getArtifact(@PathVariable UUID ticketId, @RequestParam String format) {
        TicketTemplateFormat parsedFormat = parseFormat(format);
        RenderedTicketArtifact artifact = ticketArtifactService.render(ticketId, parsedFormat);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(artifact.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + artifact.filename() + "\"")
                .body(artifact.content());
    }

    @GetMapping("/{ticketId}/check-in-records")
    public List<CheckInRecordResponse> listCheckInRecords(@PathVariable UUID ticketId) {
        return checkInService.listTicketCheckInRecords(ticketId);
    }

    private TicketTemplateFormat parseFormat(String format) {
        try {
            return TicketTemplateFormat.valueOf(format.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("format must be one of: digital, physical");
        }
    }
}
