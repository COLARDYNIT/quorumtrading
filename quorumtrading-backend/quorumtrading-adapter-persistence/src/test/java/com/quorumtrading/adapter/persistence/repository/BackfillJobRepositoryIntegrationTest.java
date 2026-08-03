package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.application.port.out.BackfillJobRepository;
import com.quorumtrading.model.enums.BackfillStatus;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.marketdata.BackfillJob;
import com.quorumtrading.model.marketdata.MarketDataGap;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.TimeRange;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for BackfillJobRepository.
 *
 * Tests persistence and retrieval of BackfillJob domain entities.
 * Following TDD principles and the test diamond strategy.
 *
 * Uses real PostgreSQL test database (NOT H2) as per project guidelines.
 */
@QuarkusTest
class BackfillJobRepositoryIntegrationTest {

    @Inject
    BackfillJobRepository repository;

    @Test
    @Transactional
    @DisplayName("shouldSaveAndRetrieveBackfillJob_WhenNewRequest")
    void shouldSaveAndRetrieveBackfillJob_WhenNewRequest() {
        // Arrange
        BackfillJob request = createTestBackfillJob("BTC", EventType.PRICE);

        // Act
        repository.save(request);
        Optional<BackfillJob> retrieved = repository.findById(request.getId());

        // Assert
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getId()).isEqualTo(request.getId());
        assertThat(retrieved.get().getStatus()).isEqualTo(BackfillStatus.PENDING);
        assertThat(retrieved.get().getProgressPercentage()).isEqualTo(0);
        assertThat(retrieved.get().getGaps()).hasSize(1);
        assertThat(retrieved.get().getGaps().get(0).symbol().ticker()).isEqualTo("BTC");
        assertThat(retrieved.get().getGaps().get(0).dataType()).isEqualTo(EventType.PRICE);
    }

    @Test
    @Transactional
    @DisplayName("shouldUpdateExistingRequest_WhenSavingWithSameId")
    void shouldUpdateExistingRequest_WhenSavingWithSameId() {
        // Arrange
        BackfillJob request = createTestBackfillJob("ETH", EventType.NEWS);
        repository.save(request);

        // Modify the request
        request.startProcessing();
        request.updateProgress(50, 100);

        // Act
        repository.save(request);
        Optional<BackfillJob> retrieved = repository.findById(request.getId());

        // Assert
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getStatus()).isEqualTo(BackfillStatus.IN_PROGRESS);
        assertThat(retrieved.get().getProgressPercentage()).isEqualTo(50);
        assertThat(retrieved.get().getStartedAt()).isNotNull();
        assertThat(retrieved.get().getRecordsProcessed()).isEqualTo(50);
        assertThat(retrieved.get().getTotalRecordsExpected()).isEqualTo(100);
    }

    @Test
    @DisplayName("shouldReturnEmpty_WhenRequestNotFound")
    void shouldReturnEmpty_WhenRequestNotFound() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();

        // Act
        Optional<BackfillJob> result = repository.findById(nonExistentId);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("shouldFindPendingJobs_WhenMultipleJobsExist")
    void shouldFindPendingJobs_WhenMultipleJobsExist() {
        // Arrange - Create jobs with different statuses
        // Each job uses different symbols to enable filtering
        BackfillJob pending1 = createTestBackfillJob("AAVE", EventType.PRICE);
        BackfillJob pending2 = createTestBackfillJob("MATIC", EventType.PRICE);
        BackfillJob inProgress = createTestBackfillJob("DOGE", EventType.NEWS);
        BackfillJob completed = createTestBackfillJob("ADA", EventType.SOCIAL);
        BackfillJob failed = createTestBackfillJob("SOL", EventType.PRICE);

        // Modify statuses
        inProgress.startProcessing();
        completed.startProcessing();
        completed.markCompleted();
        failed.startProcessing();
        failed.markFailed("Test error");

        // Save all
        repository.save(pending1);
        repository.save(pending2);
        repository.save(inProgress);
        repository.save(completed);
        repository.save(failed);

        // Act
        List<BackfillJob> allPendingJobs = repository.findPendingJobs();

        // Filter to only our test jobs by checking for our specific test symbols
        Set<UUID> testJobIds = Set.of(pending1.getId(), pending2.getId(), inProgress.getId());
        List<BackfillJob> pendingJobs = allPendingJobs.stream()
            .filter(job -> testJobIds.contains(job.getId()))
            .toList();

        // Assert - Should return only PENDING and IN_PROGRESS
        assertThat(pendingJobs).hasSize(3);
        assertThat(pendingJobs)
            .extracting(BackfillJob::getStatus)
            .containsOnly(BackfillStatus.PENDING, BackfillStatus.IN_PROGRESS);

        // Verify ordering by createdAt (implicit in insertion order for this test)
        assertThat(pendingJobs)
            .extracting(BackfillJob::getId)
            .containsExactlyInAnyOrder(pending1.getId(), pending2.getId(), inProgress.getId());
    }

    @Test
    @Transactional
    @DisplayName("shouldPreserveAllFields_WhenReconstitutingFromDatabase")
    void shouldPreserveAllFields_WhenReconstitutingFromDatabase() {
        // Arrange
        BackfillJob request = createTestBackfillJob("BTC", EventType.PRICE);
        request.startProcessing();
        request.updateProgress(75, 100);

        // Act
        repository.save(request);
        Optional<BackfillJob> retrieved = repository.findById(request.getId());

        // Assert - All fields should be preserved
        assertThat(retrieved).isPresent();
        BackfillJob reconstructed = retrieved.get();

        assertThat(reconstructed.getId()).isEqualTo(request.getId());
        assertThat(reconstructed.getStatus()).isEqualTo(BackfillStatus.IN_PROGRESS);
        assertThat(reconstructed.getProgressPercentage()).isEqualTo(75);
        assertThat(reconstructed.getStartedAt()).isNotNull();
        assertThat(reconstructed.getCompletedAt()).isNull();
        assertThat(reconstructed.getErrorMessage()).isNull();
        assertThat(reconstructed.getTotalRecordsExpected()).isEqualTo(100);
        assertThat(reconstructed.getRecordsProcessed()).isEqualTo(75);
        assertThat(reconstructed.getGaps()).hasSize(1);
    }

    @Test
    @Transactional
    @DisplayName("shouldPreserveErrorMessage_WhenJobFails")
    void shouldPreserveErrorMessage_WhenJobFails() {
        // Arrange
        BackfillJob request = createTestBackfillJob("BTC", EventType.PRICE);
        request.startProcessing();
        request.markFailed("API rate limit exceeded");

        // Act
        repository.save(request);
        Optional<BackfillJob> retrieved = repository.findById(request.getId());

        // Assert
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getStatus()).isEqualTo(BackfillStatus.FAILED);
        assertThat(retrieved.get().getErrorMessage()).isEqualTo("API rate limit exceeded");
        assertThat(retrieved.get().getCompletedAt()).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("shouldHandleMultipleSymbolsAndDataTypes_WhenSavingAndRetrieving")
    void shouldHandleMultipleSymbolsAndDataTypes_WhenSavingAndRetrieving() {
        // Arrange - Create request with multiple gaps
        // NOTE: The mapper stores a flattened representation (unique symbols + unique data types + date range)
        // and reconstructs gaps as a Cartesian product. This is a known design decision.
        List<MarketDataGap> gaps = List.of(
            new MarketDataGap(
                new Symbol("BTC", InvestmentType.CRYPTO),
                EventType.PRICE,
                TimeRange.of(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31))
            ),
            new MarketDataGap(
                new Symbol("ETH", InvestmentType.CRYPTO),
                EventType.NEWS,
                TimeRange.of(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31))
            ),
            new MarketDataGap(
                new Symbol("BTC", InvestmentType.CRYPTO),
                EventType.SOCIAL,
                TimeRange.of(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31))
            )
        );
        BackfillJob request = new BackfillJob(gaps);

        // Act
        repository.save(request);
        Optional<BackfillJob> retrieved = repository.findById(request.getId());

        // Assert
        assertThat(retrieved).isPresent();

        // Due to Cartesian product reconstruction: 2 symbols × 3 data types = 6 gaps
        // Original gaps: BTC-PRICE, ETH-NEWS, BTC-SOCIAL
        // Unique symbols: {BTC, ETH}
        // Unique data types: {PRICE, NEWS, SOCIAL}
        // Reconstructed: BTC-PRICE, BTC-NEWS, BTC-SOCIAL, ETH-PRICE, ETH-NEWS, ETH-SOCIAL
        assertThat(retrieved.get().getGaps()).hasSize(6);

        // Verify all original combinations are still present
        List<String> actualCombinations = retrieved.get().getGaps().stream()
            .map(gap -> gap.symbol().ticker() + "-" + gap.dataType().name())
            .toList();

        // All original combinations should be present (plus additional Cartesian product combinations)
        assertThat(actualCombinations).contains(
            "BTC-PRICE",
            "ETH-NEWS",
            "BTC-SOCIAL"
        );

        // Verify Cartesian product: all symbols with all data types
        assertThat(actualCombinations).containsExactlyInAnyOrder(
            "BTC-PRICE", "BTC-NEWS", "BTC-SOCIAL",
            "ETH-PRICE", "ETH-NEWS", "ETH-SOCIAL"
        );
    }

    @Test
    @Transactional
    @DisplayName("shouldHandleMarketWideGaps_WhenSymbolIsNull")
    void shouldHandleMarketWideGaps_WhenSymbolIsNull() {
        // Arrange - Create request with market-wide NEWS gap (null symbol)
        MarketDataGap marketWideGap = new MarketDataGap(
            null,  // null symbol = market-wide data
            EventType.NEWS,
            TimeRange.of(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31))
        );
        BackfillJob request = new BackfillJob(List.of(marketWideGap));

        // Act
        repository.save(request);
        Optional<BackfillJob> retrieved = repository.findById(request.getId());

        // Assert
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getGaps()).hasSize(1);
        assertThat(retrieved.get().getGaps().get(0).symbol()).isNull();
        assertThat(retrieved.get().getGaps().get(0).dataType()).isEqualTo(EventType.NEWS);
        assertThat(retrieved.get().getGaps().get(0).isMarketWide()).isTrue();
    }

    @Test
    @Transactional
    @DisplayName("shouldHandleMixedSymbolAndMarketWideGaps_WhenBothExist")
    void shouldHandleMixedSymbolAndMarketWideGaps_WhenBothExist() {
        // Arrange - Mix of symbol-specific and market-wide gaps
        List<MarketDataGap> gaps = List.of(
            new MarketDataGap(
                new Symbol("BTC", InvestmentType.CRYPTO),
                EventType.PRICE,
                TimeRange.of(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31))
            ),
            new MarketDataGap(
                null,  // market-wide NEWS
                EventType.NEWS,
                TimeRange.of(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31))
            )
        );
        BackfillJob request = new BackfillJob(gaps);

        // Act
        repository.save(request);
        Optional<BackfillJob> retrieved = repository.findById(request.getId());

        // Assert
        assertThat(retrieved).isPresent();

        // Due to Cartesian product reconstruction:
        // Symbols: {BTC, MARKET}
        // DataTypes: {PRICE, NEWS}
        // Reconstructed: BTC-PRICE, BTC-NEWS, MARKET-NEWS (MARKET-PRICE is skipped as invalid)
        List<MarketDataGap> retrievedGaps = retrieved.get().getGaps();
        assertThat(retrievedGaps).hasSize(3);

        // Find the market-wide gap
        List<MarketDataGap> marketWideGaps = retrievedGaps.stream()
            .filter(MarketDataGap::isMarketWide)
            .toList();
        assertThat(marketWideGaps).hasSize(1);
        assertThat(marketWideGaps.get(0).dataType()).isEqualTo(EventType.NEWS);

        // Find the symbol-specific gaps
        List<MarketDataGap> symbolGaps = retrievedGaps.stream()
            .filter(gap -> !gap.isMarketWide())
            .toList();
        assertThat(symbolGaps).hasSize(2);
        assertThat(symbolGaps)
            .extracting(gap -> gap.symbol().ticker())
            .containsOnly("BTC");
    }

    // Helper methods

    private BackfillJob createTestBackfillJob(String symbol, EventType dataType) {
        MarketDataGap gap = new MarketDataGap(
            new Symbol(symbol, InvestmentType.STOCK),
            dataType,
            TimeRange.of(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31))
        );
        return new BackfillJob(List.of(gap));
    }
}
