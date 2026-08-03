package com.quorumtrading.model.orchestration;

/**
 * Configuration for an individual agent.
 * Specifies LLM model and parameters for agent execution.
 */
public class AgentConfig {
    private String role;  // String representation of AgentRole
    private String llmModel;
    private double temperature;
    private int maxTokens;

    public AgentConfig() {}

    public AgentConfig(String role, String llmModel, double temperature, int maxTokens) {
        this.role = role;
        this.llmModel = llmModel;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    // Getters and setters
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getLlmModel() { return llmModel; }
    public void setLlmModel(String llmModel) { this.llmModel = llmModel; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
}
