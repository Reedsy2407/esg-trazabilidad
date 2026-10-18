import { TestBed } from '@angular/core/testing';

import { Tally } from './tally';

describe('Tally', () => {
  function render(inputs: Record<string, unknown>): HTMLElement {
    const fixture = TestBed.createComponent(Tally);
    for (const [key, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(key, value);
    }
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('says the total in text for screen readers while the counting figure is hidden from them', () => {
    const el = render({ label: 'Empresas registradas', total: 14, rows: [{ label: 'Activas', value: 11 }] });
    expect(el.querySelector('.visually-hidden')!.textContent!.trim()).toBe('14');
    expect(el.querySelector('.countup')!.getAttribute('aria-hidden')).toBe('true');
    expect(el.querySelector('section')!.getAttribute('aria-label')).toBe('Empresas registradas');
  });

  it('lists the rows it is given, with the scope they refer to', () => {
    const el = render({
      label: 'Vecinos registrados',
      total: 12,
      scope: 'En esta página',
      rows: [
        { label: 'Activos', value: 11 },
        { label: 'Inactivos', value: 1 },
      ],
    });
    expect(el.querySelector('.scope')!.textContent).toBe('En esta página');
    expect(Array.from(el.querySelectorAll('.row'), (r) => r.textContent!.replace(/\s+/g, ' ').trim())).toEqual(['Activos11', 'Inactivos1']);
  });

  it('draws the 0–100 ruler only when it is given a share', () => {
    const rows = [{ label: 'A', value: 1 }];
    expect(render({ label: 'x', total: 1, rows }).querySelector('.ruler')).toBeNull();
    const withRuler = render({ label: 'x', total: 1, rows, share: 87.5 });
    expect(withRuler.querySelector('.ruler')).not.toBeNull();
  });
});
