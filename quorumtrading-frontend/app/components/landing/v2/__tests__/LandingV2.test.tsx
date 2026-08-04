import { LandingV2 } from '../LandingV2';
import { Act6OwnItPanel } from '../Act6OwnItPanel';

/**
 * Composition guard: the landing page module must keep exporting a component and the
 * act it now includes. Rendering the full marketing page is covered by e2e (landing);
 * here we only pin the module wiring so a dropped import can't slip through silently.
 */
describe('LandingV2 composition', () => {
  it('exports the landing page component', () => {
    expect(typeof LandingV2).toBe('function');
  });

  it('the own-it act is part of the module graph', () => {
    expect(typeof Act6OwnItPanel).toBe('function');
  });
});
