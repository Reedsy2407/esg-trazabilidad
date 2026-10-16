package pe.esgtrazabilidad.auth.staffuser.port.in;

import java.util.UUID;

public interface ChangeOwnPasswordUseCase {

    /** AUTH-008 if {@code currentRawPassword} is wrong, AUTH-007 if the new one is not acceptable. */
    void changeOwnPassword(UUID staffUserId, String currentRawPassword, String newRawPassword);
}
