package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BenchmarkRepository;
import com.quorumtrading.application.usecase.command.DeleteBenchmarkCommand;
import com.quorumtrading.model.entity.Benchmark;
import com.quorumtrading.model.valueobject.BenchmarkId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;

import static com.quorumtrading.application.service.TestConstants.TEST_USER_ID;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteBenchmarkServiceTest {

    @Mock
    private BenchmarkRepository benchmarkRepository;

    @Mock
    private AuthorizationService authorizationService;

    private DeleteBenchmarkService service;

    @BeforeEach
    void setUp() {
        service = new DeleteBenchmarkService(benchmarkRepository, authorizationService);
    }

    @Test
    void shouldDeleteBenchmarkSuccessfully() {
        BenchmarkId id = BenchmarkId.generate();
        Benchmark benchmark = new Benchmark(id, "Test Benchmark", Instant.now(), Instant.now().plusSeconds(3600), Instant.now(), TEST_USER_ID);

        when(benchmarkRepository.findById(id)).thenReturn(Optional.of(benchmark));
        when(benchmarkRepository.deleteById(id)).thenReturn(true);

        assertThatNoException().isThrownBy(() ->
            service.execute(new DeleteBenchmarkCommand(id, TEST_USER_ID)));

        verify(authorizationService).requireDelete(TEST_USER_ID, benchmark);
        verify(benchmarkRepository).deleteById(id);
    }

    @Test
    void shouldThrowWhenBenchmarkNotFound() {
        BenchmarkId id = BenchmarkId.generate();

        when(benchmarkRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(new DeleteBenchmarkCommand(id, TEST_USER_ID)))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining(id.toString());
    }
}
