package pe.esgtrazabilidad.collection.company.adapter.out.persistence;

import org.springframework.data.jpa.domain.Specification;

final class CompanySpecifications {

    private CompanySpecifications() {
    }

    static Specification<CompanyEntity> hasStatus(String status) {
        return (root, query, criteriaBuilder) ->
                status == null ? null : criteriaBuilder.equal(root.get("status"), status);
    }
}
