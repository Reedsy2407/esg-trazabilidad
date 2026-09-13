package pe.esgtrazabilidad.recycler.recycler.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.esgtrazabilidad.kernel.error.ApplicationException;
import pe.esgtrazabilidad.recycler.association.port.out.AssociationRepository;
import pe.esgtrazabilidad.recycler.recycler.domain.Recycler;
import pe.esgtrazabilidad.recycler.recycler.exception.RecyclerErrors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecyclerRepositoryAdapterTest {

    @Mock
    private RecyclerJpaRepository jpaRepository;

    @Mock
    private AssociationRepository associationRepository;

    private RecyclerRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RecyclerRepositoryAdapter(jpaRepository, associationRepository);
    }

    @Test
    void rejectsSavingARecyclerWhoseAssociationDoesNotExist() {
        UUID missingAssociationId = UUID.randomUUID();
        when(associationRepository.findById(missingAssociationId)).thenReturn(Optional.empty());
        Recycler recycler = Recycler.create("Juan Pérez", "12345678", "999999999", missingAssociationId);

        assertThatThrownBy(() -> adapter.save(recycler))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(RecyclerErrors.ASSOCIATION_NOT_FOUND));
        verify(jpaRepository, never()).save(any());
    }
}
