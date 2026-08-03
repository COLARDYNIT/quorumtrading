package com.quorumtrading.model.entity;

/**
 * Marker interface for Domain-Driven Design Aggregate Roots.
 *
 * An Aggregate Root is the entry point for accessing and modifying a cluster of related entities
 * that form a consistency boundary. All external access to the aggregate must go through the root.
 *
 * <p><b>Key Responsibilities:</b></p>
 * <ul>
 *   <li>Enforce invariants across all entities within the aggregate</li>
 *   <li>Control transactional boundaries (aggregate = transaction boundary)</li>
 *   <li>Ensure consistency of all entities within the aggregate</li>
 *   <li>Provide the only entry point for modifications to child entities</li>
 * </ul>
 *
 * <p><b>Aggregate Design Guidelines:</b></p>
 * <ul>
 *   <li>Keep aggregates small - prefer smaller, focused aggregates over large ones</li>
 *   <li>Reference other aggregates by ID only, not by direct object references</li>
 *   <li>Ensure one transaction modifies one aggregate instance</li>
 *   <li>Use eventual consistency between aggregates when possible</li>
 * </ul>
 *
 * <p><b>QuorumTrading Aggregates:</b></p>
 * <ul>
 *   <li><b>Strategy</b> - Trading strategy configuration (standalone aggregate)</li>
 *   <li><b>Simulation</b> - Backtesting run with child entities: PortfolioSnapshots, LLMDecisions, ConsensusDecisions</li>
 *   <li><b>Portfolio</b> - Portfolio state with Positions (value object aggregate)</li>
 * </ul>
 *
 * @see <a href="https://martinfowler.com/bliki/DDD_Aggregate.html">Martin Fowler - DDD Aggregate</a>
 * @see <a href="https://www.vaughnvernon.com/effective-aggregate-design-part-i">Vaughn Vernon - Effective Aggregate Design</a>
 */
public interface AggregateRoot {
    // Marker interface - no methods required
    // Entities implementing this interface are recognized as aggregate roots
}
