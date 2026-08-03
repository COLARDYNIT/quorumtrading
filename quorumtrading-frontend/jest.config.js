// eslint-disable-next-line @typescript-eslint/no-require-imports
const nextJest = require('next/jest')

const createJestConfig = nextJest({
  // Provide the path to your Next.js app to load next.config.js and .env files in your test environment
  dir: './',
})

// Add any custom config to be passed to Jest
const customJestConfig = {
  setupFilesAfterEnv: ['<rootDir>/jest.setup.js'],
  testEnvironment: 'jest-environment-jsdom',
  moduleNameMapper: {
    '^@/(.*)$': '<rootDir>/$1',
  },
  testMatch: [
    '**/__tests__/**/*.[jt]s?(x)',
    '**/?(*.)+(spec|test).[jt]s?(x)'
  ],
  testPathIgnorePatterns: [
    '/node_modules/',
    '/e2e/',
    '\\.integration\\.spec\\.(ts|tsx)$',
    '/test-components/',
  ],
  // Transform ESM modules that Jest doesn't handle by default
  transformIgnorePatterns: [
    '/node_modules/(?!(react-markdown|remark-.*|unified|bail|is-plain-obj|trough|vfile.*|unist-.*|mdast-.*|micromark.*|decode-named-character-reference|character-entities|property-information|hast-.*|space-separated-tokens|comma-separated-tokens|html-void-elements|ccount|escape-string-regexp|markdown-table|trim-lines|devlop|zwitch)/)',
  ],
  collectCoverageFrom: [
    'app/**/*.{js,jsx,ts,tsx}',
    '!app/**/*.d.ts',
    '!app/**/*.stories.{js,jsx,ts,tsx}',
    '!app/**/index.ts',
  ],
  // Coverage RATCHET, not a target: gates are set just below the measured baseline
  // (2026-07-05: statements 5.29, branches 4.55, functions 2.32, lines 5.33) so coverage can
  // only go up. Raise these numbers whenever new tests land — never lower them.
  coverageThreshold: {
    global: {
      statements: 5,
      branches: 4,
      functions: 2,
      lines: 5,
    },
  },
}

// createJestConfig is exported this way to ensure that next/jest can load the Next.js config which is async
module.exports = createJestConfig(customJestConfig)
