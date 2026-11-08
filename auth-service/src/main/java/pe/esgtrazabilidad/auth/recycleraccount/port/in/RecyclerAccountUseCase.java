package pe.esgtrazabilidad.auth.recycleraccount.port.in;

import java.util.List;
import java.util.UUID;

import pe.esgtrazabilidad.auth.recycleraccount.domain.AccountSummary;
import pe.esgtrazabilidad.auth.recycleraccount.domain.IssuedCode;

/** What staff do with recycler accounts: give access, reissue the code, see who has it. */
public interface RecyclerAccountUseCase {

    /**
     * Creates the account (or, if it is still waiting to be activated, replaces its code). The
     * association and the status come from the local status copy, never from the caller.
     */
    IssuedCode createAccountAndCode(UUID recyclerId);

    /** For an account not yet activated, or whose devices are all revoked. */
    IssuedCode reissueCode(UUID accountId);

    List<AccountSummary> list(UUID associationId);
}
