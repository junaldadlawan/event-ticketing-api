package com.junaldadlawan.event_ticketing_api.event;

import com.junaldadlawan.event_ticketing_api.event.dto.EventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    public EventResponse create() {

    }


}
