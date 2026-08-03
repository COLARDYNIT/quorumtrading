package com.quorumtrading.model.exception;

import com.quorumtrading.model.enums.AgentRole;
import com.quorumtrading.model.enums.LlmProvider;
import com.quorumtrading.model.valueobject.ModelCandidate;

import java.util.List;

/**
 * A model was pinned that the active engine will not serve.
 *
 * <p>The old rejection was a flat string that explained <em>how</em> to find valid ids but contained
 * none — so an MCP client, an LLM that could have self-corrected in the same turn, had to spend a
 * round-trip discovering them, and a UI had nothing to render. This carries the answer with the
 * refusal.</p>
 *
 * <p>Built in the use case so every driving adapter inherits it; adapters only render. MCP puts the
 * candidates inline in the error string, REST repeats the same text in {@code detail} and adds the
 * list as a {@code validModels} RFC 7807 extension member. A contract test holds the two renderings
 * in step, since either alone is a bug that shows up on only one surface.</p>
 *
 * <p>Extends {@link IllegalArgumentException} so the existing 400-mapping keeps working for any
 * adapter that has not been taught the richer shape.</p>
 */
public class UnservableModelPinException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final String pinnedId;
    private final transient AgentRole agentRole;
    private final transient LlmProvider engine;
    private final transient List<ModelCandidate> candidates;
    private final String fullListingHint;

    public UnservableModelPinException(String pinnedId,
                                       AgentRole agentRole,
                                       LlmProvider engine,
                                       List<ModelCandidate> candidates,
                                       String fullListingHint) {
        super(buildMessage(pinnedId, agentRole, engine, candidates, fullListingHint));
        this.pinnedId = pinnedId;
        this.agentRole = agentRole;
        this.engine = engine;
        this.candidates = candidates == null ? List.of() : List.copyOf(candidates);
        this.fullListingHint = fullListingHint;
    }

    /**
     * The one rendering both adapters share. Naming the engine matters as much as naming the model:
     * "unknown model" on a deployment running Ollama used to send users hunting through OpenRouter's
     * catalogue for an id that could never have worked there.
     */
    private static String buildMessage(String pinnedId, AgentRole agentRole, LlmProvider engine,
                                       List<ModelCandidate> candidates, String fullListingHint) {
        StringBuilder message = new StringBuilder("Unknown LLM model: ").append(pinnedId).append(". ");
        if (agentRole != null) {
            message.append("Pinned for the ").append(agentRole.name()).append(" agent. ");
        }
        message.append("The active LLM engine (")
                .append(engine == null ? "not selected" : engine.name())
                .append(") does not advertise this id. ");

        if (candidates == null || candidates.isEmpty()) {
            message.append("It advertises no models at all right now, so no alternative can be offered.");
        } else {
            message.append("Models it will serve: ");
            message.append(String.join(", ", candidates.stream().map(ModelCandidate::describe).toList()));
            message.append('.');
        }
        if (fullListingHint != null && !fullListingHint.isBlank()) {
            message.append(' ').append(fullListingHint);
        }
        return message.toString();
    }

    public String getPinnedId() {
        return pinnedId;
    }

    /** The agent this pin was scoped to, or {@code null} for a strategy-global pin. */
    public AgentRole getAgentRole() {
        return agentRole;
    }

    public LlmProvider getEngine() {
        return engine;
    }

    public List<ModelCandidate> getCandidates() {
        return candidates;
    }

    /** Where to find the complete list when the candidates are a curated subset. */
    public String getFullListingHint() {
        return fullListingHint;
    }
}
