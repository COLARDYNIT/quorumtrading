# QuorumTrading Style Guide

This guide explains how to create UI components that match the polished look and feel of QuorumTrading.

> **v2 design system in progress.** The codebase is mid-migration from the original mono-everywhere terminal aesthetic to a sans-chrome / mono-numerics system inspired by Bloomberg Terminal density + Linear typographic precision. New work should use the **v2 primitives** in `app/components/shared/v2/`. Existing components (AnimatedCard, SectionTitle, PageSection) still work and inherit the new theme tokens, but they are part of the older generation and will be deprecated as pages migrate.

## Design Principles

The landing page looks more professional because it follows these principles:

### 1. Purposeful Animation
Every animation tells a story. Don't animate for the sake of it.

```tsx
// GOOD: Staggered card reveals create visual flow
<AnimatedCard $delay={index * 100}>

// BAD: Random animations without purpose
animation: bounce 1s infinite;
```

### 2. Visual Hierarchy Through Scale
Large titles create focal points. Use size contrast deliberately.

```tsx
// GOOD: Clear hierarchy
<SectionTitle $size="xl">Main Heading</SectionTitle>  // 30px
<SectionTitle $size="md">Subsection</SectionTitle>    // 19px

// BAD: Everything the same size
font-size: 16px; // everywhere
```

### 3. Multi-Layer Shadows
Depth comes from layered shadows, not single heavy shadows.

```tsx
// GOOD: Premium multi-layer shadow
box-shadow: ${shadows.cardPremium};
// = 0 4px 24px rgba(0,0,0,0.4), 0 0 0 1px rgba(0,255,65,0.1)

// BAD: Single heavy shadow
box-shadow: 0 10px 30px rgba(0,0,0,0.5);
```

### 4. Subtle Text Glow
Titles get a soft glow matching their color. Never on body text.

```tsx
// GOOD: Glow on titles only
<SectionTitle $color="cyan">  // auto-applies cyan glow

// BAD: Glow on everything
text-shadow: 0 0 10px green; // on paragraphs
```

### 5. Hover Lift Effect
Cards lift up (-4px) and glow on hover. Consistent everywhere.

```tsx
// GOOD: Consistent lift + glow
&:hover {
  transform: translateY(-4px);
  box-shadow: ${shadows.cardHover};
}

// BAD: Inconsistent hover (some lift, some don't)
&:hover { opacity: 0.8; }  // different page
&:hover { scale: 1.02; }   // another page
```

### 6. Color Variety with Purpose
Use the full palette, but each color has meaning:
- **Green** (`terminalGreen`): Primary actions, success, titles
- **Cyan** (`terminalBlue`): Section headers, informational
- **Amber** (`terminalAmber`): Labels, highlights, warnings
- **Pink** (`terminalPink`): Alerts, sell signals, errors

---

## Shared Components

Always check if a shared component exists before creating custom styles.

### SectionTitle
For any page/section heading.

```tsx
import { SectionTitle } from '@/app/components/shared';

// Page title
<SectionTitle $size="xl" $color="cyan">Trading Strategies</SectionTitle>

// Dashboard section
<SectionTitle $size="md">Active Simulations</SectionTitle>

// Centered (like landing page)
<SectionTitle $size="lg" $color="cyan" $centered>The Council Decides</SectionTitle>
```

| Prop | Values | When to Use |
|------|--------|-------------|
| `$size` | `sm`, `md`, `lg`, `xl` | `xl` for page titles, `md` for sections |
| `$color` | `primary`, `cyan`, `amber` | `cyan` for major sections, `primary` for subsections |
| `$centered` | boolean | Landing/marketing pages only |

### AnimatedCard
For any card that should have entrance animation and hover effects.

```tsx
import { AnimatedCard, CardHeader, CardContent } from '@/app/components/shared';

// Basic card with staggered animation
{items.map((item, i) => (
  <AnimatedCard key={item.id} $delay={i * 100}>
    <CardContent>{item.name}</CardContent>
  </AnimatedCard>
))}

// Premium card (hero sections)
<AnimatedCard $premium $accentColor={agent.color}>
  <CardHeader $color={agent.color}>{agent.name}</CardHeader>
  <CardContent>...</CardContent>
</AnimatedCard>
```

