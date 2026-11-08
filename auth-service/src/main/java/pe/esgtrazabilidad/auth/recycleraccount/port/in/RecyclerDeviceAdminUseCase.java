package pe.esgtrazabilidad.auth.recycleraccount.port.in;

import java.util.List;
import java.util.UUID;

import pe.esgtrazabilidad.auth.recycleraccount.domain.DeviceSummary;

/** What staff do with an account's phones: see them and cut one off (a lost phone). */
public interface RecyclerDeviceAdminUseCase {

    List<DeviceSummary> listDevices(UUID accountId);

    /** The device's refresh stops working at once; its access token still lives at most 15 minutes. Idempotent. */
    void revokeDevice(UUID accountId, UUID deviceId);
}
