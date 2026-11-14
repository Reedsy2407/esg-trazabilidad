package pe.esgtrazabilidad.collection.wastetype;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class WasteTypeController {

    private final WasteTypeCatalog catalog;

    WasteTypeController(WasteTypeCatalog catalog) {
        this.catalog = catalog;
    }

    /** Staff and the field app read the same catalogue (the app needs it to fill the check-in form). */
    @GetMapping("/waste-types")
    List<WasteTypeResponse> list() {
        return catalog.listActive().stream()
                .map(type -> new WasteTypeResponse(type.getCode(), type.getName()))
                .toList();
    }

    record WasteTypeResponse(String code, String name) {
    }
}
