package pe.esgtrazabilidad.recycler.certification.adapter.in.web;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class CertificationExceptionHandlerTest {

    private final CertificationExceptionHandler handler = new CertificationExceptionHandler();

    private static ConstraintViolationException constraintViolation(String constraintName) {
        return new ConstraintViolationException("constraint violated", null, constraintName);
    }

    @Test
    void mapsTheAssociationForeignKeyConstraintToAssociationNotFound() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "foreign key violation", constraintViolation("fk_certification_association"));

        ProblemDetail problemDetail = handler.handleDataIntegrityViolation(exception);

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problemDetail.getProperties()).containsEntry("code", "CER-002");
    }

    @Test
    void fallsBackToAGenericConflictForAnUnrecognizedConstraint() {
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException("something else", constraintViolation("some_other_constraint"));

        ProblemDetail problemDetail = handler.handleDataIntegrityViolation(exception);

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problemDetail.getProperties()).containsEntry("code", "DATA_CONFLICT");
    }
}
