import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

// Routes that don't require authentication
const publicPaths = [
  '/',
  '/auth/signin',
  '/auth/signup',
  '/auth/callback',
  '/terms',
  '/privacy',
  '/blog',
  '/faq',
  '/docs',
];

// Check if path is public (starts with any public path)
function isPublicPath(pathname: string): boolean {
  return publicPaths.some(path =>
    pathname === path || pathname.startsWith(`${path}/`)
  );
}

// Check if request is a data/RSC request (not a full page navigation)
function isDataRequest(request: NextRequest): boolean {
  // RSC (React Server Component) flight requests
  if (request.nextUrl.searchParams.has('_rsc')) {
    return true;
  }

  // Check for fetch/XHR requests via headers
  const accept = request.headers.get('accept') || '';
  const isRscRequest = request.headers.get('rsc') === '1';
  const isNextRouterPrefetch = request.headers.get('next-router-prefetch') === '1';

  // If it's not accepting HTML, it's likely a data request
  if (!accept.includes('text/html') && (accept.includes('application/json') || accept.includes('text/x-component'))) {
    return true;
  }

  return isRscRequest || isNextRouterPrefetch;
}

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Allow public paths
  if (isPublicPath(pathname)) {
    return NextResponse.next();
  }

  // Allow static assets and API routes
  if (
    pathname.startsWith('/_next') ||
    pathname.startsWith('/api') ||
    pathname.includes('.')  // files with extensions
  ) {
    return NextResponse.next();
  }

  // Check for authentication cookies:
  // 1. Quarkus OIDC session cookie (q_session_<tenant>) for production
  // 2. dev_session cookie for E2E testing
  const cookies = request.cookies.getAll();
  const hasOidcSession = cookies.some(cookie => cookie.name.startsWith('q_session'));
  const hasDevSession = cookies.some(cookie => cookie.name === 'dev_session');

  if (!hasOidcSession && !hasDevSession) {
    // For data/RSC requests, let them through so the client can handle
    // the 401 error and show the session expired modal
    if (isDataRequest(request)) {
      return NextResponse.next();
    }

    // For full page navigations, send them to sign in and preserve where they were going.
    //
    // Deliberately NOT `expired=true`. All we know here is that the cookie is missing, which is
    // equally true of someone who has never signed in — and that is the common case on the
    // self-hosted edition, which the README describes as having "no sign-up, no OAuth dance".
    // Telling a first-time user their session expired implies they had one and that something
    // broke. "Sign in to continue" is true whichever it is.
    const url = request.nextUrl.clone();
    const originalPath = request.nextUrl.pathname + request.nextUrl.search;
    url.pathname = '/auth/signin';
    url.search = `?returnTo=${encodeURIComponent(originalPath)}`;
    return NextResponse.redirect(url);
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    // Match all paths except static files
    '/((?!_next/static|_next/image|favicon.ico).*)',
  ],
};
