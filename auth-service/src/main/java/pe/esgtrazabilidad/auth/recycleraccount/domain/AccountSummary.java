package pe.esgtrazabilidad.auth.recycleraccount.domain;

import java.time.Instant;
import java.util.UUID;

/** One row of the staff list. {@code status} already reflects the live recycler status. */
public record AccountSummary(
        UUID accountId, UUID recyclerId, AccountStatus status, Instant activatedAt, int devices) {
}
