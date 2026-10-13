package pe.esgtrazabilidad.reporting.sunat.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import pe.esgtrazabilidad.kernel.ruc.Ruc;
import pe.esgtrazabilidad.reporting.sunat.RucLookup;
import pe.esgtrazabilidad.reporting.sunat.RucLookupResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The cache decisions, without a database: what is read, what is asked, and what is stored. */
@ExtendWith(MockitoExtension.class)
class CachedRucLookupTest {

    private static final Ruc RUC = Ruc.of("20131312955");
    private static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");
    private static final RucLookupResult.Found FOUND =
            new RucLookupResult.Found("EMPRESA S.A.", "ACTIVO", "HABIDO", "150101");

    @Mock
    private RucLookup provider;

    @Mock
    private JdbcTemplate jdbc;

    private CachedRucLookup cache;

    @BeforeEach
    void setUp() {
        cache = new CachedRucLookup(provider, jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @SuppressWarnings("unchecked")
    private void cachedAnswer(List<RucLookupResult> rows) {
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any())).thenReturn((List) rows);
    }

    @Test
    void aFreshCacheEntryIsReturnedWithoutAskingTheProvider() {
        cachedAnswer(List.of(FOUND));

        assertThat(cache.lookup(RUC)).isEqualTo(FOUND);

        verifyNoInteractions(provider);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void aMissAsksTheProviderAndStoresAFoundAnswer() {
        cachedAnswer(List.of());
        when(provider.lookup(RUC)).thenReturn(FOUND);

        assertThat(cache.lookup(RUC)).isEqualTo(FOUND);

        verify(jdbc).update(anyString(), any(Object[].class));
    }

    @Test
    void aNotFoundAnswerIsStoredToo() {
        cachedAnswer(List.of());
        when(provider.lookup(RUC)).thenReturn(new RucLookupResult.NotFound());

        assertThat(cache.lookup(RUC)).isInstanceOf(RucLookupResult.NotFound.class);

        verify(jdbc).update(anyString(), any(Object[].class));
    }

    @Test
    void anOutageIsNeverStoredSoTheNextRequestRetries() {
        cachedAnswer(List.of());
        when(provider.lookup(RUC)).thenReturn(new RucLookupResult.Unavailable());

        assertThat(cache.lookup(RUC)).isInstanceOf(RucLookupResult.Unavailable.class);

        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void lookupFreshSkipsTheCacheReadButStoresTheNewAnswer() {
        when(provider.lookup(RUC)).thenReturn(FOUND);

        assertThat(cache.lookupFresh(RUC)).isEqualTo(FOUND);

        verify(jdbc, never()).query(anyString(), any(RowMapper.class), any(), any());
        verify(jdbc).update(anyString(), any(Object[].class));
    }

    @Test
    void theCacheOnlyTrustsEntriesYoungerThanTwentyFourHours() {
        cachedAnswer(List.of());
        when(provider.lookup(RUC)).thenReturn(FOUND);

        cache.lookup(RUC);

        org.mockito.ArgumentCaptor<Object> oldest = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(jdbc).query(anyString(), any(RowMapper.class), any(), oldest.capture());
        assertThat(oldest.getValue()).isEqualTo(java.time.LocalDateTime.of(2026, 10, 11, 12, 0));
    }
}
