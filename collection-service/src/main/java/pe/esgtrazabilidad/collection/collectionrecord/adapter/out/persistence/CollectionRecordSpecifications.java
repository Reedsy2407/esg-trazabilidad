package pe.esgtrazabilidad.collection.collectionrecord.adapter.out.persistence;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

final class CollectionRecordSpecifications {

    private CollectionRecordSpecifications() {
    }

    static Specification<CollectionRecordEntity> hasNeighborId(UUID neighborId) {
        return (root, query, criteriaBuilder) ->
                neighborId == null ? null : criteriaBuilder.equal(root.get("neighborId"), neighborId);
    }

    static Specification<CollectionRecordEntity> collectionDateFrom(LocalDate from) {
        return (root, query, criteriaBuilder) ->
                from == null ? null : criteriaBuilder.greaterThanOrEqualTo(root.get("collectionDate"), from);
    }

    static Specification<CollectionRecordEntity> collectionDateTo(LocalDate to) {
        return (root, query, criteriaBuilder) ->
                to == null ? null : criteriaBuilder.lessThanOrEqualTo(root.get("collectionDate"), to);
    }
}
