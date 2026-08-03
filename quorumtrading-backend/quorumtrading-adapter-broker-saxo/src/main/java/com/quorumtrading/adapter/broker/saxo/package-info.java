/**
 * Saxo Bank (EU) broker adapter.
 *
 * <p>Implements the broker out-ports ({@code ConfigurableBrokerPort},
 * {@code BrokerCredentialValidationPort}) for Saxo Bank, dispatched per linked
 * {@code BrokerAccount} whose {@code brokerType} is {@code SAXO_SIM} or
 * {@code SAXO_LIVE} via the {@code BrokerRouter}.
 *
 * <p>This module is an empty scaffold (work package
 * {@code P0-saxo-broker-module-shell}); the REST clients, DTOs and adapter
 * logic land in later Saxo work packages ({@code SB1}+). Adapters must not
 * depend on other adapters (hexagonal layering, enforced by ArchUnit).
 */
package com.quorumtrading.adapter.broker.saxo;
