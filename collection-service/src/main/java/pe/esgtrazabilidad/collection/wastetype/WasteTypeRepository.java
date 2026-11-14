package pe.esgtrazabilidad.collection.wastetype;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface WasteTypeRepository extends JpaRepository<WasteType, String> {

    List<WasteType> findByActiveTrueOrderByNameAsc();

    boolean existsByCodeAndActiveTrue(String code);
}
