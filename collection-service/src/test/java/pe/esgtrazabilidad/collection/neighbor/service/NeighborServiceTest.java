package pe.esgtrazabilidad.collection.neighbor.service;

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

import pe.esgtrazabilidad.collection.exception.CollectionErrors;
import pe.esgtrazabilidad.collection.neighbor.domain.Neighbor;
import pe.esgtrazabilidad.collection.neighbor.domain.NeighborStatus;
import pe.esgtrazabilidad.collection.neighbor.port.in.CreateNeighborCommand;
import pe.esgtrazabilidad.collection.neighbor.port.out.NeighborRepository;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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

    private CreateNeighborCommand sampleCommand() {
        return new CreateNeighborCommand("Ana Torres", "999999999", "Av. Siempre Viva 123", "Surco");
    }

    @Test
    void createsAndSavesANewNeighbor() {
        when(repository.save(any(Neighbor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Neighbor result = service.create(sampleCommand());

        assertThat(result.getFullName()).isEqualTo("Ana Torres");
        assertThat(result.getStatus()).isEqualTo(NeighborStatus.ACTIVE);
        verify(repository).save(any(Neighbor.class));
    }

    @Test
    void returnsTheNeighborWhenFoundById() {
        Neighbor neighbor = Neighbor.create("Ana Torres", "999999999", "Av. Siempre Viva 123", "Surco");
        when(repository.findById(neighbor.getId())).thenReturn(Optional.of(neighbor));

        Neighbor result = service.getById(neighbor.getId());

        assertThat(result).isEqualTo(neighbor);
    }

    @Test
    void throwsNotFoundWhenNeighborDoesNotExist() {
        UUID missingId = UUID.randomUUID();
        when(repository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(missingId))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(CollectionErrors.NEIGHBOR_NOT_FOUND));
    }

    @Test
    void listDelegatesToTheRepository() {
        Pageable pageable = Pageable.ofSize(10);
        Page<Neighbor> expectedPage = new PageImpl<>(List.of());
        when(repository.findAll(NeighborStatus.ACTIVE, "Surco", pageable)).thenReturn(expectedPage);

        Page<Neighbor> result = service.list(NeighborStatus.ACTIVE, "Surco", pageable);

        assertThat(result).isSameAs(expectedPage);
    }
}
