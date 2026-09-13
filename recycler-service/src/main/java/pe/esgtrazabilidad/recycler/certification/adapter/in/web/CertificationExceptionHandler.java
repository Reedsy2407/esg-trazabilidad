package pe.esgtrazabilidad.recycler.certification.adapter.in.web;

import org.hibernate.exception.ConstraintViolationException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import pe.esgtrazabilidad.recycler.certification.exception.CertificationErrors;

/**
 * Fallback for the same TOCTOU race AssociationService/RecyclerService have:
 * CertificationService's association-exists check isn't atomic with save().
 * Certification has one constraint that can surface this way
 * (fk_certification_association); identified by getConstraintName() rather
 * than the exception message, which varies by driver/locale.
 */
@RestControllerAdvice(assignableTypes = CertificationController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class CertificationExceptionHandler {

    private static final String ASSOCIATION_FK_CONSTRAINT = "fk_certification_association";

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        if (ASSOCIATION_FK_CONSTRAINT.equals(constraintNameOf(exception))) {
            return problemDetailFor(CertificationErrors.ASSOCIATION_NOT_FOUND);
        }

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "La operación viola una restricción de integridad de datos");
        problemDetail.setProperty("code", "DATA_CONFLICT");
        return problemDetail;
    }

    private static String constraintNameOf(DataIntegrityViolationException exception) {
        return exception.getCause() instanceof ConstraintViolationException constraintViolation
                ? constraintViolation.getConstraintName()
                : null;
    }

    private ProblemDetail problemDetailFor(CertificationErrors error) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(error.getStatus(), error.getMessage());
        problemDetail.setProperty("code", error.getCode());
        return problemDetail;
    }
}
