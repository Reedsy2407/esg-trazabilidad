package pe.esgtrazabilidad.collection.retention;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** On-demand retention run. ADMIN only (SecurityConfig): STAFF and RECYCLER get 403 AUTH-000. */
@RestController
class RetentionController {

    private final RetentionRunner runner;

    RetentionController(RetentionRunner runner) {
        this.runner = runner;
    }

    @PostMapping("/retention/run")
    RetentionRunner.RetentionRunResult run() {
        return runner.run();
    }
}
