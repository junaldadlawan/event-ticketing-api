package com.junaldadlawan.event_ticketing_api.seatmap.controller;

import com.junaldadlawan.event_ticketing_api.seatmap.dto.SeatMapResponse;
import com.junaldadlawan.event_ticketing_api.seatmap.dto.SeatResponse;
import com.junaldadlawan.event_ticketing_api.seatmap.entity.SeatMap;
import com.junaldadlawan.event_ticketing_api.seatmap.service.SeatMapService;
import com.junaldadlawan.event_ticketing_api.venue.layout.VenueLayout;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events/{eventId}/seatmap")
@RequiredArgsConstructor
public class SeatMapController {

    private final SeatMapService seatMapService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public SeatMapResponse get(@PathVariable UUID eventId) {
        SeatMap seatMap = seatMapService.getSeatMap(eventId);
        var seats = seatMapService.getSeats(seatMap.getId()).stream().map(SeatResponse::from).toList();
        List<VenueLayout.Shape> shapes = seatMap.getLayoutShapes() == null ? null
                : objectMapper.readValue(seatMap.getLayoutShapes(), new TypeReference<List<VenueLayout.Shape>>() { });
        return SeatMapResponse.from(seatMap, seats, shapes);
    }
}
