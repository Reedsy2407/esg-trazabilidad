package pe.esgtrazabilidad.recycler.recycler.adapter.in.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.esgtrazabilidad.recycler.recycler.port.in.RepublishRecyclerStatusUseCase;

/** Only ADMIN reaches this (SecurityConfig). */
@RestController
@RequestMapping("/recyclers/status-events")
class RecyclerStatusEventsController {

    record RepublishResponse(int published) {
    }

    private final RepublishRecyclerStatusUseCase republishUseCase;

    RecyclerStatusEventsController(RepublishRecyclerStatusUseCase republishUseCase) {
        this.republishUseCase = republishUseCase;
    }

    @PostMapping("/republish")
    RepublishResponse republish() {
        return new RepublishResponse(republishUseCase.republishAll());
    }
}
