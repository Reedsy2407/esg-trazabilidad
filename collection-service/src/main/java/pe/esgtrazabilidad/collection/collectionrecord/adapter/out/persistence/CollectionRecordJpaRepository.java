package pe.esgtrazabilidad.collection.collectionrecord.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface CollectionRecordJpaRepository
        extends JpaRepository<CollectionRecordEntity, UUID>, JpaSpecificationExecutor<CollectionRecordEntity> {

    Page<CollectionRecordEntity> findByNeighborId(UUID neighborId, Pageable pageable);
}
