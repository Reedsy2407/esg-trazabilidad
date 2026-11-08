package pe.esgtrazabilidad.auth.recycleraccount.adapter.in.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.esgtrazabilidad.auth.recycleraccount.domain.IssuedCode;
import pe.esgtrazabilidad.auth.recycleraccount.port.in.RecyclerAccountUseCase;
import pe.esgtrazabilidad.auth.recycleraccount.port.in.RecyclerDeviceAdminUseCase;

/**
 * Staff only (STAFF or ADMIN, by SecurityConfig). The activation code is in the body of the call that
 * issues it and nowhere else; the response is not cacheable.
 */
@RestController
@RequestMapping("/auth/recycler-accounts")
class RecyclerAccountController {

    private final RecyclerAccountUseCase useCase;
    private final RecyclerDeviceAdminUseCase deviceAdmin;

    RecyclerAccountController(RecyclerAccountUseCase useCase, RecyclerDeviceAdminUseCase deviceAdmin) {
        this.useCase = useCase;
        this.deviceAdmin = deviceAdmin;
    }

    @PostMapping
    ResponseEntity<IssuedCodeResponse> create(@Valid @RequestBody CreateRecyclerAccountRequest request) {
        return issued(useCase.createAccountAndCode(request.recyclerId()));
    }

    @PostMapping("/{accountId}/activation-code")
    ResponseEntity<IssuedCodeResponse> reissue(@PathVariable UUID accountId) {
        return issued(useCase.reissueCode(accountId));
    }

    @GetMapping
    List<RecyclerAccountResponse> list(@RequestParam(required = false) UUID associationId) {
        return useCase.list(associationId).stream().map(RecyclerAccountResponse::from).toList();
    }

    @GetMapping("/{accountId}/devices")
    List<DeviceResponse> devices(@PathVariable UUID accountId) {
        return deviceAdmin.listDevices(accountId).stream().map(DeviceResponse::from).toList();
    }

    @DeleteMapping("/{accountId}/devices/{deviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeDevice(@PathVariable UUID accountId, @PathVariable UUID deviceId) {
        deviceAdmin.revokeDevice(accountId, deviceId);
    }

    private static ResponseEntity<IssuedCodeResponse> issued(IssuedCode code) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(IssuedCodeResponse.from(code));
    }
}
