package pe.esgtrazabilidad.auth.recycleraccount.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import pe.esgtrazabilidad.auth.recycleraccount.domain.DeviceSummary;

/** Exactly what staff need; never a token, a hash or the install id. */
record DeviceResponse(UUID deviceId, String label, Instant createdAt, Instant lastSeenAt, Instant revokedAt) {

    static DeviceResponse from(DeviceSummary device) {
        return new DeviceResponse(
                device.deviceId(), device.label(), device.createdAt(), device.lastSeenAt(), device.revokedAt());
    }
}
