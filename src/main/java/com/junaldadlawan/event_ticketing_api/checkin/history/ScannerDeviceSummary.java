package com.junaldadlawan.event_ticketing_api.checkin.history;

import com.junaldadlawan.event_ticketing_api.checkin.enums.ScannerDeviceStatus;

import java.io.Serializable;
import java.util.UUID;

/** A scanner device as the history filter shows it: never its credential. */
public record ScannerDeviceSummary(UUID id, String name, String location, ScannerDeviceStatus status) implements Serializable {
}