| Prop | When to Use |
|------|-------------|
| `$delay` | Always pass `index * 100` for lists |
| `$premium` | Hero sections, featured content |
| `$accentColor` | When card has a semantic color (agent cards) |
| `$static` | Data-dense dashboards where hover would be distracting |

### PageSection
For full-width page sections.

```tsx
import { PageSection, SectionContainer } from '@/app/components/shared';

// Landing page style (gradient + border)
<PageSection $gradient $bordered>
  <SectionContainer>
    <SectionTitle>...</SectionTitle>
  </SectionContainer>
</PageSection>

// Dashboard style (plain)
<PageSection $size="md">
  ...
</PageSection>
```

---

## Animation Presets

Import from `@/app/styles/animations`:

```tsx
import { fadeInUp, shadows, textShadows } from '@/app/styles/animations';
```

### Keyframes
| Animation | Use Case |
|-----------|----------|
| `fadeIn` | Simple opacity fade |
| `fadeInUp` | Cards, sections entering viewport |
| `slideInLeft/Right` | Sidebars, panels |
| `pulse` | Live indicators, loading states |
| `glowPulse` | Featured/highlighted elements |

### Shadows
| Preset | Use Case |
|--------|----------|
| `shadows.card` | Default cards |
| `shadows.cardPremium` | Hero cards, featured content |
| `shadows.cardHover` | Hover state for all cards |

### Text Shadows
| Preset | Use Case |
|--------|----------|
| `textShadows.primary` | Green titles |
| `textShadows.cyan` | Section headers |
| `textShadows.amber` | Labels, highlights |

---

## Creating New Components

Follow this checklist:

### 1. Check for existing components
```
app/components/shared/     # Reusable UI
app/components/terminal/   # Terminal-themed primitives
```

### 2. Use theme tokens (never hardcode)
```tsx
// GOOD
color: ${p => p.theme.colors.terminalGreen};
padding: ${p => p.theme.spacing.md};

// BAD
color: #00ff41;
padding: 16px;
```

### 3. Apply the standard entrance + hover convention
Entrance `0.5s ease-out` (`fadeInUp`, staggered `index * 100`ms for lists), hover `-4px` lift + `shadows.cardHover` over `0.25s ease`. The canonical snippet lives in [Quick Reference](#quick-reference) — copy it rather than re-deriving the timings.

### 4. Add text-shadow to headings only
```tsx
// For custom headings not using SectionTitle
text-shadow: ${textShadows.primary};
```

---

## Anti-Patterns to Avoid

| Don't | Do Instead |
|-------|------------|
| Hardcode colors (`#00ff41`) | Use theme (`theme.colors.terminalGreen`) |
| Different hover behaviors per page | Use `shadows.cardHover` + `-4px` lift |
| Heavy single shadows | Use `shadows.cardPremium` (multi-layer) |
| Text glow on body text | Only on `SectionTitle` or headings |
| Random animation timing | Use `0.5s ease-out` for entrance, `0.25s ease` for hover |
| Scanlines/flicker effects | Disabled in theme - too retro |

---

## Quick Reference

```tsx
// Standard page setup
import { SectionTitle, AnimatedCard, CardContent } from '@/app/components/shared';
import { fadeInUp, shadows } from '@/app/styles/animations';

// Page title
<SectionTitle $size="xl">My Page</SectionTitle>

// Card grid with animations
{items.map((item, i) => (
  <AnimatedCard key={item.id} $delay={i * 100}>
    <CardContent>{item.content}</CardContent>
  </AnimatedCard>
))}

// Custom styled component matching L&F
const MyComponent = styled.div`
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.md};
  box-shadow: ${shadows.card};

  animation: ${fadeInUp} 0.5s ease-out both;
  transition: transform 0.25s ease, box-shadow 0.25s ease;

  &:hover {
    transform: translateY(-4px);
    box-shadow: ${shadows.cardHover};
  }
`;
```

---

## Design Token Inventory

