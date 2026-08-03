package com.quorumtrading.adapter.rest.dto.request;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record JoinChallengeRequest(String strategyId) {}
