package pe.esgtrazabilidad.auth.staffuser.port.in;

import java.util.UUID;

import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;

public interface GetCurrentStaffUserUseCase {

    StaffUser getCurrent(UUID staffUserId);
}