This is a faithful inventory of every token defined in `app/styles/theme/` and `app/styles/animations.ts` as of this writing. Source of truth lives in the code; this section is for tools and people that read Markdown.

### Color palette (dark theme — `colors-dark.ts`, mirrored as the default in `colors.ts`)

| Token | Hex | Role |
|---|---|---|
| `background` | `#0a0e14` | Page background |
| `foreground` | `#e6e6e6` | Default body text (neutral — was matrix green pre-v2) |
| `terminalGreen` | `#00ff41` | Primary terminal green |
| `terminalBlue` | `#00d9ff` | Cyan; section headers |
| `terminalAmber` | `#ffb000` | Warnings, labels |
| `terminalPink` | `#ff3366` | Errors, negative values |
| `terminalMint` | `#06ffa5` | Positive values |
| `primary` / `secondary` / `tertiary` | `#00ff41` / `#007a1f` / `#003d0f` | Brand green ramp |
| `brand` / `brandHover` / `brandPressed` / `brandTint` | `#00ff41` / `#33ff66` / `#00cc34` / `rgba(0,255,65,0.08)` | Identity + primary CTA only |
| `success` / `warning` / `error` / `info` | `#06ffa5` / `#ffb000` / `#ff3366` / `#00d9ff` | Semantic |
| `surface` / `surfaceElevated` / `surfaceDark` | `#141a23` / `#1e2632` / `#1a1a1a` | Card / elevated card / modal |
| `border` / `borderHighlight` / `borderSubtle` | `#2a3344` / `#00d9ff` / `#1a2030` | Borders |
| `hover` / `active` / `disabled` | `rgba(0,255,65,0.10)` / `rgba(0,255,65,0.20)` / `#4a5562` | Interactive states |
| `disabledBg` / `disabledFg` | `#1a2030` / `#4a5562` | Disabled controls |
| `focusRing` | `#00d9ff` | Global `:focus-visible` ring |
| `text` / `textPrimary` / `textSecondary` | `#e6e6e6` / `#e6e6e6` / `#cccccc` | Text stack (neutral greys) |
| `textMuted` / `textDimmed` / `textSubtle` | `#8a98a4` / `#5a6a76` / `#4a5562` | De-emphasized text |
| `fgStrong` / `fgMuted` / `fgDimmed` / `fgSubtle` / `fgInverse` | `#ffffff` / `#8a98a4` / `#5a6a76` / `#4a5562` / `#0a0e14` | Neutral fg hierarchy (v2) |
| `textInverse` / `textWhite` / `textLight` | `#0a0e14` / `#ffffff` / `#cccccc` | Inversed / readable / detail |

**Domain palettes**

| Group | Tokens |
|---|---|
| Agent (analysis) | `technical #00ff41` · `fundamental #ffb000` · `sentiment #00d9ff` |
| Agent (research) | `newsSentiment #00d4ff` · `strategyDesigner #00ff41` · `viabilityAssessor #ffd700` · `technicalAnalyst #00ff41` · `fundamentalAnalyst #ffb000` · `sentimentAnalyst #00d9ff` |
| Trading action | `buy #06ffa5` · `sell #ff3366` · `hold #ffb000` · `neutral #8a98a4` |
| Asset class | `crypto #00cc33` · `stocks #4ecdc4` |
| Aliases | `danger = error` · `positive = #06ffa5` · `negative = #ff3366` |

**Chart palette (`colors.chart`)**

- Series: `line1 #00ff41` · `line2 #00d9ff` · `line3 #ffb000` · `line4 #ff3366` · `line5 #06ffa5` · `line6 #ff6b9d` · `line7 #ffd700` · `line8 #8a2be2`
- Structure: `grid #1a2332` · `axis #4a5568`
- Tooltip: `background rgba(10,14,20,0.98)` · `border #00d9ff` · `text #ffffff` · `label #00d9ff`

**Medals (`colors.medals`)** — gold / silver / bronze, each as `{ primary, secondary, text }`.
**Coverage (`colors.coverage`)** — full `#00ff41` · high `#ffd700` · medium `#ff8c00` · low `#ff006e` · none `#1a1a1a`.
**Score (`colors.score`)** — high / medium / low, each as `{ text, background, border }`.

