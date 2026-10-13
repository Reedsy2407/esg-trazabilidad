package pe.esgtrazabilidad.reporting.sigersolmunicipal.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.esgtrazabilidad.reporting.sigersolmunicipal.DistrictContext;
import pe.esgtrazabilidad.reporting.sigersolmunicipal.DistrictStats;
import pe.esgtrazabilidad.reporting.sigersolmunicipal.SigersolMunicipalStats;
import pe.esgtrazabilidad.reporting.sigersolmunicipal.SigersolMunicipalStats.Answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The snapshot and fallback decisions, with no database and no provider. */
@ExtendWith(MockitoExtension.class)
class DistrictContextServiceTest {

    private static final String LA_MOLINA = "150114";
    private static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");

    @Mock
    private SigersolMunicipalStats provider;

    @Mock
    private DistrictSnapshotStore store;

    private DistrictContextService service;

    @BeforeEach
    void setUp() {
        service = new DistrictContextService(provider, store, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static DistrictStats snapshot(int year, Instant fetchedAt) {
        return new DistrictStats(year, LA_MOLINA, 3, 31, fetchedAt);
    }

    @Test
    void aFreshSnapshotIsUsedWithoutAskingTheProvider() {
        when(store.latest(LA_MOLINA)).thenReturn(Optional.of(snapshot(2024, NOW.minusSeconds(86_400))));

        DistrictContext context = service.contextFor(LA_MOLINA);

        assertThat(context.status()).isEqualTo(DistrictContext.Status.AVAILABLE);
        assertThat(context).extracting("year", "associationCount", "recyclerCount", "stale")
                .containsExactly(2024, 3, 31, false);
        verifyNoInteractions(provider);
    }

    @Test
    void withNoSnapshotItProbesDownFromLastYearAndStoresTheFirstYearWithRows() {
        when(store.latest(LA_MOLINA)).thenReturn(Optional.empty());
        when(provider.fetch(2025, LA_MOLINA)).thenReturn(new Answer.NoData());
        when(provider.fetch(2024, LA_MOLINA)).thenReturn(new Answer.Found(3, 31));

        DistrictContext context = service.contextFor(LA_MOLINA);

        assertThat(context.year()).isEqualTo(2024);
        assertThat(context.associationCount()).isEqualTo(3);
        verify(provider, never()).fetch(2023, LA_MOLINA);
        verify(store).save(new DistrictStats(2024, LA_MOLINA, 3, 31, NOW));
    }

    @Test
    void anOutageWithNoSnapshotIsUnavailableAndNothingIsStored() {
        when(store.latest(LA_MOLINA)).thenReturn(Optional.empty());
        when(provider.fetch(anyInt(), anyString())).thenReturn(new Answer.Unavailable());

        DistrictContext context = service.contextFor(LA_MOLINA);

        assertThat(context.status()).isEqualTo(DistrictContext.Status.UNAVAILABLE);
        assertThat(context.year()).isNull();
        verify(provider, times(1)).fetch(anyInt(), anyString()); // an outage does not probe older years
        verify(store, never()).save(any());
    }

    @Test
    void anExpiredSnapshotIsRefreshedWhenTheProviderAnswers() {
        when(store.latest(LA_MOLINA)).thenReturn(Optional.of(snapshot(2023, NOW.minusSeconds(40L * 86_400))));
        when(provider.fetch(2025, LA_MOLINA)).thenReturn(new Answer.NoData());
        when(provider.fetch(2024, LA_MOLINA)).thenReturn(new Answer.Found(4, 40));

        DistrictContext context = service.contextFor(LA_MOLINA);

        assertThat(context).extracting("year", "associationCount", "stale").containsExactly(2024, 4, false);
        verify(store).save(any(DistrictStats.class));
    }

    @Test
    void anExpiredSnapshotIsTheLastGoodValueWhenTheProviderIsDown() {
        when(store.latest(LA_MOLINA)).thenReturn(Optional.of(snapshot(2023, NOW.minusSeconds(40L * 86_400))));
        when(provider.fetch(anyInt(), anyString())).thenReturn(new Answer.Unavailable());

        DistrictContext context = service.contextFor(LA_MOLINA);

        assertThat(context.status()).isEqualTo(DistrictContext.Status.AVAILABLE);
        assertThat(context).extracting("year", "stale").containsExactly(2023, true);
        verify(store, never()).save(any());
    }

    @Test
    void anAdapterThatBreaksItsContractOrAValueTheTableRefusesNeverStopsTheCaller() {
        when(store.latest(LA_MOLINA)).thenReturn(Optional.empty());
        when(provider.fetch(2025, LA_MOLINA)).thenThrow(new IllegalStateException("boom"));

        assertThat(service.contextFor(LA_MOLINA).status()).isEqualTo(DistrictContext.Status.UNAVAILABLE);

        org.mockito.Mockito.doReturn(new Answer.Found(-1, 1)).when(provider).fetch(2025, LA_MOLINA);
        org.mockito.Mockito.doThrow(new IllegalStateException("check violated")).when(store).save(any());
        assertThat(service.contextFor(LA_MOLINA).status()).isEqualTo(DistrictContext.Status.UNAVAILABLE);
    }

    @Test
    void anUnreadableSnapshotTableIsAlsoJustUnavailable() {
        when(store.latest(LA_MOLINA)).thenThrow(new IllegalStateException("db down"));

        assertThat(service.contextFor(LA_MOLINA).status()).isEqualTo(DistrictContext.Status.UNAVAILABLE);
        verifyNoInteractions(provider);
    }

    @Test
    void aDistrictSigersolHasNoRowsForInAnyProbedYearIsUnavailable() {
        when(store.latest(LA_MOLINA)).thenReturn(Optional.empty());
        when(provider.fetch(anyInt(), anyString())).thenReturn(new Answer.NoData());

        assertThat(service.contextFor(LA_MOLINA).status()).isEqualTo(DistrictContext.Status.UNAVAILABLE);
        verify(provider, times(DistrictContextService.PROBE_YEARS)).fetch(anyInt(), anyString());
        verify(store, never()).save(any());
    }
}
