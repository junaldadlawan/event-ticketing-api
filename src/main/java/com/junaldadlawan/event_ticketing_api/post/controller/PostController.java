package com.junaldadlawan.event_ticketing_api.post.controller;

import com.junaldadlawan.event_ticketing_api.common.dto.PageResponse;
import com.junaldadlawan.event_ticketing_api.post.dto.PostCreateRequest;
import com.junaldadlawan.event_ticketing_api.post.dto.PostResponse;
import com.junaldadlawan.event_ticketing_api.post.dto.PostUpdateRequest;
import com.junaldadlawan.event_ticketing_api.post.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Announcements and sales: everyone reads the live ones, only admins write, change, hide or remove them. */
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse create(@Valid @RequestBody PostCreateRequest request) {
        return postService.create(request);
    }

    @GetMapping
    public PageResponse<PostResponse> list(
            @RequestParam(required = false) UUID eventId,
            @RequestParam(defaultValue = "false") boolean siteWide,
            @RequestParam(defaultValue = "false") boolean all,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(postService.list(eventId, siteWide, all, pageable));
    }

    @PatchMapping("/{postId}")
    public PostResponse update(@PathVariable UUID postId, @Valid @RequestBody PostUpdateRequest request) {
        return postService.update(postId, request);
    }

    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID postId) {
        postService.delete(postId);
    }
}
