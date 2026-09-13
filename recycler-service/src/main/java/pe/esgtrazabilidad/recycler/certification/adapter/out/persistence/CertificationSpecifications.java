package pe.esgtrazabilidad.recycler.certification.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

final class CertificationSpecifications {

    private CertificationSpecifications() {
    }

    static Specification<CertificationEntity> hasAssociationId(UUID associationId) {
        return (root, query, criteriaBuilder) ->
                associationId == null ? null : criteriaBuilder.equal(root.get("associationId"), associationId);
    }
}
