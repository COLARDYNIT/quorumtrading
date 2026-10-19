/**
 * Default strategy template with YAML frontmatter.
 * Prepopulated when creating a new strategy to guide users on the format.
 */
/**
 * Default markdown body (trading guidance) shown in the wizard's strategy instructions field.
 * This is the content after the YAML frontmatter closing `---`.
 */
export const DEFAULT_MARKDOWN_BODY = `# Strategic Trading Guidance

You are the Lead Orchestrator for a multi-agent AI trading system. Your role is to synthesize recommendations from specialized agents (Technical, Fundamental, Sentiment) and make final trading decisions.

## Core Principles

1. **Risk Management First**: Never risk more than what the portfolio can withstand. Respect position limits and cash reserves.

2. **Consensus Over Confidence**: When agents disagree, favor the majority view unless a high-confidence outlier has compelling reasoning.

3. **Market Context Matters**: Consider current market conditions (bull/bear/sideways) when weighing agent recommendations.

4. **Preserve Capital**: In uncertain conditions, HOLD is a valid decision. Don't force trades.

## Decision-Making Framework

### BUY Criteria
- At least 3 agents recommend BUY with avg confidence > 0.7
- Technical indicators confirm upward momentum
- Position limits allow additional allocation
- Sufficient cash reserves available after purchase

### SELL Criteria
- At least 3 agents recommend SELL with avg confidence > 0.6
- OR sentiment turns strongly negative
- Current position exists to sell

### HOLD Criteria
- Agents are split (no clear consensus)
- Low confidence across recommendations (avg < 0.5)
- Market volatility is elevated
- Position limits would be exceeded by buying
- Insufficient cash reserves

## Special Conditions

### High Volatility
- Increase confidence threshold for BUY decisions to 0.8
- Favor HOLD unless strong consensus exists
- Give extra weight to fundamental signals

### Strong Bull Market
- Can lower BUY threshold slightly (0.65 confidence)
- Still respect position limits
- Watch for overexposure

### Bear Market
- Prioritize capital preservation
- Only BUY on very strong signals (0.9+ confidence, unanimous)
- Consider SELL more readily (0.5+ confidence sufficient)
`;

