package com.junaldadlawan.event_ticketing_api.layout.repository;

import com.junaldadlawan.event_ticketing_api.layout.entity.PresetLayout;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PresetLayoutRepository extends JpaRepository<PresetLayout, UUID> {

    List<PresetLayout> findByDeletedAtIsNullOrderByNameAsc();

    List<PresetLayout> findByActiveTrueAndDeletedAtIsNullOrderByNameAsc();

    Optional<PresetLayout> findByIdAndDeletedAtIsNull(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PresetLayout> findForUpdateByIdAndDeletedAtIsNull(UUID id);

    Optional<PresetLayout> findByNameIgnoreCaseAndDeletedAtIsNull(String name);
}
