package pe.esgtrazabilidad.collection.neighbor.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import pe.esgtrazabilidad.collection.exception.CollectionErrors;
import pe.esgtrazabilidad.collection.neighbor.domain.Neighbor;
import pe.esgtrazabilidad.collection.neighbor.domain.NeighborStatus;
import pe.esgtrazabilidad.collection.neighbor.port.in.CreateNeighborCommand;
import pe.esgtrazabilidad.collection.neighbor.port.out.NeighborRepository;
import pe.esgtrazabilidad.kernel.error.ApplicationError;
import pe.esgtrazabilidad.kernel.error.ApplicationException;
import pe.esgtrazabilidad.kernel.ubigeo.UbigeoCatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NeighborServiceTest {

    @Mock
    private NeighborRepository repository;

    private NeighborService service;

    @BeforeEach
    void setUp() {
        service = new NeighborService(repository);
    }

    private CreateNeighborCommand commandWith(String districtUbigeo, String district) {
        return new CreateNeighborCommand("Ana Torres", "999999999", "Av. Siempre Viva 123", districtUbigeo, district);
    }

    @Test
    void createsAndSavesANewNeighborWithTheCatalogDistrictForItsUbigeo() {
        when(repository.save(any(Neighbor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Neighbor result = service.create(commandWith("150128", null));

        assertThat(result.getFullName()).isEqualTo("Ana Torres");
        assertThat(result.getStatus()).isEqualTo(NeighborStatus.ACTIVE);
        assertThat(result.getDistrictUbigeo()).isEqualTo("150128");
        assertThat(result.getDistrict()).isEqualTo("Rímac");
        verify(repository).save(any(Neighbor.class));
    }

    @Test
    void theUbigeoWinsOverTheFreeTextDistrict() {
        when(repository.save(any(Neighbor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Neighbor result = service.create(commandWith("150110", "Independencia"));

        assertThat(result.getDistrictUbigeo()).isEqualTo("150110");
    }

    @Test
    void acceptsTheV2FreeTextDistrictWhenItNamesACatalogDistrictIgnoringCaseAndAccents() {
        when(repository.save(any(Neighbor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Neighbor result = service.create(commandWith(null, "  san martin de porres "));

        assertThat(result.getDistrictUbigeo()).isEqualTo("150135");
        assertThat(result.getDistrict()).isEqualTo("San Martín de Porres");
    }

    @Test
    void refusesAnUnknownUbigeoAFreeTextOutsideTheCatalogOrNoDistrictAtAll() {
        assertError(() -> service.create(commandWith("150201", null)), CollectionErrors.INVALID_DISTRICT);
        assertError(() -> service.create(commandWith(null, "Surco")), CollectionErrors.INVALID_DISTRICT);
        assertError(() -> service.create(commandWith(null, null)), CollectionErrors.INVALID_DISTRICT);
        verifyNoInteractions(repository);
    }

    @Test
    void returnsTheNeighborWhenFoundById() {
        Neighbor neighbor = Neighbor.create(
                "Ana Torres", "999999999", "Av. Siempre Viva 123", UbigeoCatalog.find("150140").orElseThrow());
        when(repository.findById(neighbor.getId())).thenReturn(Optional.of(neighbor));

        Neighbor result = service.getById(neighbor.getId());

        assertThat(result).isEqualTo(neighbor);
    }

    @Test
    void throwsNotFoundWhenNeighborDoesNotExist() {
        UUID missingId = UUID.randomUUID();
        when(repository.findById(missingId)).thenReturn(Optional.empty());

        assertError(() -> service.getById(missingId), CollectionErrors.NEIGHBOR_NOT_FOUND);
    }

    @Test
    void listFiltersByUbigeo() {
        Pageable pageable = Pageable.ofSize(10);
        Page<Neighbor> expectedPage = new PageImpl<>(List.of());
        when(repository.findAll(NeighborStatus.ACTIVE, "150140", pageable)).thenReturn(expectedPage);

        Page<Neighbor> result = service.list(NeighborStatus.ACTIVE, "150140", null, pageable);

        assertThat(result).isSameAs(expectedPage);
    }

    @Test
    void listResolvesTheV2DistrictFilterByNameWithoutCaseOrAccents() {
        Pageable pageable = Pageable.ofSize(10);
        when(repository.findAll(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.list(null, null, "RIMAC", pageable);

        ArgumentCaptor<String> ubigeo = ArgumentCaptor.forClass(String.class);
        verify(repository).findAll(isNull(), ubigeo.capture(), eq(pageable));
        assertThat(ubigeo.getValue()).isEqualTo("150128");
    }

    @Test
    void listWithADistrictNameOutsideTheCatalogMatchesNobodyWithoutQuerying() {
        Page<Neighbor> result = service.list(null, null, "Surco", Pageable.ofSize(10));

        assertThat(result).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void listWithAnUnknownUbigeoIsRefused() {
        assertError(() -> service.list(null, "999999", null, Pageable.ofSize(10)), CollectionErrors.INVALID_DISTRICT);
    }

    @Test
    void listWithoutADistrictFilterDoesNotFilterByDistrict() {
        Pageable pageable = Pageable.ofSize(10);
        Page<Neighbor> expectedPage = new PageImpl<>(List.of());
        when(repository.findAll(NeighborStatus.INACTIVE, null, pageable)).thenReturn(expectedPage);

        assertThat(service.list(NeighborStatus.INACTIVE, null, " ", pageable)).isSameAs(expectedPage);
    }

    private static void assertError(ThrowingCallable call, ApplicationError error) {
        assertThatThrownBy(call)
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError()).isEqualTo(error));
    }
}
