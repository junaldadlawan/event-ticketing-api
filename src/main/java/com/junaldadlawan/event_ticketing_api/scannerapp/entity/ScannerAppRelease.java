package com.junaldadlawan.event_ticketing_api.scannerapp.entity;

import com.junaldadlawan.event_ticketing_api.common.entity.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * One uploaded version of the scanner app (an Android APK). The file is in the file storage under {@code storageKey}; this row is the history
 * ({@code createdAt}/{@code createdBy} say when and by whom). Soft-deleted through {@link Auditable#markDeleted()}: the row stays, the file goes.
 */
@Entity
@Table(name = "scanner_app_releases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScannerAppRelease extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "version_name", nullable = false, length = 50)
    private String versionName;

    /** The Android versionCode: the app compares it to decide whether it is outdated. Unique among live uploads. */
    @Column(name = "version_code", nullable = false)
    private int versionCode;

    @Column(name = "release_notes", length = 2000)
    private String releaseNotes;

    @Column(name = "storage_key", nullable = false, length = 200)
    private String storageKey;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** SHA-256 of the file (hex), so the device can check what it downloaded. */
    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;
}