**Light theme** — defined in `colors-light.ts`, palette differs significantly from dark. There is no documented rule for which token-level rebrandings happen between modes; treat as a separate palette mapping.

### Typography (`typography.ts`)

| Family | Stack |
|---|---|
| `fontFamily.sans` (default body, headings, chrome, prose) | `'IBM Plex Sans', system-ui, -apple-system, 'Segoe UI', Helvetica, Arial, sans-serif` |
| `fontFamily.mono` (numerics, IDs, timestamps, code — opt-in) | `'IBM Plex Mono', 'SF Mono', 'Courier New', Courier, monospace` |
| `fontFamily.display` | Alias of `fontFamily.sans` |

`html { font-size: 100% }` — the browser default (16px on most setups). Lets user-zoom + accessibility prefs scale `rem`-based values naturally. Body sizing is driven by the `fontSize.*` tokens below, not by `rem`.

| `fontSize.*` | px | Used for |
|---|---|---|
| `xs` | 12 | micro-labels, eyebrows |
| `sm` | 13 | buttons, chips, form labels |
| `base` | 15 | body, inputs, p, h5 |
| `lg` | 17 | h4 |
| `xl` | 19 | h3, page titles |
| `2xl` | 24 | h2 |
| `3xl` | 30 | h1 |
| `4xl` | 44 | hero |

The scale was bumped one notch in May 2026. Base sits at 15px — the sweet spot between dense-tool convention (Bloomberg/Linear at 14px) and general-consumer (16–17px). Mono numeric ledger cells stay tight at hardcoded 12.5–13.5px on call sites; the token scale is for chrome only.

| Other | Values |
|---|---|
| `lineHeight` | `tight 1.2` · `normal 1.4` · `relaxed 1.6` |
| `letterSpacing` | `tight -0.5px` · `normal 0.5px` · `wide 1px` |
| `fontWeight` | `normal 400` · `medium 500` · `semibold 600` · `bold 700` |

**Global element rules** (from `GlobalStyles.tsx`)
- `body` uses `fontFamily.sans`. `.mono` / `.tabular` opt children into mono.
- `h1`–`h6` use `fontFamily.sans`, `fontWeight.semibold`, `letterSpacing: -0.005em`, sentence case. Brand color is **not** applied to chrome headings.
- `a` defaults to `colors.info`, hovers to `colors.foreground`, transition `transition.fast`.
- `code, pre` use `colors.hover` bg, `colors.border` 1px border, `borderRadius.sm`.
- `:focus-visible` paints a 2px cyan ring (`colors.focusRing`) with 2px offset so focus doesn't compete with brand.

### `SectionTitle` size → font-size mapping

| `$size` | Desktop (≥769px) | Mobile (≤768px) |
|---|---|---|
| `sm` | `fontSize.lg` (17px) | `fontSize.base` (15px) |
| `md` | `fontSize.xl` (19px) | `fontSize.lg` (17px) |
| `lg` *(default)* | `fontSize.2xl` (24px) | `fontSize.xl` (19px) |
| `xl` | `fontSize.3xl` (30px) | `fontSize.2xl` (24px) |

Letter-spacing is `0.1em`, weight `600`, `text-transform: uppercase`, bottom margin `spacing.lg` (desktop) / `spacing.md` (mobile).

### Spacing scale (`index.ts`)

8px base. `2xs 2px` · `xs 4px` · `sm 8px` · `md 16px` · `lg 24px` · `xl 32px` · `2xl 48px` · `3xl 64px`.

### Border radius

`none 0` · `sm 2px` · `md 4px` · `lg 8px` · `label 2px`.

Labels — pills, tags, badges, row actions — sit on `borderRadius.label` as of **v0.4.0**. The
round-pill exception is retired: "bracket-y, not bubble-y" now applies to labels too. The one
round survivor is the **switch track**, which hardcodes its own radius because it is a control,
not a label, and the roundness is what makes it read as throwable.

There is no `full`/pill token, deliberately — reaching for one is the bug the v0.4.0 label
overhaul removed. Circles (dots, avatars) use raw `50%` where needed.

