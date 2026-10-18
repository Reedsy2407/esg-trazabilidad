import { BrowseMode } from './browse-mode';

describe('BrowseMode', () => {
  it('shows the text until a click on a box that holds a choice', () => {
    const mode = new BrowseMode();
    expect(mode.show('Miraflores')).toBe('Miraflores');
    mode.start('Miraflores');
    expect(mode.browsing()).toBe(true);
    expect(mode.show('Miraflores')).toBe('');
  });

  it('does nothing when the box has no chosen text', () => {
    const mode = new BrowseMode();
    mode.start('');
    expect(mode.browsing()).toBe(false);
    expect(mode.show('')).toBe('');
  });

  it('stop restores the text', () => {
    const mode = new BrowseMode();
    mode.start('Lince');
    mode.stop();
    expect(mode.browsing()).toBe(false);
    expect(mode.show('Lince')).toBe('Lince');
  });
});
