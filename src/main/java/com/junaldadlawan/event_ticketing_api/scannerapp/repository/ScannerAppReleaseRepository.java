package com.junaldadlawan.event_ticketing_api.scannerapp.repository;

import com.junaldadlawan.event_ticketing_api.scannerapp.entity.ScannerAppRelease;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScannerAppReleaseRepository extends JpaRepository<ScannerAppRelease, UUID> {

    Optional<ScannerAppRelease> findByIdAndDeletedAtIsNull(UUID id);

    /** The newest version: the highest version code (not the latest upload, so an old build uploaded late is not offered as "latest"). */
    Optional<ScannerAppRelease> findFirstByDeletedAtIsNullOrderByVersionCodeDescCreatedAtDesc();

    List<ScannerAppRelease> findByDeletedAtIsNullOrderByCreatedAtDesc();

    boolean existsByVersionCodeAndDeletedAtIsNull(int versionCode);
}
