import SimulationDetailPage from '../page';

/**
 * Module-level smoke test: the detail page is a 1,000-line client component whose module
 * scope (styled components, hook imports) must stay importable — a bad import or a
 * module-scope throw breaks the route at build time. Rendering it needs the full
 * provider stack; importing it needs nothing, and catches the same class of breakage
 * the 6,000-line dead-code purge could have introduced.
 */
describe('simulation detail page module', () => {
  it('exports a page component', () => {
    expect(typeof SimulationDetailPage).toBe('function');
  });
});
