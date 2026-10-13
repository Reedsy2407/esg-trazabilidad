package pe.esgtrazabilidad.reporting.trackedcompany.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import pe.esgtrazabilidad.kernel.error.ApplicationException;
import pe.esgtrazabilidad.kernel.ruc.Ruc;
import pe.esgtrazabilidad.reporting.exception.ReportingErrors;
import pe.esgtrazabilidad.reporting.sunat.RucLookupResult;
import pe.esgtrazabilidad.reporting.sunat.service.CachedRucLookup;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.CompanyContact;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompany;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompanyVerification;
import pe.esgtrazabilidad.reporting.trackedcompany.port.in.RegisterTrackedCompanyCommand;
import pe.esgtrazabilidad.reporting.trackedcompany.port.in.RegisterUnverifiedTrackedCompanyCommand;
import pe.esgtrazabilidad.reporting.trackedcompany.port.out.TrackedCompanyRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackedCompanyServiceTest {

    // Real RUC of a public entity with a valid check digit; no call is made to anything.
    private static final String RUC = "20131312955";
    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");

    @Mock
    private TrackedCompanyRepository repository;

    @Mock
    private CachedRucLookup rucLookup;

    private TrackedCompanyService service;

    @BeforeEach
    void setUp() {
        service = new TrackedCompanyService(repository, rucLookup, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private RegisterTrackedCompanyCommand command(String ruc) {
        return new RegisterTrackedCompanyCommand(
                ruc, UUID.randomUUID(), new CompanyContact("Rosa", "rosa@example.org", "999111222"));
    }

    @Test
    void registersAVerifiedCompanyWithTheNameFromSunat() {
        when(repository.findByRuc(RUC)).thenReturn(Optional.empty());
        when(rucLookup.lookup(Ruc.of(RUC)))
                .thenReturn(new RucLookupResult.Found("SUPERINTENDENCIA NACIONAL", "ACTIVO", "HABIDO", "150101"));
        when(repository.save(any(TrackedCompany.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TrackedCompany result = service.register(command(RUC));

        assertThat(result.getName()).isEqualTo("SUPERINTENDENCIA NACIONAL");
        assertThat(result.getRuc()).isEqualTo(RUC);
        assertThat(result.getVerification()).isEqualTo(TrackedCompanyVerification.VERIFIED);
        assertThat(result.getSunatEstado()).isEqualTo("ACTIVO");
        assertThat(result.getSunatCondicion()).isEqualTo("HABIDO");
        assertThat(result.getSunatCheckedAt()).isEqualTo(NOW);
        assertThat(result.getContact().email()).isEqualTo("rosa@example.org");
    }

    @Test
    void rejectsARucWithABadCheckDigitBeforeTouchingTheRepositoryOrSunat() {
        assertThatThrownBy(() -> service.register(command("20131312950")))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(ReportingErrors.INVALID_RUC));
        verifyNoInteractions(repository, rucLookup);
    }

    @Test
    void rejectsADuplicateRucWithoutSpendingASunatCall() {
        TrackedCompany existing = TrackedCompany.create("Otra empresa", RUC, UUID.randomUUID());
        when(repository.findByRuc(RUC)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.register(command(RUC)))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(ReportingErrors.DUPLICATE_TRACKED_COMPANY_RUC));
        verifyNoInteractions(rucLookup);
        verify(repository, never()).save(any());
    }

    @Test
    void mapsEachSunatOutcomeThatBlocksRegistrationToItsError() {
        when(repository.findByRuc(RUC)).thenReturn(Optional.empty());

        when(rucLookup.lookup(Ruc.of(RUC))).thenReturn(new RucLookupResult.Unavailable());
        assertRegisterFailsWith(ReportingErrors.SUNAT_UNAVAILABLE);

        when(rucLookup.lookup(Ruc.of(RUC))).thenReturn(new RucLookupResult.NotFound());
        assertRegisterFailsWith(ReportingErrors.RUC_NOT_FOUND_IN_SUNAT);

        when(rucLookup.lookup(Ruc.of(RUC)))
                .thenReturn(new RucLookupResult.Found("X", "BAJA DEFINITIVA", "NO HABIDO", null));
        assertRegisterFailsWith(ReportingErrors.RUC_DEREGISTERED_IN_SUNAT);

        verify(repository, never()).save(any());
    }

    @Test
    void aNotLocatedTaxpayerIsStillRegistered() {
        when(repository.findByRuc(RUC)).thenReturn(Optional.empty());
        when(rucLookup.lookup(Ruc.of(RUC))).thenReturn(new RucLookupResult.Found("X", "ACTIVO", "NO HABIDO", null));
        when(repository.save(any(TrackedCompany.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TrackedCompany result = service.register(command(RUC));

        assertThat(result.isNotLocated()).isTrue();
        assertThat(result.getVerification()).isEqualTo(TrackedCompanyVerification.VERIFIED);
    }

    private RegisterUnverifiedTrackedCompanyCommand unverifiedCommand() {
        return new RegisterUnverifiedTrackedCompanyCommand(RUC, "  Nombre escrito a mano  ", UUID.randomUUID(), CompanyContact.NONE);
    }

    @Test
    void theFallbackStoresAnUnverifiedCompanyOnlyWhenTheFreshLookupIsUnavailable() {
        when(repository.findByRuc(RUC)).thenReturn(Optional.empty());
        when(rucLookup.lookupFresh(Ruc.of(RUC))).thenReturn(new RucLookupResult.Unavailable());
        when(repository.save(any(TrackedCompany.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TrackedCompany result = service.registerUnverified(unverifiedCommand());

        assertThat(result.getVerification()).isEqualTo(TrackedCompanyVerification.UNVERIFIED);
        assertThat(result.getName()).isEqualTo("Nombre escrito a mano");
        assertThat(result.getSunatCheckedAt()).isNull();
        verify(rucLookup, never()).lookup(any());
    }

    @Test
    void theFallbackIsRefusedWhenSunatAnswersAtAll() {
        when(repository.findByRuc(RUC)).thenReturn(Optional.empty());

        when(rucLookup.lookupFresh(Ruc.of(RUC))).thenReturn(new RucLookupResult.Found("X", "ACTIVO", "HABIDO", null));
        assertThatThrownBy(() -> service.registerUnverified(unverifiedCommand()))
                .satisfies(e -> assertThat(((ApplicationException) e).getError()).isEqualTo(ReportingErrors.SUNAT_ANSWERED));

        when(rucLookup.lookupFresh(Ruc.of(RUC))).thenReturn(new RucLookupResult.NotFound());
        assertThatThrownBy(() -> service.registerUnverified(unverifiedCommand()))
                .satisfies(e -> assertThat(((ApplicationException) e).getError()).isEqualTo(ReportingErrors.SUNAT_ANSWERED));

        verify(repository, never()).save(any());
    }

    @Test
    void theFallbackChecksTheRucAndTheDuplicateBeforeSpendingAProviderCall() {
        assertThatThrownBy(() -> service.registerUnverified(
                        new RegisterUnverifiedTrackedCompanyCommand("20131312950", "X", UUID.randomUUID(), CompanyContact.NONE)))
                .satisfies(e -> assertThat(((ApplicationException) e).getError()).isEqualTo(ReportingErrors.INVALID_RUC));

        when(repository.findByRuc(RUC)).thenReturn(Optional.of(TrackedCompany.create("Otra", RUC, UUID.randomUUID())));
        assertThatThrownBy(() -> service.registerUnverified(unverifiedCommand()))
                .satisfies(e -> assertThat(((ApplicationException) e).getError())
                        .isEqualTo(ReportingErrors.DUPLICATE_TRACKED_COMPANY_RUC));
        verifyNoInteractions(rucLookup);
    }

    private TrackedCompany unverifiedCompany() {
        TrackedCompany company = TrackedCompany.registerUnverified("A mano", RUC, UUID.randomUUID(), CompanyContact.NONE);
        when(repository.findById(company.getId())).thenReturn(Optional.of(company));
        return company;
    }

    @Test
    void refreshVerifiesAnUnverifiedCompanyWithSunatsData() {
        TrackedCompany company = unverifiedCompany();
        when(rucLookup.lookupFresh(Ruc.of(RUC))).thenReturn(new RucLookupResult.Found("RAZON SUNAT", "ACTIVO", "HABIDO", null));
        when(repository.updateSunatData(company.getId(), "RAZON SUNAT", true, "ACTIVO", "HABIDO", NOW)).thenReturn(true);

        service.refreshFromSunat(company.getId());

        verify(repository).updateSunatData(company.getId(), "RAZON SUNAT", true, "ACTIVO", "HABIDO", NOW);
    }

    @Test
    void refreshLeavesAnUnverifiedCompanyUntouchedWhenSunatDoesNotHelp() {
        TrackedCompany company = unverifiedCompany();

        when(rucLookup.lookupFresh(Ruc.of(RUC))).thenReturn(new RucLookupResult.Unavailable());
        assertThatThrownBy(() -> service.refreshFromSunat(company.getId()))
                .satisfies(e -> assertThat(((ApplicationException) e).getError()).isEqualTo(ReportingErrors.SUNAT_UNAVAILABLE));

        when(rucLookup.lookupFresh(Ruc.of(RUC))).thenReturn(new RucLookupResult.NotFound());
        assertThatThrownBy(() -> service.refreshFromSunat(company.getId()))
                .satisfies(e -> assertThat(((ApplicationException) e).getError()).isEqualTo(ReportingErrors.RUC_NOT_FOUND_IN_SUNAT));

        when(rucLookup.lookupFresh(Ruc.of(RUC)))
                .thenReturn(new RucLookupResult.Found("X", "BAJA DEFINITIVA", "NO HABIDO", null));
        assertThatThrownBy(() -> service.refreshFromSunat(company.getId()))
                .satisfies(e -> assertThat(((ApplicationException) e).getError()).isEqualTo(ReportingErrors.RUC_DEREGISTERED_IN_SUNAT));

        verify(repository, never()).updateSunatData(any(), any(), anyBoolean(), any(), any(), any());
    }

    @Test
    void refreshStoresBajaDefinitivaOnAVerifiedCompanyInsteadOfRefusing() {
        TrackedCompany company = TrackedCompany.create("Verificada", RUC, UUID.randomUUID());
        when(repository.findById(company.getId())).thenReturn(Optional.of(company));
        when(rucLookup.lookupFresh(Ruc.of(RUC)))
                .thenReturn(new RucLookupResult.Found("Verificada", "BAJA DEFINITIVA", "NO HABIDO", null));
        when(repository.updateSunatData(company.getId(), "Verificada", true, "BAJA DEFINITIVA", "NO HABIDO", NOW))
                .thenReturn(true);

        service.refreshFromSunat(company.getId());

        verify(repository).updateSunatData(company.getId(), "Verificada", true, "BAJA DEFINITIVA", "NO HABIDO", NOW);
    }

    private void assertRegisterFailsWith(ReportingErrors expected) {
        assertThatThrownBy(() -> service.register(command(RUC)))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError()).isEqualTo(expected));
    }

    @Test
    void returnsTheTrackedCompanyWhenFoundById() {
        TrackedCompany trackedCompany = TrackedCompany.create("Empresa", "20123456789", UUID.randomUUID());
        when(repository.findById(trackedCompany.getId())).thenReturn(Optional.of(trackedCompany));

        TrackedCompany result = service.getById(trackedCompany.getId());

        assertThat(result).isEqualTo(trackedCompany);
    }

    @Test
    void throwsNotFoundWhenTrackedCompanyDoesNotExist() {
        UUID missingId = UUID.randomUUID();
        when(repository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(missingId))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(ReportingErrors.TRACKED_COMPANY_NOT_FOUND));
    }

    @Test
    void listDelegatesToTheRepository() {
        Pageable pageable = Pageable.ofSize(10);
        Page<TrackedCompany> expectedPage = new PageImpl<>(List.of());
        when(repository.findAll(pageable)).thenReturn(expectedPage);

        Page<TrackedCompany> result = service.list(pageable);

        assertThat(result).isSameAs(expectedPage);
    }
}
