import '@testing-library/jest-dom'
import 'jest-styled-components'

// Mock react-markdown (ESM-only module)
jest.mock('react-markdown', () => {
  return function ReactMarkdown({ children }) {
    return children;
  };
});

// Mock remark-gfm (ESM-only module)
jest.mock('remark-gfm', () => {
  return function remarkGfm() {
    return {};
  };
});

// Mock Next.js router
jest.mock('next/navigation', () => ({
  useRouter() {
    return {
      push: jest.fn(),
      replace: jest.fn(),
      prefetch: jest.fn(),
    }
  },
  useSearchParams() {
    return new URLSearchParams()
  },
  usePathname() {
    return ''
  },
}))

// Suppress console errors in tests
global.console = {
  ...console,
  error: jest.fn(),
  warn: jest.fn(),
}