### Label tint stops

`labelMix.border 42%` · `labelMix.bg 7%`.

On a label, **`color` is the only property a kind may set**. Border and fill both derive from
`currentColor` via `color-mix` at these two ratios. The census that triggered v0.4.0 found six
hand-rolled rgba alphas doing this job, each baking in the *dark* theme's hue — so the light
palette could not retint them. Two tokens replace all six, and the light theme now retints the
entire label family by swapping hue tokens alone.

Consume them through `labelBorder` / `labelFill` in `app/styles/labels.ts`, never by
re-deriving the strings. The older `alpha()` helper in `theme/alpha.ts` still exists for
non-label tints (chart bands, panel washes) where there is no `currentColor` to mix from.

### Z-index layers

`dropdown 1000` · `modal 2000` · `tooltip 3000` · `notification 4000`.

### Transitions

`fast 150ms ease` · `normal 250ms ease` · `slow 350ms ease`.

### Shadows (`GlobalStyles.tsx` CSS variables, surfaced via `shadows.*`)

| Token | Definition (dark theme) |
|---|---|
| `shadows.card` | `0 4px 12px rgba(0,0,0,0.2)` |
| `shadows.cardPremium` | `0 4px 24px rgba(0,0,0,0.4), 0 0 0 1px rgba(primary,0.1)` |
| `shadows.cardHover` | `0 8px 32px rgba(0,0,0,0.4), 0 0 0 1px rgba(primary,0.2)` |
| `shadows.glow(color)` | `0 0 20px {color}33` |

Light-theme variants reduce the rgba black alpha (e.g. `0.4 → 0.12`).

### Text shadows

| Token | Definition |
|---|---|
| `textShadows.primary` | `0 0 2px rgba(primary, 0.4)` |
| `textShadows.cyan` | `0 0 2px rgba(terminalBlue, 0.4)` |
| `textShadows.amber` | `0 0 2px rgba(terminalAmber, 0.4)` |
| `textShadows.none` | `none` |

`--qt-text-glow` `0 0 1px rgba(primary,0.3)` and `--qt-heading-glow` `0 0 2px / 0 0 4px` are also defined globally.

### Effects (`effects.ts`) — current state

| Token | Status | Output |
|---|---|---|
| `scanlines` | **No-op** (kept for back-compat). Retired as too retro. | — |
| `flicker` | **No-op** (kept for back-compat). Retired as too distracting. | — |
| `textGlow` | Active | `text-shadow: var(--qt-text-glow)` |
| `headingGlow` | Active | `text-shadow: var(--qt-heading-glow)` |
| `screenCurvature` | Active | `border-radius: 4px; box-shadow: 0 2px 8px rgba(0,0,0,0.3)` |
| `boxGlow` | Active (refined) | `0 1px 3px rgba(0,0,0,0.3), 0 4px 12px rgba(0,0,0,0.15)` |
| `borderGlow` | Active | `border-color: primary; box-shadow: var(--qt-border-glow-shadow)` |
| `cardElevation` | Active | `0 1px 2px rgba(0,0,0,0.2), 0 2px 8px rgba(0,0,0,0.1)` |
| `focusRing` | Active | `outline: 2px solid primary; outline-offset: 2px` |

### Animations (`animations.ts`)

**Entrance**: `fadeIn` · `fadeInUp` (20px) · `fadeInDown` (20px) · `slideInLeft` (-20px) · `slideInRight` (20px).
**Loops**: `pulse` (opacity 1↔0.6) · `blink` (1↔0, 50% duty) · `glowPulse` (animates `box-shadow` via `--qt-glow-*` vars).
**Convention**: `0.5s ease-out` for entrance, `0.25s ease` for hover.
**Helper**: `staggeredFadeIn(delayIncrement)` — applies `fadeInUp` to `:nth-child(1..8)` with the increment in seconds. Caps at 8 children.
**Hover helpers**: `cardHoverEffect` (-4px lift + `cardHover` shadow) · `cardHoverWithColor(color)` (-4px lift + accent border + 20% accent glow).

### Responsive breakpoints (in-code, NOT yet a formal token)

