package pe.esgtrazabilidad.collection.evidence;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import pe.esgtrazabilidad.kernel.security.RecyclerClaims;

/** Evidence routes of the recycler's device. The association comes only from the token. */
@RestController
class EvidenceController {

    private final EvidenceService service;

    EvidenceController(EvidenceService service) {
        this.service = service;
    }

    record PresignRequest(
            @NotNull UUID checkinClientId,
            @NotNull @Pattern(regexp = "image/jpeg", message = "Solo se acepta image/jpeg") String contentType,
            @NotNull @Positive @MaxEvidenceSize Long sizeBytes,
            @NotNull @Pattern(regexp = "[0-9a-fA-F]{64}", message = "El sha256 debe tener 64 caracteres hexadecimales") String sha256) {
    }

    @PostMapping("/evidence/presign")
    EvidenceService.Presigned presign(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PresignRequest request) {
        return service.presign(RecyclerClaims.from(jwt), request.checkinClientId(), request.sizeBytes(), request.sha256());
    }
}
