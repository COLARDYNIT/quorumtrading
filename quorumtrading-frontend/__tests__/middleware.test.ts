/**
 * @jest-environment node
 *
 * Node, not jsdom: middleware runs on the edge runtime and NextRequest is built on the Web
 * `Request` global, which jsdom does not provide.
 */

/**
 * What the middleware tells someone who has no session.
 *
 * A first-time user on the self-hosted edition opens /simulations, has never signed in, and is
 * redirected to `?expired=true` — which renders the heading "Session expired" and the banner
 * "Your session expired. Sign in again to continue." Nothing expired; there was never a session.
 * On an edition the README advertises as "no sign-up, no OAuth dance", being told your session
 * expired is doubly confusing: it implies you had one, and that something went wrong.
 *
 * The middleware cannot distinguish an expired session from an absent one — it only sees the
 * missing cookie — so it must not claim to. "Sign in to continue" is true in both cases.
 */
import { NextRequest } from 'next/server';
import { middleware } from '../middleware';

function requestFor(path: string, cookies: Record<string, string> = {}): NextRequest {
  const request = new NextRequest(new URL(`http://localhost:3000${path}`));
  for (const [name, value] of Object.entries(cookies)) {
    request.cookies.set(name, value);
  }
  return request;
}

describe('middleware — unauthenticated redirects', () => {
  it('does not claim a session expired when there was never one', () => {
    const response = middleware(requestFor('/simulations'));

    const location = response.headers.get('location');
    expect(location).toBeTruthy();
    expect(location).toContain('/auth/signin');
    expect(location).not.toContain('expired=true');
  });

  it('preserves where the user was going', () => {
    const response = middleware(requestFor('/simulations/abc-123'));

    expect(response.headers.get('location')).toContain(
      `returnTo=${encodeURIComponent('/simulations/abc-123')}`
    );
  });

  it('lets an authenticated request through', () => {
    const response = middleware(requestFor('/simulations', { dev_session: 'local@quorumtrading.local' }));

    expect(response.headers.get('location')).toBeNull();
  });

  it('leaves public paths alone', () => {
    const response = middleware(requestFor('/faq'));

    expect(response.headers.get('location')).toBeNull();
  });
});
