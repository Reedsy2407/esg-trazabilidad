package pe.esgtrazabilidad.reporting.sigersolsync.adapter.in.web;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SigersolSyncExceptionHandlerTest {

    private final SigersolSyncExceptionHandler handler = new SigersolSyncExceptionHandler();

    @Test
    void anExclusionViolationAnswers409Rpt006() {
        ProblemDetail problem = handler.handleOverlappingPeriod(new DataIntegrityViolationException("exclusion violation"));

        assertConflict(problem);
    }

    @Test
    void aDeadlockVictimAnswers409Rpt006LikeAnExclusionViolation() {
        PessimisticLockingFailureException deadlock =
                new CannotAcquireLockException("could not execute statement", new SQLException("deadlock detected", "40P01"));

        assertConflict(handler.handleLostTheRace(deadlock));
    }

    @Test
    void aSerializationFailureAnswers409Rpt006() {
        PessimisticLockingFailureException serialization = new CannotAcquireLockException(
                "could not execute statement", new SQLException("wrapper", "HY000", new SQLException("could not serialize access", "40001")));

        assertConflict(handler.handleLostTheRace(serialization));
    }

    @Test
    void aLockFailureThatIsNotADeadlockIsNotThisConflictAndStaysAnError() {
        PessimisticLockingFailureException timeout =
                new CannotAcquireLockException("lock timeout", new SQLException("could not obtain lock", "55P03"));

        assertThatThrownBy(() -> handler.handleLostTheRace(timeout)).isSameAs(timeout);
    }

    private static void assertConflict(ProblemDetail problem) {
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getProperties()).containsEntry("code", "RPT-006");
    }
}
