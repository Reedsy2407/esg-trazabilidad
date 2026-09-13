package pe.esgtrazabilidad.recycler.certification.service;

import java.time.LocalDate;
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
import pe.esgtrazabilidad.recycler.association.domain.Association;
import pe.esgtrazabilidad.recycler.association.port.out.AssociationRepository;
import pe.esgtrazabilidad.recycler.certification.domain.Certification;
import pe.esgtrazabilidad.recycler.certification.exception.CertificationErrors;
import pe.esgtrazabilidad.recycler.certification.port.in.CreateCertificationCommand;
import pe.esgtrazabilidad.recycler.certification.port.out.CertificationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificationServiceTest {

    @Mock
    private CertificationRepository certificationRepository;

    @Mock
    private AssociationRepository associationRepository;

    private CertificationService service;

    private final UUID associationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CertificationService(certificationRepository, associationRepository);
    }

    private Association existingAssociation() {
        return Association.create("Asociación", "20123456789", "REG-001", "Dirección", "a@b.pe", "999999999");
    }

    private CreateCertificationCommand sampleCommand() {
        return new CreateCertificationCommand(
                associationId, "ISO 14001", LocalDate.now().minusDays(30), LocalDate.now().plusYears(1));
    }

    @Test
    void createsAndSavesACertificationWhenAssociationExistsAndDatesAreValid() {
        when(associationRepository.findById(associationId)).thenReturn(Optional.of(existingAssociation()));
        when(certificationRepository.save(any(Certification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Certification result = service.create(sampleCommand());

        assertThat(result.getAssociationId()).isEqualTo(associationId);
        assertThat(result.getCertificationType()).isEqualTo("ISO 14001");
        verify(certificationRepository).save(any(Certification.class));
    }

    @Test
    void rejectsCreationWhenTheAssociationDoesNotExist() {
        when(associationRepository.findById(associationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(sampleCommand()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(CertificationErrors.ASSOCIATION_NOT_FOUND));
        verify(certificationRepository, never()).save(any());
    }

    @Test
    void rejectsCreationWhenTheIssuedDateIsNotBeforeTheExpirationDate() {
        when(associationRepository.findById(associationId)).thenReturn(Optional.of(existingAssociation()));
        LocalDate sameDay = LocalDate.now();
        CreateCertificationCommand invalidCommand =
                new CreateCertificationCommand(associationId, "ISO 14001", sameDay, sameDay);

        assertThatThrownBy(() -> service.create(invalidCommand))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(CertificationErrors.INVALID_DATE_RANGE));
        verify(certificationRepository, never()).save(any());
    }

    @Test
    void returnsTheCertificationWhenFoundByIdUnderItsOwnAssociation() {
        Certification certification = Certification.create(
                associationId, "ISO 14001", LocalDate.now().minusDays(30), LocalDate.now().plusYears(1));
        when(certificationRepository.findById(certification.getId())).thenReturn(Optional.of(certification));

        Certification result = service.getById(associationId, certification.getId());

        assertThat(result).isEqualTo(certification);
    }

    @Test
    void throwsNotFoundWhenCertificationDoesNotExist() {
        UUID missingId = UUID.randomUUID();
        when(certificationRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(associationId, missingId))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(CertificationErrors.NOT_FOUND));
    }

    @Test
    void throwsNotFoundWhenCertificationBelongsToADifferentAssociation() {
        UUID otherAssociationId = UUID.randomUUID();
        Certification certification = Certification.create(
                otherAssociationId, "ISO 14001", LocalDate.now().minusDays(30), LocalDate.now().plusYears(1));
        when(certificationRepository.findById(certification.getId())).thenReturn(Optional.of(certification));

        assertThatThrownBy(() -> service.getById(associationId, certification.getId()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(CertificationErrors.NOT_FOUND));
    }

    @Test
    void listDelegatesToTheRepository() {
        Pageable pageable = Pageable.ofSize(10);
        Page<Certification> expectedPage = new PageImpl<>(java.util.List.of());
        when(certificationRepository.findAll(associationId, pageable)).thenReturn(expectedPage);

        Page<Certification> result = service.list(associationId, pageable);

        assertThat(result).isSameAs(expectedPage);
    }
}
