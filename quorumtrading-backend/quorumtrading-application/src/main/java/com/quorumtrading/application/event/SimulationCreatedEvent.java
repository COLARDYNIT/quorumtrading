package com.quorumtrading.application.event;

import com.quorumtrading.model.valueobject.SimulationId;

/**
 * Application event fired when a simulation is created and ready to start.
 * This event enables async non-blocking startup of simulations.
 */
public record SimulationCreatedEvent(SimulationId simulationId) {
}
