package com.junaldadlawan.event_ticketing_api.auditlog.repository;

import com.junaldadlawan.event_ticketing_api.auditlog.entity.AuditLogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogEntryRepository extends JpaRepository<AuditLogEntry, UUID> {

    /** {@code GET /audit-log?actor_id=} - optional filter, see {@code AuditLogServiceImpl.list}. */
    Page<AuditLogEntry> findByActorId(UUID actorId, Pageable pageable);
}
