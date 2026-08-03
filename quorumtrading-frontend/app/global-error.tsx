'use client';

import React from 'react';

/**
 * Global error boundary for the entire application.
 *
 * This component replaces the root layout when an unhandled error occurs,
 * so styled-components and theme providers are NOT available here.
 * All styling must be done via inline styles using raw theme values.
 */
export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <html lang="en">
      <body
        style={{
          margin: 0,
          padding: 0,
          backgroundColor: 'var(--qt-bg, #0a0e14)',
          color: 'var(--qt-fg, #e0e0e0)',
          fontFamily: "'IBM Plex Mono', 'Courier New', monospace",
        }}
      >
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            minHeight: '100vh',
            padding: '24px',
            textAlign: 'center',
            gap: '16px',
          }}
        >
          <h1
            style={{
              fontSize: '22px',
              color: 'var(--qt-info, #00d9ff)',
              margin: 0,
              letterSpacing: '0.5px',
            }}
          >
            System Error
          </h1>
          <p
            style={{
              fontSize: '18px',
              color: 'var(--qt-err, #ff3366)',
              maxWidth: '600px',
              margin: 0,
              lineHeight: 1.6,
            }}
          >
            An unexpected error occurred. The application was unable to recover
            automatically.
          </p>
          {error.digest && (
            <p
              style={{
                fontSize: '14px',
                color: 'var(--qt-muted, #7a8f84)',
                margin: 0,
              }}
            >
              Error reference: {error.digest}
            </p>
          )}
          <button
            onClick={reset}
            style={{
              marginTop: '8px',
              padding: '8px 24px',
              backgroundColor: 'transparent',
              border: '1px solid var(--qt-info, #00d9ff)',
              color: 'var(--qt-info, #00d9ff)',
              fontFamily: "'IBM Plex Mono', 'Courier New', monospace",
              fontSize: '15px',
              cursor: 'pointer',
              borderRadius: '2px',
              transition: 'all 0.2s',
            }}
            onMouseEnter={e => {
              const info = getComputedStyle(document.documentElement).getPropertyValue('--qt-info').trim() || '#00d9ff';
              const bg = getComputedStyle(document.documentElement).getPropertyValue('--qt-bg').trim() || '#0a0e14';
              e.currentTarget.style.backgroundColor = info;
              e.currentTarget.style.color = bg;
            }}
            onMouseLeave={e => {
              e.currentTarget.style.backgroundColor = 'transparent';
              e.currentTarget.style.color = getComputedStyle(document.documentElement).getPropertyValue('--qt-info').trim() || '#00d9ff';
            }}
          >
            Try Again
          </button>
        </div>
      </body>
    </html>
  );
}
