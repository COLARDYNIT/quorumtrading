import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: 'standalone',
  compiler: {
    styledComponents: true,
  },
  async headers() {
    return [
      {
        source: '/:path*',
        headers: [
          { key: 'X-Frame-Options', value: 'DENY' },
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
          { key: 'Permissions-Policy', value: 'camera=(), microphone=(), geolocation=()' },
          ...(process.env.NODE_ENV === 'production'
            ? [{ key: 'Strict-Transport-Security', value: 'max-age=31536000; includeSubDomains' }]
            : []),
        ],
      },
    ];
  },
  async redirects() {
    return [
      {
        source: '/docs',
        destination: '/faq',
        permanent: true,
      },
      {
        source: '/brokers',
        destination: '/settings/broker-accounts',
        permanent: true,
      },
    ];
  },
  // Proxy API calls to backend in development
  // This allows cookies to be same-origin (SameSite=lax works with same-origin)
  async rewrites() {
    // Only proxy in development - production uses direct API calls
    if (process.env.NODE_ENV === 'development') {
      return [
        {
          source: '/api/v1/:path*',
          destination: 'http://localhost:8085/api/v1/:path*',
        },
      ];
    }
    return [];
  },
};

export default nextConfig;
