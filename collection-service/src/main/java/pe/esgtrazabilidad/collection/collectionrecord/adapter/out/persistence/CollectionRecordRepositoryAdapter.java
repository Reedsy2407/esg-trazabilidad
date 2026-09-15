package pe.esgtrazabilidad.collection.collectionrecord.adapter.out.persistence;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.collection.collectionrecord.domain.CollectionRecord;
import pe.esgtrazabilidad.collection.collectionrecord.port.out.CollectionRecordRepository;

@Component
class CollectionRecordRepositoryAdapter implements CollectionRecordRepository {

    private final CollectionRecordJpaRepository jpaRepository;

    CollectionRecordRepositoryAdapter(CollectionRecordJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public CollectionRecord save(CollectionRecord record) {
        return toDomain(jpaRepository.save(toEntity(record)));
    }

    @Override
    public Optional<CollectionRecord> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Page<CollectionRecord> findAll(UUID neighborId, LocalDate from, LocalDate to, Pageable pageable) {
        return jpaRepository
                .findAll(
                        CollectionRecordSpecifications.hasNeighborId(neighborId)
                                .and(CollectionRecordSpecifications.collectionDateFrom(from))
                                .and(CollectionRecordSpecifications.collectionDateTo(to)),
                        pageable)
                .map(this::toDomain);
    }

    private CollectionRecordEntity toEntity(CollectionRecord record) {
        return new CollectionRecordEntity(
                record.getId(),
                record.getNeighborId(),
                record.getScheduleId(),
                record.getAssociationId(),
                record.getCollectionDate(),
                record.getWeightKg());
    }

    private CollectionRecord toDomain(CollectionRecordEntity entity) {
        return CollectionRecord.reconstruct(
                entity.getId(),
                entity.getNeighborId(),
                entity.getScheduleId(),
                entity.getAssociationId(),
                entity.getCollectionDate(),
                entity.getWeightKg());
    }
}
