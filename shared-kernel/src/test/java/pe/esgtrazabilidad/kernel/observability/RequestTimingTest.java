package pe.esgtrazabilidad.kernel.observability;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import javax.sql.DataSource;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestTimingTest {

    private final ListAppender<ILoggingEvent> lines = new ListAppender<>();
    private Logger http;
    private Logger db;

    @BeforeEach
    void capture() {
        http = (Logger) LoggerFactory.getLogger("esg.http");
        db = (Logger) LoggerFactory.getLogger("esg.db");
        lines.start();
        http.addAppender(lines);
        db.addAppender(lines);
    }

    @AfterEach
    void release() {
        http.detachAppender(lines);
        db.detachAppender(lines);
    }

    private DataSource timed(long slowMillis) throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        when(statement.executeQuery()).thenAnswer(invocation -> {
            Thread.sleep(25);
            return null;
        });
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement("SELECT * FROM staff_user WHERE email = ? AND id = 42")).thenReturn(statement);
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        return TimedDataSource.wrap(dataSource, "demo-service", slowMillis);
    }

    @Test
    void oneLineWithTheFixedFieldsUsesTheRouteTemplateAndCountsTheDatabaseTime() throws Exception {
        DataSource dataSource = timed(10_000);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/neighbors/0192f3a8-1c2d-7e4f-8a9b-0c1d2e3f4a5b");
        request.setQueryString("email=ana@example.com&token=secret");
        request.setAttribute("org.springframework.web.servlet.HandlerMapping.bestMatchingPattern", "/neighbors/{neighborId}");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        new RequestTimingFilter("demo-service").doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                try {
                    dataSource.getConnection()
                            .prepareStatement("SELECT * FROM staff_user WHERE email = ? AND id = 42")
                            .executeQuery();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        });

        assertThat(lines.list).hasSize(1);
        ILoggingEvent event = lines.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        String line = event.getFormattedMessage();
        assertThat(line).startsWith("event=http_request service=demo-service method=GET route=/neighbors/{neighborId} status=200 durationMs=");
        assertThat(line).containsPattern("dbMs=([2-9]\\d|\\d{3,}) ").contains("dbQueries=1");
        // Neither the real id, the query string nor the e-mail or token ever reach the log.
        assertThat(line).doesNotContain("0192f3a8").doesNotContain("ana@").doesNotContain("secret");
    }

    @Test
    void aFailureIsLoggedAtErrorAndAnUnmatchedRouteNeverShowsItsPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login/abc123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(503);

        new RequestTimingFilter("demo-service").doFilter(request, response, new MockFilterChain());

        ILoggingEvent event = lines.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage()).contains("route=UNMATCHED status=503").doesNotContain("abc123");
    }

    @Test
    void anExceptionThatEscapesIsLoggedAsAnErrorWithStatus500AndStillPropagates() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        MockHttpServletResponse response = new MockHttpServletResponse();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new RequestTimingFilter("demo-service")
                        .doFilter(request, response, new MockFilterChain() {
                            @Override
                            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                                throw new IllegalStateException("boom");
                            }
                        }))
                .hasMessageContaining("boom");

        assertThat(lines.list.get(0).getLevel()).isEqualTo(Level.ERROR);
        assertThat(lines.list.get(0).getFormattedMessage()).contains("status=500");
    }

    @Test
    void aSlowStatementIsLoggedOnceWithItsShapeOnly() throws Exception {
        DataSource dataSource = timed(10);

        dataSource.getConnection().prepareStatement("SELECT * FROM staff_user WHERE email = ? AND id = 42").executeQuery();

        List<String> slow = lines.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(slow).hasSize(1);
        assertThat(slow.get(0)).startsWith("event=slow_query service=demo-service durationMs=")
                .contains("sql=\"SELECT * FROM staff_user WHERE email = ? AND id = ?\"");
    }

    @Test
    void theShapeDropsLiteralsAndNumbersAndCutsLongStatements() {
        assertThat(TimedDataSource.shape("update t set name = 'Ana ''Q'' Ruiz', kg = 12.50 where id = 7"))
                .isEqualTo("update t set name = ?, kg = ? where id = ?");
        assertThat(TimedDataSource.shape("select " + "x, ".repeat(100) + "y")).hasSize(163).endsWith("...");
    }
}
