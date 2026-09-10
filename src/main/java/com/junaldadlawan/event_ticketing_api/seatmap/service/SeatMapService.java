package com.junaldadlawan.event_ticketing_api.seatmap.service;

import com.junaldadlawan.event_ticketing_api.seatmap.entity.Seat;
import com.junaldadlawan.event_ticketing_api.seatmap.entity.SeatMap;

import java.util.List;
import java.util.UUID;

public interface SeatMapService {

    /** Fetches the event's seat map, applying draft-visibility gating. */
    SeatMap getSeatMap(UUID eventId);

    /** Fetches an already-authorized seat map's seats. */
    List<Seat> getSeats(UUID seatMapId);
}
