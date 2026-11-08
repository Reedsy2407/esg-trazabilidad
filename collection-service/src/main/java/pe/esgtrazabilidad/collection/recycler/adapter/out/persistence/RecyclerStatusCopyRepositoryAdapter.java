package pe.esgtrazabilidad.collection.recycler.adapter.out.persistence;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pe.esgtrazabilidad.collection.recycler.domain.RecyclerStatus;
import pe.esgtrazabilidad.collection.recycler.port.out.RecyclerStatusCopyRepository;

@Component
class RecyclerStatusCopyRepositoryAdapter implements RecyclerStatusCopyRepository {

    private final RecyclerStatusCopyJpaRepository jpaRepository;

    RecyclerStatusCopyRepositoryAdapter(RecyclerStatusCopyJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public boolean applyIfNewer(UUID recyclerId, UUID associationId, RecyclerStatus status, Instant occurredAt) {
        // Postgres keeps microseconds: compare at that precision, or a redelivery of the very
        // same event (nanoseconds on the wire, truncated in the table) would look newer.
        Instant at = occurredAt.truncatedTo(ChronoUnit.MICROS);
        return jpaRepository.upsertIfNewer(recyclerId, associationId, status.name(), at) > 0;
    }

    @Override
    public boolean isActive(UUID recyclerId) {
        return jpaRepository.isActive(recyclerId);
    }
}
