package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.ForceEndSimulationCommand;
import com.quorumtrading.application.usecase.result.ForceEndSimulationResult;

public interface ForceEndSimulationUseCase {

    ForceEndSimulationResult execute(ForceEndSimulationCommand command);
}
