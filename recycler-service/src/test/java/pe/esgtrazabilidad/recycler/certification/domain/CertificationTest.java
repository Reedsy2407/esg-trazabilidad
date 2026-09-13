package pe.esgtrazabilidad.recycler.certification.domain;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CertificationTest {

    private final UUID associationId = UUID.randomUUID();

    @Test
    void createsACertificationWithAGeneratedId() {
        Certification certification = Certification.create(
                associationId, "ISO 14001", LocalDate.now().minusDays(30), LocalDate.now().plusYears(1));

        assertThat(certification.getId()).isNotNull();
        assertThat(certification.getAssociationId()).isEqualTo(associationId);
    }

    @Test
    void rejectsAnIssuedDateThatIsNotBeforeTheExpirationDate() {
        LocalDate sameDay = LocalDate.now();

        assertThatThrownBy(() -> Certification.create(associationId, "ISO 14001", sameDay, sameDay))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnIssuedDateAfterTheExpirationDate() {
        assertThatThrownBy(() -> Certification.create(
                        associationId, "ISO 14001", LocalDate.now(), LocalDate.now().minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isNotExpiredWhenExpirationDateIsToday() {
        Certification certification = Certification.create(
                associationId, "ISO 14001", LocalDate.now().minusDays(30), LocalDate.now());

        assertThat(certification.isExpired()).isFalse();
    }

    @Test
    void isExpiredWhenExpirationDateWasYesterday() {
        Certification certification = Certification.reconstruct(
                UUID.randomUUID(),
                associationId,
                "ISO 14001",
                LocalDate.now().minusDays(30),
                LocalDate.now().minusDays(1));

        assertThat(certification.isExpired()).isTrue();
    }

    @Test
    void isNotExpiredWhenExpirationDateIsFarInTheFuture() {
        Certification certification = Certification.create(
                associationId, "ISO 14001", LocalDate.now(), LocalDate.now().plusYears(5));

        assertThat(certification.isExpired()).isFalse();
    }

    @Test
    void renewExtendsTheExpirationDate() {
        Certification certification = Certification.create(
                associationId, "ISO 14001", LocalDate.now().minusDays(30), LocalDate.now().plusDays(1));
        LocalDate newExpirationDate = LocalDate.now().plusYears(1);

        certification.renew(newExpirationDate);

        assertThat(certification.getExpirationDate()).isEqualTo(newExpirationDate);
    }

    @Test
    void renewingWithADateNotAfterTheIssuedDateThrows() {
        Certification certification = Certification.create(
                associationId, "ISO 14001", LocalDate.now().minusDays(30), LocalDate.now().plusDays(1));

        assertThatThrownBy(() -> certification.renew(certification.getIssuedDate()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
