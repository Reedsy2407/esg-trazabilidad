package pe.esgtrazabilidad.recycler.recycler.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface RecyclerJpaRepository extends JpaRepository<RecyclerEntity, UUID> {

    Optional<RecyclerEntity> findByDni(String dni);
}
