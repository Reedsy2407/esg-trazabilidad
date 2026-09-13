package pe.esgtrazabilidad.recycler.recycler.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

final class RecyclerSpecifications {

    private RecyclerSpecifications() {
    }

    static Specification<RecyclerEntity> hasAssociationId(UUID associationId) {
        return (root, query, criteriaBuilder) ->
                associationId == null ? null : criteriaBuilder.equal(root.get("associationId"), associationId);
    }

    static Specification<RecyclerEntity> hasStatus(String status) {
        return (root, query, criteriaBuilder) ->
                status == null ? null : criteriaBuilder.equal(root.get("status"), status);
    }
}