export const DEFAULT_STRATEGY_TEMPLATE = `---
# Strategy Configuration
# This frontmatter contains metadata that controls how the trading system operates.
# The markdown body below is the strategic guidance given to the Orchestrator agent.

# Version identifier for this strategy
version: v1.0

# Consensus method determines how the final trading decision is made
# Options: MAJORITY | ORCHESTRATOR_OVERRIDE
# - MAJORITY: Agent votes determine the final action directly. No orchestrator LLM call. Ties default to HOLD.
#             HOLD agents with confidence below consensus_threshold abstain (don't vote).
# - ORCHESTRATOR_OVERRIDE: An LLM orchestrator reviews all agent recommendations and makes the final decision.
consensus_method: MAJORITY

# =============================================================================
# TRADING MODE: Automatically inferred from tickers you configure
# =============================================================================
# 🌐 CRYPTO MODE (24/7 Trading):
#    - Triggered when ONLY crypto_tickers are specified
#    - Uses live_schedule.interval for continuous evaluation
#    - No market hours restrictions
#    - Example: Evaluate every hour (interval: 1h)
#
# 📈 STOCK MODE (Scheduled Trading):
#    - Triggered when stock_tickers are present
#    - Uses live_schedule for scheduled evaluation times
#    - Only trades during market hours on specified days
#    - Example: Mon-Fri at 10:00 AM and 2:00 PM ET
# =============================================================================

# --- OPTION 1: CRYPTO MODE (24/7 Trading) ---
# Uncomment this section and remove/comment out STOCK MODE section below

# crypto_tickers:
#   - BTC
#   - ETH
#   - SOL
#
# live_schedule:
#   interval: 1h    # Human-readable: 30s, 5m, 1h, 6h, 1d, 1w
#
# backtest_schedule:         # Optional — defaults to 1w
#   interval: 1w

# --- OPTION 2: STOCK MODE (Scheduled Trading) ---
# Uncomment this section and remove/comment out CRYPTO MODE section above

stock_tickers:
  - AAPL
  - MSFT
  - NVDA

# Live schedule: when to evaluate during real trading
live_schedule:
  timezone: "America/New_York"  # Market timezone (default: America/New_York)
  trading_days: [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]  # Controls frequency: [MONDAY] for weekly
  evaluation_times: ["10:00", "14:00"]  # Times to evaluate (HH:MM, must be within market hours)
  market_open: "09:30"   # Optional - defaults to 09:30 (NYSE standard)
  market_close: "16:00"  # Optional - defaults to 16:00 (NYSE standard)

# Backtest schedule: less frequent to save AI budget (optional)
# Defaults to Wednesday 10:00 if omitted
backtest_schedule:
  timezone: "America/New_York"
  trading_days: [WEDNESDAY]          # Mid-week avoids Monday/Friday anomalies
  evaluation_times: ["10:00"]        # Post-open, after volatility settles
  market_open: "09:30"
  market_close: "16:00"

# Maximum position sizes per asset (as percentage of portfolio, 0.0 - 1.0)
# Example: {"BTC": 0.3, "ETH": 0.2} = max 30% BTC, max 20% ETH
# NOTE: a BUY without an explicit quantity fills straight TO this cap (and with no entry
# for the ticker, to all cash above the reserve). It is the default order size of the
# first BUY — "scale in gradually" prose does not size orders; agents must state quantities.
position_limits:
  AAPL: 0.3
  MSFT: 0.25
  NVDA: 0.2
  BTC: 0.3
  ETH: 0.2
  SOL: 0.25

# Minimum cash to keep in reserve (as percentage of portfolio, 0.0 - 1.0)
# Example: 0.2 = always keep at least 20% in cash
reserve_cash_percentage: 0.2

# max_order_fraction: 0.15 # Optional: max fraction of portfolio value per single BUY order.
#                          # position_limits is a cap on position size, not an order size — when
#                          # the agents give no quantity, the platform sizes the order to 25% of
#                          # portfolio value unless this field says otherwise.

# position_cooldown: 14d   # Optional: skip consensus for a ticker after BUY/SELL

# agents:                    # Optional: limit which AI agents participate
#   - TECHNICAL              # Valid: TECHNICAL, FUNDAMENTAL, SENTIMENT
#   - FUNDAMENTAL            # Omit to use all 3 agents (default)
#   - SENTIMENT              # ORCHESTRATOR always runs and should not be listed

# llm_model: "mistralai/mistral-small-3.2-24b-instruct"  # Optional: pin all agents to one model
# llm_models:                # Optional: per-agent model overrides (takes precedence over llm_model)
#   TECHNICAL: "google/gemini-2.5-flash"
#   SENTIMENT: "anthropic/claude-sonnet-5.5"
---

# Strategic Trading Guidance

You are the Lead Orchestrator for a multi-agent AI trading system. Your role is to synthesize recommendations from specialized agents (Technical, Fundamental, Sentiment) and make final trading decisions.

## Core Principles

1. **Risk Management First**: Never risk more than what the portfolio can withstand. Respect position limits and cash reserves.

2. **Consensus Over Confidence**: When agents disagree, favor the majority view unless a high-confidence outlier has compelling reasoning.

3. **Market Context Matters**: Consider current market conditions (bull/bear/sideways) when weighing agent recommendations.

4. **Preserve Capital**: In uncertain conditions, HOLD is a valid decision. Don't force trades.

## Decision-Making Framework

### BUY Criteria
- At least 3 agents recommend BUY with avg confidence > 0.7
- Technical indicators confirm upward momentum
- Position limits allow additional allocation
- Sufficient cash reserves available after purchase

### SELL Criteria
- At least 3 agents recommend SELL with avg confidence > 0.6
- OR sentiment turns strongly negative
- Current position exists to sell

### HOLD Criteria
- Agents are split (no clear consensus)
- Low confidence across recommendations (avg < 0.5)
- Market volatility is elevated
- Position limits would be exceeded by buying
- Insufficient cash reserves

## Special Conditions

### High Volatility
- Increase confidence threshold for BUY decisions to 0.8
- Favor HOLD unless strong consensus exists
- Give extra weight to fundamental signals

### Strong Bull Market
- Can lower BUY threshold slightly (0.65 confidence)
- Still respect position limits
- Watch for overexposure

### Bear Market
- Prioritize capital preservation
- Only BUY on very strong signals (0.9+ confidence, unanimous)
- Consider SELL more readily (0.5+ confidence sufficient)

`;
