package com.junaldadlawan.event_ticketing_api.upload.storage;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** Which backend {@code app.storage.type} picks. Building the R2 client makes no network call. */
class FileStorageSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(LocalFileStorage.class, R2FileStorage.class)
            .withPropertyValues("app.upload.dir=target/test-uploads");

    @Test
    void noTypeSet_usesLocalDisk() {
        runner.run(context -> assertThat(context).hasSingleBean(FileStorage.class).hasSingleBean(LocalFileStorage.class));
    }

    @Test
    void local_usesLocalDisk_andNeedsNoR2Settings() {
        runner.withPropertyValues("app.storage.type=local")
                .run(context -> assertThat(context).hasSingleBean(FileStorage.class).hasSingleBean(LocalFileStorage.class));
    }

    @Test
    void r2_usesR2() {
        runner.withPropertyValues("app.storage.type=r2",
                        "app.storage.r2.account-id=test-account",
                        "app.storage.r2.access-key-id=test-key",
                        "app.storage.r2.secret-access-key=test-secret",
                        "app.storage.r2.bucket=event-ticketing-media")
                .run(context -> assertThat(context).hasSingleBean(FileStorage.class).hasSingleBean(R2FileStorage.class));
    }

    @Test
    void r2_withoutItsSettings_failsAtStartup() {
        runner.withPropertyValues("app.storage.type=r2").run(context -> assertThat(context).hasFailed());
    }
}
