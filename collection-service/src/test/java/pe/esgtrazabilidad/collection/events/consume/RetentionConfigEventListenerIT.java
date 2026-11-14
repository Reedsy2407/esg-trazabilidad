package pe.esgtrazabilidad.collection.events.consume;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;

import pe.esgtrazabilidad.collection.retention.RetentionSettingsCopy;
import pe.esgtrazabilidad.collection.retention.RetentionSettingsCopy.RetentionTerms;

/** CA-41, consumer side: the periods in force follow the newest announcement, and default to 90 and 12. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "esg.events.outbox-dispatcher.enabled=false")
class RetentionConfigEventListenerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private RetentionConfigEventListener listener;

    @Autowired
    private RetentionSettingsCopy settings;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private static final Instant T0 = Instant.parse("2026-11-14T12:00:00Z");

    @BeforeEach
    void noAnnouncementYet() {
        jdbc.update("delete from retention_settings_copy");
    }

    private Message message(int gpsDays, int photoMonths, Instant at) throws Exception {
        return json(objectMapper.writeValueAsString(new RetentionConfigChangedEvent(UUID.randomUUID(), at, gpsDays, photoMonths, 2)));
    }

    private static Message json(String body) {
        return MessageBuilder.withBody(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)).build();
    }

    @Test
    void withoutAnyEventTheDecidedDefaultsHold() {
        assertThat(settings.current()).isEqualTo(new RetentionTerms(90, 12));
    }

    @Test
    void anAnnouncementChangesTheTermsOfTheNextRun() throws Exception {
        listener.handle(message(60, 6, T0));

        assertThat(settings.current()).isEqualTo(new RetentionTerms(60, 6));
    }

    @Test
    void anOlderOrRepeatedEventChangesNothing() throws Exception {
        listener.handle(message(60, 6, T0.plusSeconds(60)));

        listener.handle(message(120, 24, T0)); // older, delivered later
        assertThat(settings.current()).isEqualTo(new RetentionTerms(60, 6));

        Message sameEventAgain = message(60, 6, T0.plusSeconds(60));
        listener.handle(sameEventAgain);
        listener.handle(sameEventAgain);
        assertThat(settings.current()).isEqualTo(new RetentionTerms(60, 6));

        listener.handle(message(45, 9, T0.plusSeconds(120))); // newer: applies
        assertThat(settings.current()).isEqualTo(new RetentionTerms(45, 9));
        assertThat(jdbc.queryForObject("select count(*) from retention_settings_copy", Long.class)).as("one row only").isEqualTo(1L);
    }

    @Test
    void theFinalStateDoesNotDependOnArrivalOrderEvenWithConcurrentDeliveries() throws Exception {
        List<Message> events = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            events.add(message(30 + i, 3 + (i % 5), T0.plusSeconds(i)));
        }
        int threads = 6;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> done = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int offset = t;
                done.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    // Each thread delivers the whole set, in a different order.
                    for (int i = 0; i < events.size(); i++) {
                        listener.handle(events.get((i + offset * 2) % events.size()));
                    }
                    return null;
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            for (Future<?> f : done) {
                f.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(settings.current()).as("the newest announcement wins").isEqualTo(new RetentionTerms(41, 3 + (11 % 5)));
    }

    @Test
    void aMalformedOutOfRangeOrIncompleteAnnouncementIsDiscarded() throws Exception {
        listener.handle(message(60, 6, T0));

        listener.handle(json("not json"));
        listener.handle(message(0, 6, T0.plusSeconds(10))); // a zero would purge everything at once
        listener.handle(message(60, 0, T0.plusSeconds(11)));
        listener.handle(message(10_000, 6, T0.plusSeconds(12)));
        listener.handle(message(60, 99, T0.plusSeconds(13)));
        listener.handle(json("{\"eventId\":\"" + UUID.randomUUID() + "\",\"gpsDays\":45}"));

        assertThat(settings.current()).isEqualTo(new RetentionTerms(60, 6));
    }

    @Test
    void theRangesAreTheOnesAuthAcceptsAndInclusive() throws Exception {
        listener.handle(message(RetentionSettingsCopy.MIN_GPS_DAYS, RetentionSettingsCopy.MIN_PHOTO_MONTHS, T0));
        assertThat(settings.current()).isEqualTo(new RetentionTerms(30, 3));
        listener.handle(message(RetentionSettingsCopy.MAX_GPS_DAYS, RetentionSettingsCopy.MAX_PHOTO_MONTHS, T0.plusSeconds(1)));
        assertThat(settings.current()).isEqualTo(new RetentionTerms(365, 36));
    }
}
