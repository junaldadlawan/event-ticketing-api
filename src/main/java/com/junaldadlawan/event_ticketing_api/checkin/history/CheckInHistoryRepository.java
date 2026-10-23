package com.junaldadlawan.event_ticketing_api.checkin.history;

import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInResult;
import com.junaldadlawan.event_ticketing_api.checkin.enums.CheckInSourceType;
import com.junaldadlawan.event_ticketing_api.checkin.enums.ScannerDeviceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * An event's scan attempts, read with plain SQL. A record has no event column: it belongs to the event of the scanner device that scanned
 * (SCANNER_DEVICE) or of the fallback scan it was reconciled from (RECONCILED_FALLBACK). Nothing about the ticket's buyer or owner is selected.
 */
@Repository
@RequiredArgsConstructor
public class CheckInHistoryRepository {

    public record Filter(CheckInResult result, CheckInSourceType sourceType, UUID scannerDeviceId, String ticketNumber) {
    }

    private static final String FROM = """
            from check_in_records r
            join tickets t on t.id = r.ticket_id
            left join ticket_types tt on tt.id = t.ticket_type_id
            left join scanner_devices d on r.source_type = 'SCANNER_DEVICE' and d.id = r.source_id
            left join fallback_scan_records f on r.source_type = 'RECONCILED_FALLBACK' and f.id = r.source_id
            where (d.event_id = :eventId or f.event_id = :eventId)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public List<CheckInHistoryItem> page(UUID eventId, Filter filter, int limit, long offset) {
        MapSqlParameterSource params = params(eventId, filter).addValue("limit", limit).addValue("offset", offset);
        String sql = """
                select r.id, r.ticket_id, t.ticket_number, t.ticket_type_id, tt.name as ticket_type_name, r.source_type, r.source_id,
                       d.device_label as scanner_name, r.scanner_location, r.scanned_at, r.result
                """ + FROM + where(filter) + " order by r.scanned_at desc, r.id desc limit :limit offset :offset";
        return jdbc.query(sql, params, (rs, i) -> new CheckInHistoryItem(
                rs.getObject("id", UUID.class), rs.getObject("ticket_id", UUID.class), rs.getString("ticket_number"),
                rs.getObject("ticket_type_id", UUID.class), rs.getString("ticket_type_name"),
                CheckInSourceType.valueOf(rs.getString("source_type")), rs.getObject("source_id", UUID.class),
                rs.getString("scanner_name"), rs.getString("scanner_location"),
                rs.getObject("scanned_at", OffsetDateTime.class).toInstant(), CheckInResult.valueOf(rs.getString("result"))));
    }

    public long count(UUID eventId, Filter filter) {
        return jdbc.queryForObject("select count(*) " + FROM + where(filter), params(eventId, filter), Long.class);
    }

    public CheckInSummaryResponse summary(UUID eventId, Filter filter) {
        long admittable = jdbc.queryForObject("select count(*) from tickets where event_id = :eventId and status not in ('REFUNDED', 'CANCELLED')",
                new MapSqlParameterSource("eventId", eventId), Long.class);
        String sql = """
                select count(*) as total,
                       count(*) filter (where r.result = 'VALID') as valid,
                       count(*) filter (where r.result = 'DUPLICATE') as duplicate,
                       count(*) filter (where r.result = 'INVALID') as invalid,
                       count(*) filter (where r.result = 'WRONG_EVENT') as wrong_event,
                       count(*) filter (where r.source_type = 'SCANNER_DEVICE') as online,
                       count(*) filter (where r.source_type = 'RECONCILED_FALLBACK') as offline,
                       count(distinct r.ticket_id) filter (where r.result = 'VALID') as unique_admitted
                """ + FROM + where(filter);
        return jdbc.queryForObject(sql, params(eventId, filter), (rs, i) -> new CheckInSummaryResponse(rs.getLong("total"), rs.getLong("valid"),
                rs.getLong("duplicate"), rs.getLong("invalid"), rs.getLong("wrong_event"), rs.getLong("online"), rs.getLong("offline"),
                rs.getLong("unique_admitted"), admittable));
    }

    public List<ScannerDeviceSummary> devices(UUID eventId) {
        return jdbc.query("select id, device_label, location, status from scanner_devices where event_id = :eventId order by created_at",
                new MapSqlParameterSource("eventId", eventId), (rs, i) -> new ScannerDeviceSummary(rs.getObject("id", UUID.class),
                        rs.getString("device_label"), rs.getString("location"), ScannerDeviceStatus.valueOf(rs.getString("status"))));
    }

    private static String where(Filter f) {
        StringBuilder sql = new StringBuilder();
        if (f.result() != null) {
            sql.append(" and r.result = :result");
        }
        if (f.sourceType() != null) {
            sql.append(" and r.source_type = :sourceType");
        }
        if (f.scannerDeviceId() != null) {
            sql.append(" and r.source_type = 'SCANNER_DEVICE' and r.source_id = :deviceId");
        }
        if (f.ticketNumber() != null) {
            sql.append(" and position(lower(:ticketNumber) in lower(t.ticket_number)) > 0");
        }
        return sql.toString();
    }

    private static MapSqlParameterSource params(UUID eventId, Filter f) {
        MapSqlParameterSource p = new MapSqlParameterSource("eventId", eventId);
        if (f.result() != null) {
            p.addValue("result", f.result().name());
        }
        if (f.sourceType() != null) {
            p.addValue("sourceType", f.sourceType().name());
        }
        if (f.scannerDeviceId() != null) {
            p.addValue("deviceId", f.scannerDeviceId());
        }
        if (f.ticketNumber() != null) {
            p.addValue("ticketNumber", f.ticketNumber());
        }
        return p;
    }
}
