package com.junaldadlawan.event_ticketing_api.upload.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** R2 backend against a mocked S3 client: right bucket and key, and a missing object maps to null/false. No network. */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class R2FileStorageTest {

    private static final String KEY = "users/11111111-1111-1111-1111-111111111111.png";

    @Mock
    private S3Client s3;

    private R2FileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new R2FileStorage(s3, "event-ticketing-media");
    }

    private static <B> B built(ArgumentCaptor<Consumer<B>> captor, B builder) {
        captor.getValue().accept(builder);
        return builder;
    }

    @Test
    void put_writesTheKeyToTheBucketWithItsContentType() throws Exception {
        storage.put(KEY, new byte[] {1, 2, 3}, "image/png");

        ArgumentCaptor<Consumer<PutObjectRequest.Builder>> request = ArgumentCaptor.forClass(Consumer.class);
        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(request.capture(), body.capture());
        PutObjectRequest sent = built(request, PutObjectRequest.builder()).build();
        assertThat(sent.bucket()).isEqualTo("event-ticketing-media");
        assertThat(sent.key()).isEqualTo(KEY);
        assertThat(sent.contentType()).isEqualTo("image/png");
        assertThat(body.getValue().contentStreamProvider().newStream().readAllBytes()).containsExactly(1, 2, 3);
    }

    @Test
    void get_returnsTheBytes_orNullWhenMissing() throws Exception {
        when(s3.getObjectAsBytes(any(Consumer.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[] {7}))
                .thenThrow(NoSuchKeyException.builder().build());

        assertThat(storage.get(KEY).getContentAsByteArray()).containsExactly(7);
        assertThat(storage.get(KEY)).isNull();

        ArgumentCaptor<Consumer<GetObjectRequest.Builder>> request = ArgumentCaptor.forClass(Consumer.class);
        verify(s3, org.mockito.Mockito.times(2)).getObjectAsBytes(request.capture());
        assertThat(built(request, GetObjectRequest.builder()).build().key()).isEqualTo(KEY);
    }

    @Test
    void exists_isFalseForNoSuchKeyOrABare404_andOtherErrorsPropagate() {
        when(s3.headObject(any(Consumer.class)))
                .thenReturn(null)
                .thenThrow(NoSuchKeyException.builder().build())
                .thenThrow(S3Exception.builder().statusCode(404).build())
                .thenThrow(S3Exception.builder().statusCode(403).build());

        assertThat(storage.exists(KEY)).isTrue();
        assertThat(storage.exists(KEY)).isFalse();
        assertThat(storage.exists(KEY)).isFalse();
        assertThatThrownBy(() -> storage.exists(KEY)).isInstanceOf(S3Exception.class);

        ArgumentCaptor<Consumer<HeadObjectRequest.Builder>> request = ArgumentCaptor.forClass(Consumer.class);
        verify(s3, org.mockito.Mockito.atLeastOnce()).headObject(request.capture());
        assertThat(built(request, HeadObjectRequest.builder()).build().bucket()).isEqualTo("event-ticketing-media");
    }

    @Test
    void delete_removesTheKey() {
        storage.delete(KEY);

        ArgumentCaptor<Consumer<DeleteObjectRequest.Builder>> request = ArgumentCaptor.forClass(Consumer.class);
        verify(s3).deleteObject(request.capture());
        DeleteObjectRequest sent = built(request, DeleteObjectRequest.builder()).build();
        assertThat(sent.bucket()).isEqualTo("event-ticketing-media");
        assertThat(sent.key()).isEqualTo(KEY);
    }
}
