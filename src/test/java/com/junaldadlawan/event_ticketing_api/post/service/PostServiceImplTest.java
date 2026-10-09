package com.junaldadlawan.event_ticketing_api.post.service;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.ForbiddenException;
import com.junaldadlawan.event_ticketing_api.common.exception.ResourceNotFoundException;
import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import com.junaldadlawan.event_ticketing_api.event.repository.EventRepository;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.post.dto.PostCreateRequest;
import com.junaldadlawan.event_ticketing_api.post.dto.PostResponse;
import com.junaldadlawan.event_ticketing_api.post.dto.PostUpdateRequest;
import com.junaldadlawan.event_ticketing_api.post.entity.Post;
import com.junaldadlawan.event_ticketing_api.post.enums.PostKind;
import com.junaldadlawan.event_ticketing_api.post.enums.PostStatus;
import com.junaldadlawan.event_ticketing_api.post.repository.PostRepository;
import com.junaldadlawan.event_ticketing_api.upload.service.UploadedFileUrls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceImplTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private OrganizationAccessGuard accessGuard;
    @Mock
    private UploadedFileUrls uploadedFileUrls;

    private PostServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PostServiceImpl(postRepository, eventRepository, accessGuard, uploadedFileUrls);
    }

    private Event event(UUID id, String title) {
        Instant start = Instant.now().plusSeconds(86_400);
        return Event.builder().id(id).organizationId(UUID.randomUUID()).title(title).description("d").category("Music")
                .status(EventStatus.PUBLISHED).ticketPrefix("ABC").startAt(start).endAt(start.plusSeconds(7200)).timezone("UTC").build();
    }

    private Post post(UUID id, UUID eventId, PostKind kind, String title) {
        return Post.builder().id(id).eventId(eventId).kind(kind).title(title).body("details").build();
    }

    // ---- create ----

    @Test
    void create_siteWidePost_savesItWithoutAnEvent() {
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostResponse response = service.create(new PostCreateRequest(null, PostKind.ANNOUNCEMENT, "  Doors at 7  ", "  Bring ID  "));

        ArgumentCaptor<Post> saved = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(saved.capture());
        assertThat(saved.getValue().getEventId()).isNull();
        assertThat(saved.getValue().getTitle()).isEqualTo("Doors at 7");
        assertThat(saved.getValue().getBody()).isEqualTo("Bring ID");
        assertThat(response.eventId()).isNull();
        assertThat(response.eventTitle()).isNull();
        assertThat(response.kind()).isEqualTo(PostKind.ANNOUNCEMENT);
        verify(accessGuard).requireAdmin();
    }

    @Test
    void create_eventPost_carriesTheEventsTitle() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event(eventId, "Summer Fest")));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostResponse response = service.create(new PostCreateRequest(eventId, PostKind.SALE, "Early bird", "20% off"));

        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.eventTitle()).isEqualTo("Summer Fest");
        assertThat(response.kind()).isEqualTo(PostKind.SALE);
    }

    @Test
    void create_missingBody_isStoredAsEmpty() {
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.create(new PostCreateRequest(null, PostKind.SALE, "Flash sale", null)).body()).isEmpty();
    }

    @Test
    void create_unknownOrDeletedEvent_throwsResourceNotFound_andSavesNothing() {
        UUID eventId = UUID.randomUUID();
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new PostCreateRequest(eventId, PostKind.SALE, "Early bird", null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(postRepository, never()).save(any());
    }

    @Test
    void create_aNonAdmin_throwsForbidden_andNothingElseIsTouched() {
        doThrow(new ForbiddenException("Admin access required")).when(accessGuard).requireAdmin();

        assertThatThrownBy(() -> service.create(new PostCreateRequest(null, PostKind.SALE, "x", null)))
                .isInstanceOf(ForbiddenException.class);
        verify(postRepository, never()).save(any());
        verify(eventRepository, never()).findByIdAndDeletedAtIsNull(any());
    }

    @Test
    void create_aTitleThatIsOnlyWhitespace_throwsBadRequest() {
        assertThatThrownBy(() -> service.create(new PostCreateRequest(null, PostKind.SALE, "    ", null)))
                .isInstanceOf(BadRequestException.class);
        verify(postRepository, never()).save(any());
    }

    // ---- list ----

    @Test
    void list_default_isSiteWidePlusPostsOfVisibleEvents_withEventTitlesFilledInOneLookup() {
        UUID eventId = UUID.randomUUID();
        Post siteWide = post(UUID.randomUUID(), null, PostKind.ANNOUNCEMENT, "Welcome");
        Post forEvent = post(UUID.randomUUID(), eventId, PostKind.SALE, "Early bird");
        when(postRepository.findVisible(any(), any(Instant.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(forEvent, siteWide)));
        when(eventRepository.findAllById(any())).thenReturn(List.of(event(eventId, "Summer Fest")));

        Page<PostResponse> page = service.list(null, false, false, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(PostResponse::title).containsExactly("Early bird", "Welcome");
        assertThat(page.getContent().get(0).eventTitle()).isEqualTo("Summer Fest");
        assertThat(page.getContent().get(1).eventTitle()).isNull();
        verify(eventRepository).findAllById(java.util.Set.of(eventId));
    }

    @Test
    void list_onlySiteWidePosts_doNotLookUpAnyEvent() {
        when(postRepository.findSiteWide(any(Instant.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(post(UUID.randomUUID(), null, PostKind.SALE, "x"))));

        service.list(null, true, false, PageRequest.of(0, 20));

        verify(eventRepository, never()).findAllById(any());
    }

    @Test
    void list_forOneEvent_usesTheEventQuery_withOnlyVisibleStatuses() {
        UUID eventId = UUID.randomUUID();
        when(postRepository.findVisibleForEvent(eq(eventId), any(), any(Instant.class), any(Pageable.class))).thenReturn(Page.empty());

        service.list(eventId, false, false, PageRequest.of(0, 20));

        ArgumentCaptor<java.util.Collection<EventStatus>> statuses = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(postRepository).findVisibleForEvent(eq(eventId), statuses.capture(), any(Instant.class), any(Pageable.class));
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(EventStatus.PUBLISHED, EventStatus.ON_SALE,
                EventStatus.SOLD_OUT, EventStatus.COMPLETED);
        assertThat(statuses.getValue()).doesNotContain(EventStatus.DRAFT, EventStatus.CANCELLED, EventStatus.SUSPENDED);
    }

    @Test
    void list_eventIdTogetherWithSiteWide_throwsBadRequest() {
        assertThatThrownBy(() -> service.list(UUID.randomUUID(), true, false, PageRequest.of(0, 20)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void list_capsThePageSizeAt50_andIgnoresAClientSort() {
        when(postRepository.findVisible(any(), any(Instant.class), any(Pageable.class))).thenReturn(Page.empty());

        service.list(null, false, false, PageRequest.of(2, 500, Sort.by("title")));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findVisible(any(), any(Instant.class), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getSort().isSorted()).isFalse();
    }

    @Test
    void list_aTinyPageSizeIsKept_andUnpagedFallsBackToTheDefault() {
        when(postRepository.findVisible(any(), any(Instant.class), any(Pageable.class))).thenReturn(Page.empty());

        service.list(null, false, false, PageRequest.of(0, 1));
        service.list(null, false, false, Pageable.unpaged());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository, org.mockito.Mockito.times(2)).findVisible(any(), any(Instant.class), pageable.capture());
        assertThat(pageable.getAllValues().get(0).getPageSize()).isEqualTo(1);
        assertThat(pageable.getAllValues().get(1).getPageSize()).isEqualTo(PostServiceImpl.DEFAULT_PAGE_SIZE);
        assertThat(pageable.getAllValues().get(1).getPageNumber()).isZero();
    }

    @Test
    void list_isPublic_noAdminCheck() {
        when(postRepository.findVisible(any(), any(Instant.class), any(Pageable.class))).thenReturn(Page.empty());

        service.list(null, false, false, PageRequest.of(0, 20));

        verify(accessGuard, never()).requireAdmin();
    }

    // ---- delete ----

    @Test
    void delete_softDeletesThePost() {
        UUID id = UUID.randomUUID();
        Post existing = post(id, null, PostKind.SALE, "x");
        when(postRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(existing));

        service.delete(id);

        assertThat(existing.getDeletedAt()).isNotNull();
        verify(postRepository).save(existing);
        verify(postRepository, never()).delete(any());
        verify(postRepository, never()).deleteById(any());
        verify(accessGuard).requireAdmin();
    }

    @Test
    void delete_unknownOrAlreadyDeleted_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(postRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ResourceNotFoundException.class);
        verify(postRepository, never()).save(any());
    }

    @Test
    void delete_aNonAdmin_throwsForbidden_andDeletesNothing() {
        UUID id = UUID.randomUUID();
        doThrow(new ForbiddenException("Admin access required")).when(accessGuard).requireAdmin();

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ForbiddenException.class);
        verify(postRepository, never()).save(any());
        verify(postRepository, never()).findByIdAndDeletedAtIsNull(any());
    }

    // ---- schedule, expiry and hiding ----

    private static final Instant SOON = Instant.now().plusSeconds(3600);
    private static final Instant LATER = Instant.now().plusSeconds(7200);

    private PostCreateRequest withWindow(Instant publishAt, Instant expiresAt, Boolean hidden) {
        return new PostCreateRequest(null, PostKind.SALE, "Flash", null, null, publishAt, expiresAt, hidden);
    }

    @Test
    void create_withScheduleExpiryAndHidden_storesThemAndReportsTheStatus() {
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostResponse scheduled = service.create(withWindow(SOON, LATER, null));
        PostResponse hidden = service.create(withWindow(null, LATER, true));
        PostResponse live = service.create(withWindow(null, null, false));

        assertThat(scheduled.publishAt()).isEqualTo(SOON);
        assertThat(scheduled.expiresAt()).isEqualTo(LATER);
        assertThat(scheduled.hidden()).isFalse();
        assertThat(scheduled.status()).isEqualTo(PostStatus.SCHEDULED);
        assertThat(hidden.hidden()).isTrue();
        assertThat(hidden.status()).isEqualTo(PostStatus.HIDDEN);
        assertThat(live.status()).isEqualTo(PostStatus.LIVE);
    }

    @Test
    void create_aPublishTimeInThePast_isAllowed_andMeansLiveNow() {
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.create(withWindow(Instant.now().minusSeconds(60), null, null)).status()).isEqualTo(PostStatus.LIVE);
    }

    @Test
    void create_anExpiryInThePast_orNotAfterThePublishTime_throwsBadRequest_andSavesNothing() {
        assertThatThrownBy(() -> service.create(withWindow(null, Instant.now().minusSeconds(60), null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.create(withWindow(LATER, SOON, null))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.create(withWindow(SOON, SOON, null))).isInstanceOf(BadRequestException.class);
        verify(postRepository, never()).save(any());
    }

    @Test
    void list_everyoneAsksOnlyForLivePosts_evaluatedAtTheCurrentMoment() {
        when(postRepository.findVisible(any(), any(Instant.class), any(Pageable.class))).thenReturn(Page.empty());
        Instant before = Instant.now();

        service.list(null, false, false, PageRequest.of(0, 20));

        ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
        verify(postRepository).findVisible(any(), now.capture(), any(Pageable.class));
        assertThat(now.getValue()).isBetween(before, Instant.now());
        verify(accessGuard, never()).requireAdmin();
    }

    @Test
    void list_includeAll_asAdmin_usesTheAdminQueries_andReportsEachPostsStatus() {
        UUID eventId = UUID.randomUUID();
        Post hidden = post(UUID.randomUUID(), null, PostKind.SALE, "hidden");
        hidden.setHidden(true);
        Post expired = post(UUID.randomUUID(), null, PostKind.SALE, "expired");
        expired.setExpiresAt(Instant.now().minusSeconds(60));
        Post scheduled = post(UUID.randomUUID(), null, PostKind.SALE, "scheduled");
        scheduled.setPublishAt(SOON);
        when(postRepository.findAllAdmin(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(hidden, expired, scheduled)));
        when(postRepository.findAllAdminForEvent(eq(eventId), any(Pageable.class))).thenReturn(Page.empty());
        when(postRepository.findSiteWideAdmin(any(Pageable.class))).thenReturn(Page.empty());

        Page<PostResponse> all = service.list(null, false, true, PageRequest.of(0, 20));
        service.list(eventId, false, true, PageRequest.of(0, 20));
        service.list(null, true, true, PageRequest.of(0, 20));

        assertThat(all.getContent()).extracting(PostResponse::status)
                .containsExactly(PostStatus.HIDDEN, PostStatus.EXPIRED, PostStatus.SCHEDULED);
        verify(postRepository).findAllAdminForEvent(eq(eventId), any(Pageable.class));
        verify(postRepository).findSiteWideAdmin(any(Pageable.class));
        verify(postRepository, never()).findVisible(any(), any(), any());
    }

    @Test
    void list_includeAll_asANonAdmin_throwsForbidden_andReadsNothing() {
        doThrow(new ForbiddenException("Admin access required")).when(accessGuard).requireAdmin();

        assertThatThrownBy(() -> service.list(null, false, true, PageRequest.of(0, 20))).isInstanceOf(ForbiddenException.class);
        verify(postRepository, never()).findAllAdmin(any());
    }

    // ---- update ----

    private Post existing(UUID id) {
        Post existing = post(id, null, PostKind.SALE, "Old title");
        when(postRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(existing));
        return existing;
    }

    private void savesWhatItIsGiven() {
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private PostUpdateRequest update(String title, Instant publishAt, Instant expiresAt, Boolean hidden, boolean clearPublishAt,
                                     boolean clearExpiresAt) {
        return new PostUpdateRequest(null, title, null, null, publishAt, expiresAt, hidden, clearPublishAt, clearExpiresAt);
    }

    @Test
    void update_changesOnlyTheGivenFields() {
        UUID id = UUID.randomUUID();
        Post existing = existing(id);
        savesWhatItIsGiven();
        existing.setBody("keep me");

        PostResponse response = service.update(id, update("  New title  ", null, null, null, false, false));

        assertThat(response.title()).isEqualTo("New title");
        assertThat(existing.getBody()).isEqualTo("keep me");
        assertThat(existing.getKind()).isEqualTo(PostKind.SALE);
        assertThat(existing.isHidden()).isFalse();
        verify(accessGuard).requireAdmin();
    }

    @Test
    void update_hidesAndUnhides() {
        UUID id = UUID.randomUUID();
        Post existing = existing(id);
        savesWhatItIsGiven();

        assertThat(service.update(id, update(null, null, null, true, false, false)).status()).isEqualTo(PostStatus.HIDDEN);
        assertThat(existing.isHidden()).isTrue();
        assertThat(service.update(id, update(null, null, null, false, false, false)).status()).isEqualTo(PostStatus.LIVE);
        assertThat(existing.isHidden()).isFalse();
    }

    @Test
    void update_setsAndClearsTheDates() {
        UUID id = UUID.randomUUID();
        Post existing = existing(id);
        savesWhatItIsGiven();

        service.update(id, update(null, SOON, LATER, null, false, false));
        assertThat(existing.getPublishAt()).isEqualTo(SOON);
        assertThat(existing.getExpiresAt()).isEqualTo(LATER);

        service.update(id, update(null, null, null, null, true, true));
        assertThat(existing.getPublishAt()).isNull();
        assertThat(existing.getExpiresAt()).isNull();
    }

    @Test
    void update_anExpiryInThePast_isAllowed_soAnAdminCanEndAPostNow() {
        UUID id = UUID.randomUUID();
        existing(id);
        savesWhatItIsGiven();

        PostResponse response = service.update(id, update(null, null, Instant.now().minusSeconds(1), null, false, false));

        assertThat(response.status()).isEqualTo(PostStatus.EXPIRED);
    }

    @Test
    void update_theResultingWindowMustBeValid_andNothingIsSavedOtherwise() {
        UUID id = UUID.randomUUID();
        Post existing = existing(id);
        existing.setPublishAt(SOON);

        assertThatThrownBy(() -> service.update(id, update(null, null, SOON, null, false, false)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update(id, update(null, LATER, SOON, null, false, false)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update(id, update(null, SOON, null, null, true, false)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update(id, update(null, null, SOON, null, false, true)))
                .isInstanceOf(BadRequestException.class);
        verify(postRepository, never()).save(any());
    }

    @Test
    void update_aBlankTitle_throwsBadRequest() {
        UUID id = UUID.randomUUID();
        existing(id);

        assertThatThrownBy(() -> service.update(id, update("   ", null, null, null, false, false)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void update_unknownPost_throwsResourceNotFound_andANonAdminIsForbidden() {
        UUID id = UUID.randomUUID();
        when(postRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, update(null, null, null, true, false, false)))
                .isInstanceOf(ResourceNotFoundException.class);

        doThrow(new ForbiddenException("Admin access required")).when(accessGuard).requireAdmin();
        assertThatThrownBy(() -> service.update(id, update(null, null, null, true, false, false)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void update_ofAnEventPost_returnsTheEventTitle() {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Post existing = existing(id);
        savesWhatItIsGiven();
        existing.setEventId(eventId);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event(eventId, "Summer Fest")));

        assertThat(service.update(id, update("x", null, null, null, false, false)).eventTitle()).isEqualTo("Summer Fest");
    }

    // ---- picture ----

    private static final String IMG_A = "http://localhost:8081/api/v1/uploads/files/11111111-1111-1111-1111-111111111111.png";
    private static final String IMG_B = "http://localhost:8081/api/v1/uploads/files/22222222-2222-2222-2222-222222222222.jpg";

    private void ownUpload(String url) {
        when(uploadedFileUrls.ownFileName(url)).thenReturn(Optional.of("a-stored-name.png"));
    }

    private PostCreateRequest withImage(String imageUrl) {
        return new PostCreateRequest(null, PostKind.SALE, "Flash", null, imageUrl, null, null, null);
    }

    private PostUpdateRequest imageUpdate(String imageUrl) {
        return new PostUpdateRequest(null, null, null, imageUrl, null, null, null, null, null);
    }

    @Test
    void create_withAnOwnUploadedImage_storesTheTrimmedUrl() {
        ownUpload("  " + IMG_A + "  ");
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostResponse response = service.create(withImage("  " + IMG_A + "  "));

        assertThat(response.imageUrl()).isEqualTo(IMG_A);
    }

    @Test
    void create_withoutAnImage_orABlankOne_hasNoPicture_andNeverChecksTheUrl() {
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.create(withImage(null)).imageUrl()).isNull();
        assertThat(service.create(withImage("   ")).imageUrl()).isNull();
        assertThat(service.create(withImage("")).imageUrl()).isNull();
        verify(uploadedFileUrls, never()).ownFileName(any());
    }

    @Test
    void create_withALinkThatIsNotOneOfOurUploads_throwsBadRequest_andSavesNothing() {
        when(uploadedFileUrls.ownFileName("https://evil.example.com/pixel.png")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(withImage("https://evil.example.com/pixel.png")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("imageUrl");
        verify(postRepository, never()).save(any());
    }

    @Test
    void update_replacingThePicture_savesTheNewOne_andCleansUpTheOldFile() {
        UUID id = UUID.randomUUID();
        Post existing = existing(id);
        savesWhatItIsGiven();
        existing.setImageUrl(IMG_A);
        ownUpload(IMG_B);

        PostResponse response = service.update(id, imageUpdate(IMG_B));

        assertThat(response.imageUrl()).isEqualTo(IMG_B);
        verify(uploadedFileUrls).deleteIfUnreferenced(IMG_A);
    }

    @Test
    void update_addingAFirstPicture_cleansUpNothing() {
        UUID id = UUID.randomUUID();
        existing(id);
        savesWhatItIsGiven();
        ownUpload(IMG_A);

        assertThat(service.update(id, imageUpdate(IMG_A)).imageUrl()).isEqualTo(IMG_A);
        verify(uploadedFileUrls, never()).deleteIfUnreferenced(any());
    }

    @Test
    void update_anEmptyImageUrl_removesThePicture_andCleansUpTheFile() {
        UUID id = UUID.randomUUID();
        Post existing = existing(id);
        savesWhatItIsGiven();
        existing.setImageUrl(IMG_A);

        PostResponse response = service.update(id, imageUpdate(""));

        assertThat(response.imageUrl()).isNull();
        verify(uploadedFileUrls).deleteIfUnreferenced(IMG_A);
        verify(uploadedFileUrls, never()).ownFileName(any());
    }

    @Test
    void update_removingWhenThereIsNoPicture_isANoOp() {
        UUID id = UUID.randomUUID();
        existing(id);
        savesWhatItIsGiven();

        assertThat(service.update(id, imageUpdate("")).imageUrl()).isNull();
        verify(uploadedFileUrls, never()).deleteIfUnreferenced(any());
    }

    @Test
    void update_omittingTheImageUrl_leavesThePictureAlone() {
        UUID id = UUID.randomUUID();
        Post existing = existing(id);
        savesWhatItIsGiven();
        existing.setImageUrl(IMG_A);

        PostResponse response = service.update(id, update("New title", null, null, null, false, false));

        assertThat(response.imageUrl()).isEqualTo(IMG_A);
        verify(uploadedFileUrls, never()).deleteIfUnreferenced(any());
        verify(uploadedFileUrls, never()).ownFileName(any());
    }

    @Test
    void update_sendingTheCurrentPictureAgain_isANoOp_andNeverDeletesIt() {
        UUID id = UUID.randomUUID();
        Post existing = existing(id);
        savesWhatItIsGiven();
        existing.setImageUrl(IMG_A);
        ownUpload(IMG_A);

        assertThat(service.update(id, imageUpdate(IMG_A)).imageUrl()).isEqualTo(IMG_A);
        verify(uploadedFileUrls, never()).deleteIfUnreferenced(any());
    }

    @Test
    void update_withALinkThatIsNotOneOfOurUploads_throwsBadRequest_andKeepsTheOldPicture() {
        UUID id = UUID.randomUUID();
        Post existing = existing(id);
        existing.setImageUrl(IMG_A);
        when(uploadedFileUrls.ownFileName("https://evil.example.com/pixel.png")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, imageUpdate("https://evil.example.com/pixel.png")))
                .isInstanceOf(BadRequestException.class);
        verify(postRepository, never()).save(any());
        verify(uploadedFileUrls, never()).deleteIfUnreferenced(any());
    }

    @Test
    void delete_releasesThePicture_ifNothingElseUsesIt() {
        UUID id = UUID.randomUUID();
        Post existing = post(id, null, PostKind.SALE, "x");
        existing.setImageUrl(IMG_A);
        when(postRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(existing));

        service.delete(id);

        verify(uploadedFileUrls).deleteIfUnreferenced(IMG_A);
    }

    @Test
    void delete_aPostWithoutAPicture_cleansUpNothing() {
        UUID id = UUID.randomUUID();
        when(postRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(post(id, null, PostKind.SALE, "x")));

        service.delete(id);

        verify(uploadedFileUrls, never()).deleteIfUnreferenced(any());
    }
}