In use across the codebase: `480px`, `640px`, `768px`, `968px`. They are repeated as raw `@media` values; there is no `theme.breakpoints.*` map yet.

### Scrollbar (global)

`12px × 12px`, track = `background` over `border`, thumb = `secondary` with `primary` border, hover thumb = `primary`. Selection = `active` background with `foreground` text.

---

## v2 Design System

### Tokens (changed from v1)

This table is a change-log explaining *what moved and why* in the v2 redesign. For current values, the [Design Token Inventory](#design-token-inventory) above (and ultimately the code in `app/styles/theme/`) is the source of truth.

| Area | What changed |
|---|---|
| **Body font** | `IBM Plex Sans` (added) is the default for body, chrome, prose. `IBM Plex Mono` is reserved for data, tickers, prices, IDs, timestamps, code blocks — anything tabular or copy-paste-worthy. Opt in to mono via `.mono` / `.tabular` utility classes or `font-family: theme.typography.fontFamily.mono`. |
| **`fontSize.base`** | Reduced from **18px → 14px** at v2 launch, then bumped one notch in May 2026 to **15px** (users were zooming to 125%). Current scale: `xs 12 · sm 13 · base 15 · lg 17 · xl 19 · 2xl 24 · 3xl 30 · 4xl 44` — see the Typography inventory above. |
| **Foreground** | `foreground / text / textPrimary` switched from `#00ff41` matrix green to `#e6e6e6` neutral. Chrome (headings, panel titles, body) uses greys, never the brand. |
| **Brand color** | New `colors.brand` (= `#00ff41` dark / `#065a1f` light) + `brandHover`, `brandPressed`, `brandTint`. **Reserved for identity, primary CTA, active sidebar item — 3 uses per screen, max.** Do not paint chrome (headings, panel titles, body) in `--brand`. |
| **`action.buy`** | Desaturated from bright `#00ff41` to mint `#06ffa5` so it visually separates from the new `--brand`. |
| **Surfaces** | Re-stacked with ~6% lightness gaps for visible elevation: `background #0a0e14` → `surface #141a23` → `surfaceElevated #1e2632`. |
| **Borders** | `border` darkened/saturated to `#2a3344` (now visible against `surface`). `borderSubtle` is the hairline `#1a2030`. |
| **Focus** | Global `:focus-visible { outline: 2px solid cyan; outline-offset: 2px }` set in `GlobalStyles.tsx`. `colors.focusRing = #00d9ff` — cyan, not brand, so focus is unambiguous. |
| **Heading rules** | `h1..h6` use **sans, weight 600**, color `fgStrong`, **no uppercase**, no glow, letter-spacing `-0.005em`. (Was: mono, bold, uppercase, glow.) |
| **Neutral fg stack** | Added: `fgStrong #ffffff` · `fgMuted #8a98a4` · `fgDimmed #5a6a76` · `fgSubtle #4a5562` · `fgInverse #0a0e14`. Use these for text hierarchy instead of color hues. |
| **Labels (v0.4.0, Aug 2026)** | Unified `StatusPill` / `Tag` / `Badge` / `Switch` / `RowAction` family. One radius (2px — the round-pill exception is retired), uppercase mono at 11px/10px, two `color-mix` tint stops replacing six rgba alphas, a dot-motion axis, and solid fill reserved for `<StatusPill money>`. `StatusPill` changed most: it was round and sans, and is now 2px and mono. See *The label family* below. |

### v2 Shared Components (`app/components/shared/v2/`)

The building blocks for the new design language. Always prefer these over hand-rolling new chrome.

| Component | Use for |
|---|---|
| `Panel` | Card container with header (title + count slot + action slot) and body. Replaces ad-hoc `surface + border + h3` patterns. |
| `SimHeader` | Page-level header pattern: title + ticker + status pills + KPI strip. |
| `KpiStrip` | Horizontal strip of 4–6 KPIs with label + value (mono, tabular-nums) + optional delta. Used in `SimHeader`. |
| `StatusPill` | **Process state.** Leading dot; kinds are `color`-only. Props: `kind`, `still`, `money`, `paperToned`, `compact`. |
| `Tag` | **Categorical metadata.** No dot. Props: `hue`, `id` (verbatim identifiers), `compact`. |
| `Badge` | **Counts.** Deliberately neutral — a count reports how many, never how urgent. |
| `Switch` | **The one switch.** Real `button[role=switch]`; requires a `label` accessible name. `lg` = 36×20. |
| `RowAction` / `TextAction` | **Row-level action buttons.** `RowAction` ships the leading `›` verb marker CSS-side; `TextAction` is the boxless tertiary variant. |
| `ActionPill` | BUY / SELL / HOLD inline tag for decision tables. Supports `override` badge. Despite the name this is a `Tag` species, not a `RowAction` — see below. |
| `VoteChip` / `VoteChipRow` | Tech / Fund / Sent ✓/✕ chip for agent vote rows in decision history; `VoteChipRow` lays out a row of them. |
| `AgentCard` | Per-agent stat card: name dot, calls, accuracy, contribution, optional 12-vote bar histogram. Used in the agent council grid. |
| `HedgedValue` | Em-dash + amber Δ-sup placeholder for fields the backend hasn't shipped yet. Mandatory for backend-gap fields — see `docs/CAPABILITIES.md`. |

Import via the barrel: `import { Panel, SimHeader, KpiStrip, StatusPill, Tag, Badge, Switch, RowAction, TextAction, ActionPill, VoteChip, VoteChipRow, AgentCard, HedgedValue } from '@/app/components/shared/v2';`

### The label family (v0.4.0)

**One species per job.** Before reaching for a new chip, decide which of these it is — the Aug
2026 census found 24 species doing these five jobs, and every new one made the next reader
guess.

| Species | Job | Tell |
|---|---|---|
| `StatusPill` | what a *process* is doing | has a dot |
| `Tag` | what something *is* (category, identifier) | no dot |
| `Badge` | how many | a number |
| `RowAction` / `TextAction` | something you can *click* | leading `›`, or a dotted underline |
| `Switch` | something you can *throw* | round track |

**The rules, all enforced by tests in `__tests__/labels.test.tsx`:**

- **One radius.** `borderRadius.label` (2px). Switch tracks are the only round shape left.
- **Uppercase mono.** `fontFamily.mono`, `fontSize.label` (11px) or `fontSize.labelSm` (10px),
  `text-transform: uppercase`. The census found two fonts and eight sizes between 9px and 12px;
  these are the only two sizes a label may use.
- **Identifiers render verbatim.** Emails, tickers, symbols, model names and key prefixes use
  `<Tag id>`. Never case-transform an identifier: it makes the value wrong to copy, and hides
  whether the casing shown is the casing stored.
- **`color` is the only per-kind property.** See *Label tint stops* above.
- **Motion means work.** The dot animates only for `live` / `simulating` / `waiting` / `syncing`.
  A pulsing dot is a claim that something is in flight; pass `still` to withdraw the claim on a
  live-kinded pill that is not currently progressing.
- **Solid fill is reserved for real money** (`<StatusPill kind="live" money>`). It is the only
  solid label in the system. If solid meant anything else, a paper run and a funded live run
  would look alike at a glance — which is the single mistake the pill exists to prevent. Paper
  gets `paperToned` instead.

**Two named exceptions, both deliberate:**

- `ActionPill` is a decision **tag** (BUY / SELL / HOLD), not the DS `action-pill` button
  species. The button is `RowAction`. The name is kept because every decision table imports it;
  two different things wearing one name was the collision the census was called to end, so the
  *button* took the unambiguous name.
- `ActionPill`'s `skip` kind keeps a **dashed** border. SKIP is the engine failing to decide,
  not a decision — colour alone was not enough, because these rows used to render as amber HOLD
  pills and a run that decided nothing looked like a run that chose to hold.

### Component-state contract (v2)

Every interactive component must honor **rest, hover, focus, active, disabled, loading**. The canonical patterns live in `GlobalStyles.tsx`. Key rules:

- **Focus** is global via `:focus-visible` — don't override with `outline: none` without a replacement.
- **Disabled** uses `colors.disabledBg` / `colors.disabledFg`, cursor `not-allowed`, `pointer-events: none`.
- **Loading** *(proposed, not yet implemented anywhere)*: `data-loading="true"` on buttons → transparent label + centered spinner; or a `Skeleton` shimmer for content.

### Migration notes

- Pages and components that read `theme.colors.foreground` / `theme.colors.text` will silently become **neutral grey** instead of matrix green. If a specific element must stay phosphor green, switch it explicitly to `theme.colors.brand`.
- `font-size` values driven by `theme.typography.fontSize.base` shrank 18px → 15px (14px at v2 launch, bumped May 2026). Audit any place that hand-sized something to be "the same as base"; it may now look small.
- `theme.colors.action.buy` is now mint; if you rely on it being identical to `terminalGreen`, that no longer holds.
- The first page to fully use v2 chrome is **Simulation Detail** (`SimulationDetailHeader.tsx`). Other pages still render their old chrome but now inherit the new tokens; visual mismatch during the migration is expected.

---

## Known Gaps (not yet systematized)

Areas where the guide is silent today. These are open design questions, not solved patterns — listed so they're explicit when iterating on the system.

- **Charts.** No conventions for recharts treatment: grid line weight/color (only `chart.grid` color is defined), axis label density and rotation, tick formatting per data type, tooltip layout/copy, legend placement, motion when data updates, empty/no-data state, log-scale toggle UX, multi-series ordering, value labels on points.
- **Data tables.** No conventions for: row density tiers (comfortable/compact/dense), sticky headers, column alignment rules (numerics right, identifiers mono-left), sortable headers visual, row hover/select states, expandable rows, pagination vs infinite scroll, sticky first column, footer totals, csv/export affordances.
- **Forms.** No documented patterns for: input default/hover/focus/error/disabled states, label placement, helper and error copy slot, inline validation timing, segmented controls, multi-select, file upload, range slider, autocompletes, form footer (Cancel/Save) alignment, destructive confirmations.
- **State affordances.** No canonical empty state, loading state (skeleton vs spinner choice rule), error state, or success state components.
- **Density gradient.** No rule for which surfaces are airy (marketing, blog, FAQ) vs dense (trader screens, dashboards, simulation detail). Currently spacing is similar across both contexts.
- **Monospace stragglers.** The sans-chrome / mono-data rule is documented (Typography inventory, Global element rules, v2 Tokens) and `GlobalStyles.tsx` enforces it for `body` and `h1..h6`, but legacy components still hardcode mono headings (e.g. `SectionTitle.tsx`), and nothing enforces the `.mono` / `.tabular` opt-in on older pages.
- **Card weight hierarchy.** `AnimatedCard` offers `$premium` vs default — two tiers. No documented third tier for dense row-style cards (e.g., list entries in `challenges/` or `simulations/`).
- **Hover discipline.** `-4px translateY` is the universal hover. There is no documented exception or alternative for surfaces where the lift is wrong (dense tables, list rows, navigation items).
- **Light theme rules.** `colors-light.ts` exists but the guide does not specify which color decisions should change per mode (e.g., glow intensity, surface tint, accent saturation).
- **Modals, dialogs, drawers.** Z-index layer `modal: 2000` is defined; no component, sizing, or layout conventions documented.
- **Toasts / notifications.** `react-hot-toast` is in the dependency tree; styling, placement, copy length, and dismissal rules are not in the guide.
- **Tooltips outside charts.** Z-index `tooltip: 3000` exists; no component or trigger convention.
- **Iconography.** No icon set, sizing scale, or stroke-weight convention documented.
- **Navigation patterns.** No conventions for top nav vs side nav, breadcrumbs, page tabs, sticky headers, scroll progress.
- **Numeric / currency formatting.** No shared formatter component for prices, percentages, deltas (with up/down color + arrow), or large numbers (`1.2M` vs `1,200,000`).
- **Breakpoints.** Used in code at 480/640/768/968 but not formalized in `theme.spacing` or `theme.breakpoints`.
- **Retired effects.** `scanlines` and `flicker` are kept as no-ops for back-compat. Decide whether to fully remove or keep the back-compat seams.
