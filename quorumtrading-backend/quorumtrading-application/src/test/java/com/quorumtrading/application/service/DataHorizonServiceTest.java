package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.MarketDataHorizonRepository;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.GranularityBucket;
import com.quorumtrading.model.enums.HorizonSource;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.marketdata.DataHorizon;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.TimeRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataHorizonServiceTest {

    @Mock
    MarketDataHorizonRepository repository;

    @Mock
    DataHorizonProbeService probeService;

    private DataHorizonService service;

    private static final Symbol BTC = new Symbol("BTC", InvestmentType.CRYPTO);

    @BeforeEach
    void setUp() {
        service = new DataHorizonService(repository, probeService, 14);
    }

    private DataHorizon horizon(LocalDate earliest, Instant probedAt, HorizonSource source) {
        return new DataHorizon("probe", BTC, EventType.PRICE, GranularityBucket.DAILY,
                Optional.ofNullable(earliest), probedAt, source);
    }

    @Test
    void cacheHitFreshReturnsWithoutProbing() {
        DataHorizon fresh = horizon(LocalDate.of(2024, 7, 1), Instant.now(), HorizonSource.PROBE);
        when(repository.find(any(), any(), any(), any())).thenReturn(Optional.of(fresh));

        Optional<LocalDate> result = service.earliest(BTC, EventType.PRICE);

        assertThat(result).contains(LocalDate.of(2024, 7, 1));
        verifyNoInteractions(probeService);
        verify(repository, never()).upsert(any());
    }

    @Test
    void staleCacheTriggersReprobeAndUpsert() {
        DataHorizon stale = horizon(LocalDate.of(2024, 7, 1),
                Instant.now().minus(java.time.Duration.ofDays(30)), HorizonSource.PROBE);
        when(repository.find(any(), any(), any(), any())).thenReturn(Optional.of(stale));
        when(probeService.probeEarliest(BTC, EventType.PRICE))
                .thenReturn(Optional.of(LocalDate.of(2024, 8, 1)));

        Optional<LocalDate> result = service.earliest(BTC, EventType.PRICE);

        assertThat(result).contains(LocalDate.of(2024, 8, 1));
        verify(probeService).probeEarliest(BTC, EventType.PRICE);
        ArgumentCaptor<DataHorizon> captor = ArgumentCaptor.forClass(DataHorizon.class);
        verify(repository).upsert(captor.capture());
        assertThat(captor.getValue().source()).isEqualTo(HorizonSource.PROBE);
    }

    @Test
    void cacheMissProbesAndUpserts() {
        when(repository.find(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(probeService.probeEarliest(BTC, EventType.PRICE))
                .thenReturn(Optional.of(LocalDate.of(2024, 7, 31)));

        Optional<LocalDate> result = service.earliest(BTC, EventType.PRICE);

        assertThat(result).contains(LocalDate.of(2024, 7, 31));
        verify(repository).upsert(any());
    }

    @Test
    void probeEmptyCachesNoDataRow() {
        when(repository.find(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(probeService.probeEarliest(BTC, EventType.PRICE)).thenReturn(Optional.empty());

        Optional<LocalDate> result = service.earliest(BTC, EventType.PRICE);

        assertThat(result).isEmpty();
        ArgumentCaptor<DataHorizon> captor = ArgumentCaptor.forClass(DataHorizon.class);
        verify(repository).upsert(captor.capture());
        assertThat(captor.getValue().hasData()).isFalse();
        assertThat(captor.getValue().source()).isEqualTo(HorizonSource.PROBE);
    }

    @Test
    void recordNoDataRaisesFloor() {
        DataHorizon current = horizon(LocalDate.of(2024, 1, 1), Instant.now(), HorizonSource.PROBE);
        when(repository.find(any(), any(), any(), any())).thenReturn(Optional.of(current));

        // No data observed Jan-Mar 2024; range starts at the floor → raise to Apr 1.
        service.recordNoData(BTC, EventType.PRICE,
                TimeRange.of(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31)));

        ArgumentCaptor<DataHorizon> captor = ArgumentCaptor.forClass(DataHorizon.class);
        verify(repository).upsert(captor.capture());
        assertThat(captor.getValue().earliestAvailable()).contains(LocalDate.of(2024, 4, 1));
        assertThat(captor.getValue().source()).isEqualTo(HorizonSource.NO_DATA_FEEDBACK);
    }

    @Test
    void recordNoDataDoesNotLowerExistingFloor() {
        // No-data window is entirely BELOW the floor → must not change the floor.
        DataHorizon current = horizon(LocalDate.of(2024, 7, 1), Instant.now(), HorizonSource.OBSERVED_FETCH);
        when(repository.find(any(), any(), any(), any())).thenReturn(Optional.of(current));

        service.recordNoData(BTC, EventType.PRICE,
                TimeRange.of(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 3, 31)));

        verify(repository, never()).upsert(any());
    }

    @Test
    void recordObservedFetchLowersFloor() {
        DataHorizon current = horizon(LocalDate.of(2024, 7, 1), Instant.now(), HorizonSource.PROBE);
        when(repository.find(any(), any(), any(), any())).thenReturn(Optional.of(current));

        service.recordObservedFetch(BTC, EventType.PRICE, LocalDate.of(2024, 1, 15));

        ArgumentCaptor<DataHorizon> captor = ArgumentCaptor.forClass(DataHorizon.class);
        verify(repository).upsert(captor.capture());
        assertThat(captor.getValue().earliestAvailable()).contains(LocalDate.of(2024, 1, 15));
        assertThat(captor.getValue().source()).isEqualTo(HorizonSource.OBSERVED_FETCH);
    }

    @Test
    void recordObservedFetchDoesNotRaiseFloor() {
        DataHorizon current = horizon(LocalDate.of(2024, 1, 1), Instant.now(), HorizonSource.PROBE);
        when(repository.find(any(), any(), any(), any())).thenReturn(Optional.of(current));

        // Observed a LATER date → must not move the floor up.
        service.recordObservedFetch(BTC, EventType.PRICE, LocalDate.of(2024, 6, 1));

        verify(repository, never()).upsert(any());
    }
}
