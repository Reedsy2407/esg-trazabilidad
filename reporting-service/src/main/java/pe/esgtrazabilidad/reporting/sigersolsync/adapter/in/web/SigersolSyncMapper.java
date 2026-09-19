package pe.esgtrazabilidad.reporting.sigersolsync.adapter.in.web;

import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.reporting.sigersolsync.domain.SigersolSync;
import pe.esgtrazabilidad.reporting.sigersolsync.port.in.RegisterSigersolSyncCommand;

@Component
class SigersolSyncMapper {

    RegisterSigersolSyncCommand toCommand(RegisterSigersolSyncRequest request) {
        return new RegisterSigersolSyncCommand(
                request.associationId(),
                request.periodStart(),
                request.periodEnd(),
                request.hierarchyCompliancePercent(),
                request.officialKilosDeclared(),
                request.sourceNote());
    }

    SigersolSyncResponse toResponse(SigersolSync sigersolSync) {
        return new SigersolSyncResponse(
                sigersolSync.getId(),
                sigersolSync.getAssociationId(),
                sigersolSync.getPeriodStart(),
                sigersolSync.getPeriodEnd(),
                sigersolSync.getHierarchyCompliancePercent(),
                sigersolSync.getOfficialKilosDeclared(),
                sigersolSync.getDeclaredAt(),
                sigersolSync.getSourceNote());
    }
}
