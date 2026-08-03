package com.quorumtrading.adapter.llm.local;

import com.quorumtrading.model.valueobject.ModelEffort;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Executor for the {@code opencode} CLI.
 *
 * <p>The one local CLI that natively speaks {@code provider/model}, which makes it a genuine bridge
 * between the hosted and local namespaces: an OpenRouter-style id — including the ones this
 * platform ships as defaults — can be handed to it unchanged, where every other CLI here takes a
 * private alias vocabulary.</p>
 *
 * <p>Verified flag surface, read from {@code opencode run --help} on <b>1.18.10</b> (2026-08-03):</p>
 * <ul>
 *   <li>{@code -m, --model <provider/model>}</li>
 *   <li>{@code --variant <level>} — provider-specific reasoning effort (e.g. high, max, minimal)</li>
 *   <li>{@code --dir <path>} — directory to run in</li>
 *   <li>{@code --agent <name>} — which agent persona to run as</li>
 *   <li>{@code --auto} — auto-approve permissions that are not explicitly denied (its own help
 *       calls this "dangerous!")</li>
 *   <li>{@code --format json} — raw JSON events</li>
 * </ul>
 *
 * <p>It advertises <b>no</b> catalogue. Which {@code provider/model} pairs work depends entirely on
 * which providers the user has configured in opencode itself, and this platform cannot see that.
 * Guessing would let a user pin an id the tool rejects, failing mid-backtest instead of at save
 * time — so the honest answer is "cannot enumerate", which leaves the pin to the user.</p>
 *
 * <h2>Why the working directory is pinned</h2>
 *
 * <p>opencode discovers project context from its working directory. Left to inherit the JVM's cwd
 * it reads whatever repository the backend happens to be started from and prepends it to every
 * trading decision. Measured on 1.18.10, same model and same nine-word prompt, the only difference
 * being the directory:</p>
 *
 * <table border="1">
 *   <caption>Input tokens by working directory</caption>
 *   <tr><th>Working directory</th><th>Input tokens</th></tr>
 *   <tr><td>the repository root (inherited cwd)</td><td>13,800</td></tr>
 *   <tr><td>an empty scratch directory ({@code --dir})</td><td>6,230</td></tr>
 * </table>
 *
 * <p>That is <b>7,570 input tokens of repository context on every single call</b>, paid on every
 * agent of every trading day of every backtest, for context that has nothing to do with the
 * decision. Running in the repository also made opencode take a git snapshot of the working tree,
 * which is a side effect on the user's checkout that an LLM call has no business having.</p>
 *
 * <p>Both {@code --dir} and {@link ProcessBuilder#directory(java.io.File)} are set. They are not
 * redundant: the flag is what opencode uses to resolve project config, while the process's own cwd
 * is what any tool the model runs would inherit.</p>
 */
@ApplicationScoped
public class OpencodeExecutor implements CLIExecutor {

    private static final Logger LOG = Logger.getLogger(OpencodeExecutor.class);
    private static final String TOOL_NAME = "opencode";
    private static final long TIMEOUT_SECONDS = 120;

    /**
     * Directory opencode runs in. Empty means "a scratch directory under the system temp dir",
     * which is the isolating default; it is never the JVM's cwd.
     */
    @ConfigProperty(name = "quorumtrading.llm.local.opencode.working-dir")
    Optional<String> configuredWorkingDir;

    /**
     * Agent persona, passed as {@code --agent}. Empty means the flag is omitted and opencode uses
     * its own default, which is {@code build} — a coding assistant.
     *
     * <p>There is deliberately no default here. Naming an agent that the user has not created
     * makes every call fail, and this platform must not write into {@code ~/.config/opencode/} to
     * create one. See LLM_CONFIGURATION.md for the file a user creates to get a trading persona.</p>
     */
    @ConfigProperty(name = "quorumtrading.llm.local.opencode.agent")
    Optional<String> configuredAgent;

    /**
     * Whether to pass {@code --auto}.
     *
     * <p>Off by default, and that is a considered choice rather than caution. Two unattended calls
     * with stdin at EOF completed normally <em>without</em> it, so it is not what unblocks an
     * unattended run — closing stdin is. Turning it on auto-approves whatever tools the configured
     * agent can reach, and opencode loads MCP servers from the user's global config, so on a
     * developer machine that can include this platform's own MCP tools. Auto-approving a trading
     * agent's tool calls is not a default anyone should inherit silently.</p>
     */
    @ConfigProperty(name = "quorumtrading.llm.local.opencode.auto-approve", defaultValue = "false")
    boolean autoApprove;

    @Override
    public String execute(String prompt) throws CLIExecutionException {
        return execute(prompt, null, null);
    }

    @Override
    public String execute(String prompt, String model, ModelEffort effort) throws CLIExecutionException {
        if (prompt == null) {
            throw new CLIExecutionException("Prompt cannot be null");
        }
        if (prompt.isEmpty()) {
            throw new CLIExecutionException("Prompt cannot be empty");
        }

        String workingDir = resolveWorkingDir();
        String agent = configuredAgent == null ? null : configuredAgent.filter(a -> !a.isBlank()).orElse(null);

        LOG.debugf("Executing opencode with model %s, agent %s, dir %s, auto-approve %s "
                        + "(prompt length: %d characters)",
                model, agent, workingDir, autoApprove, prompt.length());

        ProcessBuilder processBuilder =
                new ProcessBuilder(buildCommand(prompt, model, effort, workingDir, agent, autoApprove));
        processBuilder.redirectErrorStream(false);
        if (workingDir != null) {
            processBuilder.directory(Paths.get(workingDir).toFile());
        }

        return runProcess(processBuilder);
    }

    /** Seam over the child process — see {@code QwenExecutor#runProcess} for why it exists. */
    String runProcess(ProcessBuilder processBuilder) throws CLIExecutionException {
        return CliProcessRunner.run(processBuilder, TIMEOUT_SECONDS, "Opencode");
    }

    /**
     * The exact argv handed to {@code opencode}.
     *
     * <p>Extracted so the flag logic can be asserted without starting a process. No model means no
     * flag: opencode then uses whatever the user configured as its default, which is a better
     * answer than a model id this platform invented. The same rule governs every optional flag
     * here — an absent setting omits the flag rather than inventing a value for it.</p>
     */
    static List<String> buildCommand(String prompt, String model, ModelEffort effort) {
        return buildCommand(prompt, model, effort, null, null, false);
    }

    static List<String> buildCommand(String prompt, String model, ModelEffort effort,
                                     String workingDir, String agent, boolean autoApprove) {
        List<String> command = new ArrayList<>(List.of(TOOL_NAME, "run", "--format", "json"));
        if (model != null && !model.isBlank()) {
            command.add("--model");
            command.add(model.trim());
        }
        if (effort != null) {
            command.add("--variant");
            command.add(effort.flagValue());
        }
        if (workingDir != null && !workingDir.isBlank()) {
            command.add("--dir");
            command.add(workingDir.trim());
        }
        if (agent != null && !agent.isBlank()) {
            command.add("--agent");
            command.add(agent.trim());
        }
        if (autoApprove) {
            command.add("--auto");
        }
        command.add(prompt);
        return command;
    }

    /**
     * The directory opencode will run in.
     *
     * <p>Falls back to a scratch directory under the system temp dir rather than to the JVM's cwd:
     * inheriting the cwd is the defect this method exists to prevent. A stable path rather than a
     * fresh temp directory per call, so opencode's own session state does not accumulate one
     * directory per LLM call across a backtest.</p>
     */
    String resolveWorkingDir() {
        String configured = configuredWorkingDir == null
                ? null
                : configuredWorkingDir.filter(dir -> !dir.isBlank()).orElse(null);
        Path dir = configured != null
                ? Paths.get(configured.trim())
                : Paths.get(System.getProperty("java.io.tmpdir"), "quorumtrading-opencode");
        try {
            Files.createDirectories(dir);
            return dir.toString();
        } catch (IOException e) {
            // Better to run without isolation than not at all, but say so plainly: every call from
            // here on carries whatever context the JVM's cwd happens to contain.
            LOG.warnf("Could not create opencode working directory %s (%s) - falling back to the "
                    + "JVM working directory, which will leak its project context into prompts",
                    dir, e.getMessage());
            return null;
        }
    }

    /** {@code --variant} is verified present; the accepted levels are provider-specific. */
    @Override
    public boolean supportsEffort() {
        return true;
    }

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public boolean isInstalled() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(TOOL_NAME, "--version");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // Close stdin here too. A version probe that hangs would stall the fallback chain
            // before any model is ever reached.
            CliProcessRunner.consume(process, 5, "Opencode");
            return true;
        } catch (IOException e) {
            LOG.debugf("Opencode is not installed or not in PATH: %s", e.getMessage());
            return false;
        } catch (CLIExecutionException e) {
            LOG.debugf("Opencode version check failed: %s", e.getMessage());
            return false;
        }
    }
}
