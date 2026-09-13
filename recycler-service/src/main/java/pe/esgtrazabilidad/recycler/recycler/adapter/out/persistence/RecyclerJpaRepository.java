package pe.esgtrazabilidad.recycler.recycler.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface RecyclerJpaRepository
        extends JpaRepository<RecyclerEntity, UUID>, JpaSpecificationExecutor<RecyclerEntity> {

    Optional<RecyclerEntity> findByDni(String dni);
}
