package pe.esgtrazabilidad.recycler.association.adapter.out.persistence;

import org.springframework.data.jpa.domain.Specification;

final class AssociationSpecifications {

    private AssociationSpecifications() {
    }

    static Specification<AssociationEntity> hasStatus(String status) {
        return (root, query, criteriaBuilder) ->
                status == null ? null : criteriaBuilder.equal(root.get("status"), status);
    }
}
