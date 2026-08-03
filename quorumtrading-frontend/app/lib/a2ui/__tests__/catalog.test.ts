import { registerComponent, getComponent, hasComponent } from '../catalog';

describe('A2UI Catalog', () => {
  it('registers and retrieves a component', () => {
    const MockComponent = () => null;
    registerComponent('TestWidget', MockComponent as never);
    expect(hasComponent('TestWidget')).toBe(true);
    expect(getComponent('TestWidget')).toBe(MockComponent);
  });

  it('returns undefined for unregistered component', () => {
    expect(hasComponent('NonExistent')).toBe(false);
    expect(getComponent('NonExistent')).toBeUndefined();
  });

  it('overwrites component on re-registration', () => {
    const First = () => null;
    const Second = () => null;
    registerComponent('Overwrite', First as never);
    registerComponent('Overwrite', Second as never);
    expect(getComponent('Overwrite')).toBe(Second);
  });
});
