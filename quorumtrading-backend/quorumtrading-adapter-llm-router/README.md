# QuorumTrading LLM Router Adapter

Intelligent LLM provider routing with automatic failover and circuit breaking.

## Overview

The LLM Router provides:
- **Automatic Failover**: Routes queries through multiple providers with fallback
- **Circuit Breaker**: Uses SmallRye Fault Tolerance to skip broken providers
- **Metrics Tracking**: Records invocations, errors, and response times
- **Smart Routing**: Considers LLMConfig preferences while maintaining control

## Architecture

- **Router**: Implements `LLMPort`, discovers providers via CDI
- **Provider Discovery**: Auto-discovers all `LLMPort` implementations except itself
- **Circuit Breakers**: Each adapter has independent @CircuitBreaker annotation
- **Metrics**: In-memory tracking with periodic logging

## Configuration

```properties
# Provider order (comma-separated)
quorumtrading.llm.router.provider-order=local-llm,openrouter

# Overall LLM call timeout (seconds, default 90)
quorumtrading.llm.call-timeout-seconds=90

# Circuit breaker (global)
quarkus.fault-tolerance.global.circuit-breaker.request-volume-threshold=20
quarkus.fault-tolerance.global.circuit-breaker.failure-ratio=0.5
quarkus.fault-tolerance.global.circuit-breaker.delay=60000
quarkus.fault-tolerance.global.circuit-breaker.success-threshold=2
```

Provider metrics are logged every 5 minutes by `MetricsLogger` (fixed `@Scheduled` interval).

## Runtime Provider Selection (Self-Host)

With `quorumtrading.llm.selection.enabled=true` (self-host edition), the static provider order is replaced by an explicit runtime user selection. A call without a selection fails fast with a message pointing to `quorumtrading.llm.setup-url`, and the router never silently falls back to another provider.

## Usage

Agents automatically use the router via CDI:

```java
@Inject
LLMRouterAdapter router;

LLMConfig config = new LLMConfig(
    "mistralai/mistral-small-3.2-24b-instruct",
    0.7,
    1000,
    "openrouter"  // Optional preference
);

String response = router.query("Analyze this data", config);
```
