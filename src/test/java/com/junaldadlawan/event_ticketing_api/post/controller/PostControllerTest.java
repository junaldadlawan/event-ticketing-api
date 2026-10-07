package com.junaldadlawan.event_ticketing_api.post.controller;

import com.junaldadlawan.event_ticketing_api.auth.security.JwtAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.checkin.security.DeviceAuthenticationFilter;
import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.post.dto.PostResponse;
import com.junaldadlawan.event_ticketing_api.post.enums.PostKind;
import com.junaldadlawan.event_ticketing_api.post.enums.PostStatus;
import com.junaldadlawan.event_ticketing_api.post.service.PostService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
@AutoConfigureMockMvc(addFilters = false)
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private DeviceAuthenticationFilter deviceAuthenticationFilter;

    private PostResponse response(UUID eventId, String eventTitle, PostKind kind, String title) {
        return new PostResponse(UUID.randomUUID(), eventId, eventTitle, kind, title, "details", null, null, null, false,
                com.junaldadlawan.event_ticketing_api.post.enums.PostStatus.LIVE, Instant.parse("2026-10-08T10:00:00Z"));
    }

    // ---- create ----

    @Test
    void create_returns201_withThePost() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(postService.create(any())).thenReturn(response(eventId, "Summer Fest", PostKind.SALE, "Early bird"));

        mockMvc.perform(post("/api/v1/posts").contentType("application/json")
                        .content("{\"eventId\":\"" + eventId + "\",\"kind\":\"SALE\",\"title\":\"Early bird\",\"body\":\"20% off\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.eventTitle").value("Summer Fest"))
                .andExpect(jsonPath("$.kind").value("SALE"))
                .andExpect(jsonPath("$.title").value("Early bird"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void create_aSiteWidePostWithoutEventIdOrBody_isAccepted() throws Exception {
        when(postService.create(any())).thenReturn(response(null, null, PostKind.ANNOUNCEMENT, "Welcome"));

        mockMvc.perform(post("/api/v1/posts").contentType("application/json")
                        .content("{\"kind\":\"ANNOUNCEMENT\",\"title\":\"Welcome\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").isEmpty());
    }

    @Test
    void create_invalidBodies_return400() throws Exception {
        for (String body : new String[] {
                "{\"title\":\"x\"}",
                "{\"kind\":\"SALE\"}",
                "{\"kind\":\"SALE\",\"title\":\"\"}",
                "{\"kind\":\"SALE\",\"title\":\"   \"}",
                "{\"kind\":\"SALE\",\"title\":\"" + "T".repeat(121) + "\"}",
                "{\"kind\":\"SALE\",\"title\":\"ok\",\"body\":\"" + "B".repeat(2001) + "\"}",
                "{\"kind\":\"SALE\",\"title\":\"<b>hi</b>\"}",
                "{\"kind\":\"SALE\",\"title\":\"ok\",\"body\":\"<script>x</script>\"}",
                "{\"kind\":\"OFFER\",\"title\":\"ok\"}",
                "{\"kind\":\"SALE\",\"title\":\"ok\",\"eventId\":\"not-a-uuid\"}"}) {
            mockMvc.perform(post("/api/v1/posts").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void create_atTheLimits_isAccepted() throws Exception {
        when(postService.create(any())).thenReturn(response(null, null, PostKind.SALE, "t"));

        mockMvc.perform(post("/api/v1/posts").contentType("application/json")
                        .content("{\"kind\":\"SALE\",\"title\":\"" + "T".repeat(120) + "\",\"body\":\"" + "B".repeat(2000) + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void create_nonAdminAndUnknownEvent_return403And404() throws Exception {
        UUID missing = UUID.randomUUID();
        when(postService.create(any()))
                .thenThrow(new ForbiddenException("Admin access required"))
                .thenThrow(new ResourceNotFoundException("Event " + missing + " not found"));

        mockMvc.perform(post("/api/v1/posts").contentType("application/json").content("{\"kind\":\"SALE\",\"title\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/posts").contentType("application/json")
                        .content("{\"eventId\":\"" + missing + "\",\"kind\":\"SALE\",\"title\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    // ---- list ----

    @Test
    void list_returnsAPageOfPosts() throws Exception {
        PostResponse first = response(UUID.randomUUID(), "Summer Fest", PostKind.SALE, "Early bird");
        PostResponse second = response(null, null, PostKind.ANNOUNCEMENT, "Welcome");
        when(postService.list(eq(null), eq(false), eq(false), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(first, second)));

        mockMvc.perform(get("/api/v1/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].eventTitle").value("Summer Fest"))
                .andExpect(jsonPath("$.content[1].eventId").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void list_passesTheFiltersAndPageThrough() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(postService.list(eq(eventId), eq(false), eq(false), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(postService.list(eq(null), eq(true), eq(false), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/posts").param("eventId", eventId.toString()).param("page", "1").param("size", "5"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/posts").param("siteWide", "true")).andExpect(status().isOk());

        verify(postService).list(eq(eventId), eq(false), eq(false), org.mockito.ArgumentMatchers.argThat(p -> p.getPageNumber() == 1 && p.getPageSize() == 5));
        verify(postService).list(eq(null), eq(true), eq(false), any(Pageable.class));
    }

    @Test
    void list_defaultPageSizeIs20() throws Exception {
        when(postService.list(any(), eq(false), eq(false), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/posts")).andExpect(status().isOk());

        verify(postService).list(eq(null), eq(false), eq(false), org.mockito.ArgumentMatchers.argThat(p -> p.getPageSize() == 20));
    }

    @Test
    void list_badParameters_return400() throws Exception {
        mockMvc.perform(get("/api/v1/posts").param("eventId", "not-a-uuid")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/posts").param("siteWide", "maybe")).andExpect(status().isBadRequest());
        when(postService.list(any(), eq(true), eq(false), any(Pageable.class))).thenThrow(new BadRequestException("eventId and siteWide cannot be combined"));
        mockMvc.perform(get("/api/v1/posts").param("eventId", UUID.randomUUID().toString()).param("siteWide", "true"))
                .andExpect(status().isBadRequest());
    }

    // ---- delete ----

    @Test
    void delete_returns204_withNoBody() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/posts/{id}", id)).andExpect(status().isNoContent()).andExpect(content().string(""));

        verify(postService).delete(id);
    }

    @Test
    void delete_unknownForbiddenAndMalformed_mapToTheirStatuses() throws Exception {
        UUID missing = UUID.randomUUID();
        UUID forbidden = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Post " + missing + " not found")).when(postService).delete(missing);
        doThrow(new ForbiddenException("Admin access required")).when(postService).delete(forbidden);

        mockMvc.perform(delete("/api/v1/posts/{id}", missing)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/posts/{id}", forbidden)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/posts/{id}", "not-a-uuid")).andExpect(status().isBadRequest());
    }

    // ---- schedule, expiry and hiding ----

    @Test
    void create_withScheduleExpiryAndHidden_passesThemToTheService_andReturnsThem() throws Exception {
        Instant publishAt = Instant.parse("2030-01-01T10:00:00Z");
        Instant expiresAt = Instant.parse("2030-01-31T10:00:00Z");
        when(postService.create(any())).thenReturn(new PostResponse(UUID.randomUUID(), null, null, PostKind.SALE, "Flash", "", null,
                publishAt, expiresAt, true, PostStatus.HIDDEN, Instant.now()));

        mockMvc.perform(post("/api/v1/posts").contentType("application/json")
                        .content("{\"kind\":\"SALE\",\"title\":\"Flash\",\"publishAt\":\"2030-01-01T10:00:00Z\","
                                + "\"expiresAt\":\"2030-01-31T10:00:00Z\",\"hidden\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publishAt").value("2030-01-01T10:00:00Z"))
                .andExpect(jsonPath("$.expiresAt").value("2030-01-31T10:00:00Z"))
                .andExpect(jsonPath("$.hidden").value(true))
                .andExpect(jsonPath("$.status").value("HIDDEN"));

        verify(postService).create(argThat(r -> publishAt.equals(r.publishAt())
                && expiresAt.equals(r.expiresAt()) && Boolean.TRUE.equals(r.hidden())));
    }

    @Test
    void create_withAMalformedDate_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/posts").contentType("application/json")
                        .content("{\"kind\":\"SALE\",\"title\":\"x\",\"publishAt\":\"next tuesday\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_allTrue_isPassedToTheService() throws Exception {
        when(postService.list(eq(null), eq(false), eq(true), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/posts").param("all", "true")).andExpect(status().isOk());

        verify(postService).list(eq(null), eq(false), eq(true), any(Pageable.class));
    }

    @Test
    void list_allTrueForANonAdmin_returns403() throws Exception {
        when(postService.list(any(), eq(false), eq(true), any(Pageable.class))).thenThrow(new ForbiddenException("Admin access required"));

        mockMvc.perform(get("/api/v1/posts").param("all", "true")).andExpect(status().isForbidden());
    }

    @Test
    void update_returns200_withThePost_andPassesTheFields() throws Exception {
        UUID id = UUID.randomUUID();
        when(postService.update(eq(id), any())).thenReturn(response(null, null, PostKind.SALE, "New title"));

        mockMvc.perform(patch("/api/v1/posts/{id}", id).contentType("application/json")
                        .content("{\"title\":\"New title\",\"hidden\":true,\"clearExpiresAt\":true,\"expiresAt\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New title"));

        verify(postService).update(eq(id), argThat(r -> "New title".equals(r.title())
                && Boolean.TRUE.equals(r.hidden()) && Boolean.TRUE.equals(r.clearExpiresAt()) && r.clearPublishAt() == null && r.expiresAt() == null));
    }

    @Test
    void update_anEmptyBody_isAccepted_andInvalidFieldsReturn400() throws Exception {
        UUID id = UUID.randomUUID();
        when(postService.update(eq(id), any())).thenReturn(response(null, null, PostKind.SALE, "x"));

        mockMvc.perform(patch("/api/v1/posts/{id}", id).contentType("application/json").content("{}")).andExpect(status().isOk());
        for (String body : new String[] {
                "{\"title\":\"" + "T".repeat(121) + "\"}",
                "{\"body\":\"" + "B".repeat(2001) + "\"}",
                "{\"title\":\"<b>x</b>\"}",
                "{\"kind\":\"BANNER\"}",
                "{\"hidden\":\"maybe\"}",
                "{\"expiresAt\":\"soon\"}"}) {
            mockMvc.perform(patch("/api/v1/posts/{id}", id).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void update_unknownForbiddenAndMalformedId_mapToTheirStatuses() throws Exception {
        UUID missing = UUID.randomUUID();
        UUID forbidden = UUID.randomUUID();
        when(postService.update(eq(missing), any())).thenThrow(new ResourceNotFoundException("Post " + missing + " not found"));
        when(postService.update(eq(forbidden), any())).thenThrow(new ForbiddenException("Admin access required"));

        mockMvc.perform(patch("/api/v1/posts/{id}", missing).contentType("application/json").content("{}")).andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/posts/{id}", forbidden).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/posts/{id}", "nope").contentType("application/json").content("{}")).andExpect(status().isBadRequest());
    }

    // ---- picture ----

    @Test
    void create_withAnImageUrl_passesItToTheService_andReturnsIt() throws Exception {
        String url = "http://localhost:8081/api/v1/uploads/files/11111111-1111-1111-1111-111111111111.png";
        when(postService.create(any())).thenReturn(new PostResponse(UUID.randomUUID(), null, null, PostKind.SALE, "Flash", "", url,
                null, null, false, PostStatus.LIVE, Instant.now()));

        mockMvc.perform(post("/api/v1/posts").contentType("application/json")
                        .content("{\"kind\":\"SALE\",\"title\":\"Flash\",\"imageUrl\":\"" + url + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").value(url));

        verify(postService).create(argThat(r -> url.equals(r.imageUrl())));
    }

    @Test
    void create_aPostWithoutAPicture_returnsANullImageUrl() throws Exception {
        when(postService.create(any())).thenReturn(response(null, null, PostKind.SALE, "Flash"));

        mockMvc.perform(post("/api/v1/posts").contentType("application/json").content("{\"kind\":\"SALE\",\"title\":\"Flash\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").isEmpty());
    }

    @Test
    void createAndUpdate_anImageUrlOver500Characters_return400() throws Exception {
        String longUrl = "http://localhost/" + "a".repeat(500);

        mockMvc.perform(post("/api/v1/posts").contentType("application/json")
                        .content("{\"kind\":\"SALE\",\"title\":\"x\",\"imageUrl\":\"" + longUrl + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/posts/{id}", UUID.randomUUID()).contentType("application/json")
                        .content("{\"imageUrl\":\"" + longUrl + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_aLinkThatIsNotOurs_isAnErrorFromTheService_mappedTo400() throws Exception {
        when(postService.create(any())).thenThrow(new BadRequestException("imageUrl must be the URL of an image uploaded through POST /api/v1/uploads"));

        mockMvc.perform(post("/api/v1/posts").contentType("application/json")
                        .content("{\"kind\":\"SALE\",\"title\":\"x\",\"imageUrl\":\"https://evil.example.com/pixel.png\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_passesTheImageUrlThrough_includingTheEmptyStringThatRemovesIt() throws Exception {
        UUID id = UUID.randomUUID();
        when(postService.update(eq(id), any())).thenReturn(response(null, null, PostKind.SALE, "x"));

        mockMvc.perform(patch("/api/v1/posts/{id}", id).contentType("application/json").content("{\"imageUrl\":\"\"}"))
                .andExpect(status().isOk());

        verify(postService).update(eq(id), argThat(r -> "".equals(r.imageUrl())));
    }
}
