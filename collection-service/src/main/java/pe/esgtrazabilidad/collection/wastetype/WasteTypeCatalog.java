package pe.esgtrazabilidad.collection.wastetype;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.esgtrazabilidad.collection.exception.CollectionErrors;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

/** The active waste types, and the single check that a code belongs to the catalogue (COL-014). */
@Service
public class WasteTypeCatalog {

    private final WasteTypeRepository repository;

    WasteTypeCatalog(WasteTypeRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<WasteType> listActive() {
        return repository.findByActiveTrueOrderByNameAsc();
    }

    /** @throws ApplicationException COL-014 (400) when the code is null, unknown or retired */
    @Transactional(readOnly = true)
    public void requireActive(String code) {
        if (code == null || !repository.existsByCodeAndActiveTrue(code)) {
            throw new ApplicationException(CollectionErrors.INVALID_WASTE_TYPE);
        }
    }
}
