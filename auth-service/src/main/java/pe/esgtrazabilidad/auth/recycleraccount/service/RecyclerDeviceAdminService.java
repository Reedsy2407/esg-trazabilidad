package pe.esgtrazabilidad.auth.recycleraccount.service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.esgtrazabilidad.auth.recycleraccount.domain.DeviceSummary;
import pe.esgtrazabilidad.auth.recycleraccount.exception.RecyclerAccountErrors;
import pe.esgtrazabilidad.auth.recycleraccount.port.in.RecyclerDeviceAdminUseCase;
import pe.esgtrazabilidad.auth.recycleraccount.port.out.RecyclerAccountStore;
import pe.esgtrazabilidad.auth.recycleraccount.port.out.RecyclerDeviceStore;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

@Service
class RecyclerDeviceAdminService implements RecyclerDeviceAdminUseCase {

    private static final Logger log = LoggerFactory.getLogger(RecyclerDeviceAdminService.class);

    private final RecyclerAccountStore accounts;
    private final RecyclerDeviceStore devices;
    private final Clock clock;

    RecyclerDeviceAdminService(RecyclerAccountStore accounts, RecyclerDeviceStore devices, Clock clock) {
        this.accounts = accounts;
        this.devices = devices;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeviceSummary> listDevices(UUID accountId) {
        requireAccount(accountId);
        return devices.listByAccount(accountId);
    }

    @Override
    @Transactional
    public void revokeDevice(UUID accountId, UUID deviceId) {
        requireAccount(accountId);
        // A device that is not this account's is "not found", the same as one that does not exist.
        if (!devices.revokeOwned(accountId, deviceId, clock.instant())) {
            throw new ApplicationException(RecyclerAccountErrors.DEVICE_NOT_FOUND);
        }
        log.info("event=recycler_device_revoked reason=staff deviceId={}", deviceId);
    }

    private void requireAccount(UUID accountId) {
        accounts.findById(accountId).orElseThrow(() -> new ApplicationException(RecyclerAccountErrors.ACCOUNT_NOT_FOUND));
    }
}
