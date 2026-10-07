package com.junaldadlawan.event_ticketing_api.upload.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadedFileUrlsTest {

    private static final String NAME = "11111111-1111-1111-1111-111111111111.png";
    private static final String URL = "https://api.example.com/api/v1/uploads/files/" + NAME;

    @Mock
    private ImageStorageService storage;
    @Mock
    private EntityManager entityManager;
    @Mock
    private TypedQuery<Long> query;

    private UploadedFileUrls urls;

    @BeforeEach
    void setUp() {
        urls = new UploadedFileUrls(storage);
        ReflectionTestUtils.setField(urls, "entityManager", entityManager);
        ReflectionTestUtils.setField(urls, "publicBaseUrl", "https://api.example.com");
        lenient().when(storage.exists(NAME)).thenReturn(true);
    }

    private void referencedBy(int queryNumber, long count) {
        // the four reference checks run in order and stop at the first hit
        Long[] counts = new Long[5];
        java.util.Arrays.fill(counts, 0L);
        counts[queryNumber] = count;
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(query);
        when(query.setParameter(eq("url"), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(counts[0], java.util.Arrays.copyOfRange(counts, 1, 5));
    }

    @Test
    void ownFileName_acceptsOurOwnExistingUpload() {
        assertThat(urls.ownFileName(URL)).contains(NAME);
        assertThat(urls.ownFileName("  " + URL + "  ")).contains(NAME);
        assertThat(urls.ownFileName("HTTPS://API.EXAMPLE.COM/api/v1/uploads/files/" + NAME)).contains(NAME);
    }

    @Test
    void ownFileName_rejectsEverythingElse() {
        for (String bad : new String[] {
                null, "", "   ",
                "https://evil.example.com/api/v1/uploads/files/" + NAME,
                "http://api.example.com/api/v1/uploads/files/" + NAME,
                "https://api.example.com:8443/api/v1/uploads/files/" + NAME,
                "https://user@api.example.com/api/v1/uploads/files/" + NAME,
                URL + "?a=1", URL + "#x",
                "https://api.example.com/api/v1/uploads/files/../" + NAME,
                "https://api.example.com/elsewhere/" + NAME,
                "https://api.example.com/api/v1/uploads/files/" + UUID.randomUUID() + ".svg",
                "https://api.example.com/api/v1/uploads/files/not-ours.png",
                "/api/v1/uploads/files/" + NAME,
                "ht tp://bad"}) {
            assertThat(urls.ownFileName(bad)).as(String.valueOf(bad)).isEmpty();
        }
    }

    @Test
    void ownFileName_rejectsAFileThatDoesNotExist() {
        when(storage.exists(NAME)).thenReturn(false);

        assertThat(urls.ownFileName(URL)).isEqualTo(Optional.empty());
    }

    @Test
    void deleteIfUnreferenced_removesTheFileWhenNothingUsesIt() {
        referencedBy(0, 0);

        urls.deleteIfUnreferenced(URL);

        verify(storage).delete(NAME);
    }

    @Test
    void deleteIfUnreferenced_keepsTheFileWhenAnyOwnerStillUsesIt() {
        for (int owner = 0; owner < 5; owner++) {
            org.mockito.Mockito.reset(entityManager, query, storage);
            lenient().when(storage.exists(NAME)).thenReturn(true);
            referencedBy(owner, 1);

            urls.deleteIfUnreferenced(URL);

            verify(storage, never()).delete(any());
        }
    }

    @Test
    void deleteIfUnreferenced_ignoresForeignUrls_andNeverThrows() {
        urls.deleteIfUnreferenced("https://evil.example.com/x.png");
        urls.deleteIfUnreferenced(null);
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenThrow(new IllegalStateException("db down"));

        urls.deleteIfUnreferenced(URL);

        verify(storage, never()).delete(any());
    }
}
