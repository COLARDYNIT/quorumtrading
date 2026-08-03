package com.quorumtrading.application.port.out;

import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Strategy entities.
 * Implemented by persistence adapters to provide data access.
 */
public interface StrategyRepository {
    Strategy save(Strategy strategy);
    Optional<Strategy> findById(StrategyId id);

    /**
     * Batch-find strategies by id in a single query (avoids N+1 lookups when
     * mapping simulation lists). Unknown ids are simply absent from the result;
     * order is unspecified. An empty or null collection yields an empty list.
     *
     * @param ids the strategy ids to load
     * @return the strategies found for the given ids
     */
    List<Strategy> findByIds(Collection<StrategyId> ids);

    List<Strategy> findAll();
    boolean deleteById(StrategyId id);
    int deleteAll();
    boolean existsById(StrategyId id);

    /**
     * Find all strategies owned by a specific user.
     *
     * @param ownerId the user's ID
     * @return list of strategies owned by the user
     */
    List<Strategy> findByOwnerId(UserId ownerId);

    /**
     * Find all publicly visible, active strategies.
     *
     * @return list of public strategies ordered by name
     */
    List<Strategy> findAllPublic();

    /**
     * Count the total number of strategies.
     *
     * @return total number of strategies
     */
    long count();
}
