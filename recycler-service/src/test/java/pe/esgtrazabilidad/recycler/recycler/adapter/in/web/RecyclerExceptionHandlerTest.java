package pe.esgtrazabilidad.recycler.recycler.adapter.in.web;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class RecyclerExceptionHandlerTest {

    private final RecyclerExceptionHandler handler = new RecyclerExceptionHandler();

    private static ConstraintViolationException constraintViolation(String constraintName) {
        return new ConstraintViolationException("constraint violated", null, constraintName);
    }

    @Test
    void mapsTheDniUniqueConstraintToDuplicateDni() {
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException("duplicate key", constraintViolation("recycler_dni_key"));

        ProblemDetail problemDetail = handler.handleDataIntegrityViolation(exception);

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problemDetail.getProperties()).containsEntry("code", "REC-002");
    }

    @Test
    void mapsTheAssociationForeignKeyConstraintToAssociationNotFound() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "foreign key violation", constraintViolation("fk_recycler_association"));

        ProblemDetail problemDetail = handler.handleDataIntegrityViolation(exception);

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problemDetail.getProperties()).containsEntry("code", "REC-003");
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
