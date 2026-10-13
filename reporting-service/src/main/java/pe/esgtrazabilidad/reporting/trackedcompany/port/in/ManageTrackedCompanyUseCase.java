package pe.esgtrazabilidad.reporting.trackedcompany.port.in;

import java.util.UUID;

import pe.esgtrazabilidad.kernel.security.CurrentActor;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompany;

/** Edit, deactivate/activate and delete a tracked company (M4). The actor always comes from the token. */
public interface ManageTrackedCompanyUseCase {

    /** RPT-017 if the RUC or association changes and the company has certificates. */
    TrackedCompany edit(EditTrackedCompanyCommand command);

    /** RPT-018 when it is already inactive. */
    TrackedCompany deactivate(UUID id, String reason, CurrentActor actor);

    /** RPT-018 when it is already active. */
    TrackedCompany activate(UUID id, String reason, CurrentActor actor);

    /** Logical; RPT-017 if the company has any certificate. */
    void delete(UUID id, String reason, CurrentActor actor);
}
