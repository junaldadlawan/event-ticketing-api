package com.junaldadlawan.event_ticketing_api.seatmap.controller;

import com.junaldadlawan.event_ticketing_api.seatmap.dto.SeatMapResponse;
import com.junaldadlawan.event_ticketing_api.seatmap.dto.SeatResponse;
import com.junaldadlawan.event_ticketing_api.seatmap.entity.SeatMap;
import com.junaldadlawan.event_ticketing_api.seatmap.service.SeatMapService;
import lombok.RequiredArgsConstructor;
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

    @GetMapping
    public SeatMapResponse get(@PathVariable UUID eventId) {
        SeatMap seatMap = seatMapService.getSeatMap(eventId);
        var seats = seatMapService.getSeats(seatMap.getId()).stream().map(SeatResponse::from).toList();
        return SeatMapResponse.from(seatMap, seats);
    }
}
