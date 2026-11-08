package pe.esgtrazabilidad.auth.recycleraccount.domain;

import java.time.Instant;
import java.util.UUID;

/** A phone as staff see it: when it was activated, when it last refreshed, and whether it was revoked. */
public record DeviceSummary(UUID deviceId, String label, Instant createdAt, Instant lastSeenAt, Instant revokedAt) {
}
