package pe.esgtrazabilidad.collection.neighbor.adapter.out.persistence;

import org.springframework.data.jpa.domain.Specification;

final class NeighborSpecifications {

    private NeighborSpecifications() {
    }

    static Specification<NeighborEntity> hasStatus(String status) {
        return (root, query, criteriaBuilder) ->
                status == null ? null : criteriaBuilder.equal(root.get("status"), status);
    }

    static Specification<NeighborEntity> hasDistrict(String district) {
        return (root, query, criteriaBuilder) ->
                district == null ? null : criteriaBuilder.equal(root.get("district"), district);
    }
}
