package pe.esgtrazabilidad.auth.staffuser.port.in;

import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;

public interface CreateStaffUserUseCase {

    StaffUser createStaffUser(CreateStaffUserCommand command);
}
