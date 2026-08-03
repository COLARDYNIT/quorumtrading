import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";
import sonarjs from "eslint-plugin-sonarjs";

// SonarSource's own JS/TS smell rules, which is the part of SonarQube we actually wanted:
// cognitive complexity, duplicated branches, dead stores, redundant conditionals, unused
// collections. Same analyser, no server, no licence, and it runs on forked PRs because it needs
// no token — which a hosted scanner cannot do, since GitHub withholds secrets from fork builds.
//
// Every rule is forced to "warn" rather than left at the recommended "error". The suite reports
// far more than can be fixed in one pass, and a wall of errors would only get switched off.
// Warnings are capped instead: `npm run lint` fails above --max-warnings, so the existing count
// is frozen and any new smell breaks the build. Ratchet the cap down as they are fixed; promote
// individual rules to "error" once their count reaches zero.
const sonarjsAsWarnings = Object.fromEntries(
  Object.entries(sonarjs.configs.recommended.rules).map(([rule, level]) => {
    const severity = Array.isArray(level) ? level[0] : level;
    if (severity === "off") return [rule, level];
    return [rule, Array.isArray(level) ? ["warn", ...level.slice(1)] : "warn"];
  }),
);

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  {
    ...sonarjs.configs.recommended,
    rules: sonarjsAsWarnings,
  },
  // Pin the React version instead of letting eslint-plugin-react auto-detect it.
  //
  // Not a preference — without this, `npm run lint` does not run at all. eslint-config-next
  // still pulls eslint-plugin-react 7.37.5, whose version auto-detection calls the deprecated
  // `context.getFilename()`; ESLint 10 removed it, so every rule that consults the React version
  // dies on load with "TypeError: Error while loading rule 'react/display-name':
  // contextOrFilename.getFilename is not a function" and eslint exits 2 having linted nothing.
  //
  // An explicit version string short-circuits detectReactVersion() entirely (see
  // eslint-plugin-react/lib/util/version.js getReactVersionFromContext), so the broken code path
  // is never reached. Fixing it here rather than with a dependency override keeps the lockfile —
  // which Dependabot owns — untouched. Remove once eslint-config-next ships a plugin release
  // that supports ESLint 10; keep in sync with the `react` version in package.json.
  {
    settings: {
      react: { version: "19.2" },
    },
  },
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
    // Jest's generated HTML report — third-party bundled JS, not ours to lint.
    "coverage/**",
  ]),
  // Rule overrides for React 19 compiler - some patterns are valid but flagged
  {
    rules: {
      // setState in useEffect for modal form resets is a valid pattern
      "react-hooks/set-state-in-effect": "warn",
      // window.location for navigation (we use router where possible)
      "react-hooks/immutability": "warn",
      // Date constructors in render are often necessary
      "react-hooks/purity": "warn",
      // Components in useMemo for recharts tooltips is valid
      "react-hooks/static-components": "warn",
      // Allow any for external library types that aren't well-typed
      "@typescript-eslint/no-explicit-any": "warn",
    },
  },
]);

export default eslintConfig;
