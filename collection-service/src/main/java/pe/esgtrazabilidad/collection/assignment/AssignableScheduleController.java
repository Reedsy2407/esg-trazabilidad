package pe.esgtrazabilidad.collection.assignment;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.esgtrazabilidad.collection.exception.CollectionErrors;
import pe.esgtrazabilidad.kernel.error.ApplicationException;
import pe.esgtrazabilidad.kernel.ubigeo.UbigeoCatalog;

/** What the console can assign one by one (A.5c). Staff only: the chain's default rule. */
@RestController
class AssignableScheduleController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AssignableScheduleService service;

    AssignableScheduleController(AssignableScheduleService service) {
        this.service = service;
    }

    @GetMapping("/schedules/assignable")
    AssignableScheduleService.AssignablePage list(
            @RequestParam(required = false) String districtUbigeo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        // Same rule as the bulk assignment: a six-digit ubigeo of Lima or Callao (COL-010 otherwise).
        if (districtUbigeo == null || !districtUbigeo.matches("\\d{6}") || !UbigeoCatalog.contains(districtUbigeo)) {
            throw new ApplicationException(CollectionErrors.INVALID_DISTRICT);
        }
        return service.list(districtUbigeo, Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }
}
