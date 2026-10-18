import { TestBed } from '@angular/core/testing';

import { Logo } from './logo';

function render(inputs: Record<string, unknown> = {}): HTMLElement {
  const fixture = TestBed.createComponent(Logo);
  for (const [key, value] of Object.entries(inputs)) {
    fixture.componentRef.setInput(key, value);
  }
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('Logo', () => {
  it('is decorative by default (it sits next to the name in text): hidden from assistive technology', () => {
    const svg = render().querySelector('svg')!;
    expect(svg.getAttribute('aria-hidden')).toBe('true');
    expect(svg.hasAttribute('role')).toBe(false);
    expect(svg.hasAttribute('aria-label')).toBe(false);
  });

  it('is an image named "Trazabilidad ESG" when it is the only identification', () => {
    const svg = render({ informative: true }).querySelector('svg')!;
    expect(svg.getAttribute('role')).toBe('img');
    expect(svg.getAttribute('aria-label')).toBe('Trazabilidad ESG');
    expect(svg.hasAttribute('aria-hidden')).toBe(false);
  });

  it('draws the rounded tile in the colour and mono versions, and only the mark in the symbol version', () => {
    expect(render({ variant: 'color' }).querySelector('rect[rx]')).not.toBeNull();
    expect(render({ variant: 'mono' }).querySelector('rect[rx]')).not.toBeNull();
    const symbol = render({ variant: 'symbol' });
    expect(symbol.querySelector('rect[rx]')).toBeNull();
    expect(symbol.querySelector('svg')!.getAttribute('viewBox')).toBe('104 104 304 304');
  });

  it('keeps its size as given: square, never stretched', () => {
    const svg = render({ size: 40 }).querySelector('svg')!;
    expect(svg.getAttribute('width')).toBe('40');
    expect(svg.getAttribute('height')).toBe('40');
  });

  it('gives every instance its own mask id, so two logos on a page never share one', () => {
    const ids = [render(), render(), render()].map((el) => el.querySelector('mask')!.id);
    expect(new Set(ids).size).toBe(3);
  });
});
