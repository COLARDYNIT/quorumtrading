package com.quorumtrading.bootstrap;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import org.jboss.logging.Logger;

/**
 * Main application class for QuorumTrading.
 * This class serves as the entry point for the Quarkus application.
 */
@QuarkusMain
public class QuorumTradingApplication implements QuarkusApplication {

    private static final Logger LOG = Logger.getLogger(QuorumTradingApplication.class);

    public static void main(String... args) {
        Quarkus.run(QuorumTradingApplication.class, args);
    }

    @Override
    public int run(String... args) {
        LOG.info("╔══════════════════════════════════════════════════════════════╗");
        LOG.info("║                                                              ║");
        LOG.info("║                  QuorumTrading Backend                       ║");
        LOG.info("║          Multi-LLM Trading Consensus Platform                ║");
        LOG.info("║                                                              ║");
        LOG.info("╚══════════════════════════════════════════════════════════════╝");
        LOG.info("");
        LOG.info("QuorumTrading application started successfully!");
        LOG.info("API: http://localhost:8080/api/v1");
        LOG.info("Swagger UI: http://localhost:8080/q/swagger-ui");
        LOG.info("Health: http://localhost:8080/q/health");
        LOG.info("");

        Quarkus.waitForExit();
        return 0;
    }
}
