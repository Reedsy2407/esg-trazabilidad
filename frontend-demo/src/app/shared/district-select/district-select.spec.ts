import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';

import { DistrictSelect } from './district-select';

@Component({
  imports: [ReactiveFormsModule, DistrictSelect],
  template: `
    <label for="d">Distrito</label>
    <app-district-select inputId="d" [formControl]="control" [allowAll]="allowAll" />
    <button type="button" id="after">después</button>
  `,
})
class Host {
  control = new FormControl<string | null>(null, Validators.required);
  allowAll = false;
}

describe('DistrictSelect', () => {
  let host: Host;
  let el: HTMLElement;

  const input = () => el.querySelector('#d') as HTMLInputElement;
  const listbox = () => el.querySelector('[role="listbox"]') as HTMLElement;
  const options = () => [...el.querySelectorAll('[role="option"]')] as HTMLElement[];
  const optionNames = () => options().map((o) => o.textContent?.trim());
  const active = () => el.querySelector(`[id="${input().getAttribute('aria-activedescendant') ?? 'none'}"]`)?.textContent?.trim();
  const key = (name: string, init: KeyboardEventInit = {}) => {
    input().dispatchEvent(new KeyboardEvent('keydown', { key: name, bubbles: true, cancelable: true, ...init }));
    TestBed.tick();
  };
  const type = (text: string) => {
    input().value = text;
    input().dispatchEvent(new Event('input'));
    TestBed.tick();
  };

  const create = (allowAll = false) => {
    TestBed.configureTestingModule({ imports: [Host] });
    const fixture = TestBed.createComponent(Host);
    host = fixture.componentInstance;
    host.allowAll = allowAll;
    el = fixture.nativeElement as HTMLElement;
    document.body.appendChild(el);
    TestBed.tick();
  };

  afterEach(() => el?.remove());

  it('is a combobox wired to its listbox, closed until asked', () => {
    create();
    expect(input().getAttribute('role')).toBe('combobox');
    expect(input().getAttribute('aria-autocomplete')).toBe('list');
    expect(input().getAttribute('aria-expanded')).toBe('false');
    expect(input().getAttribute('aria-controls')).toBe(listbox().id);
    expect(listbox().hidden).toBe(true);
    expect(el.querySelector('label')?.getAttribute('for')).toBe('d');
  });

  it('lists 43 + 7 districts in two labelled groups, with accents', () => {
    create();
    key('ArrowDown');
    expect(input().getAttribute('aria-expanded')).toBe('true');
    const groups = [...el.querySelectorAll('[role="group"]')];
    expect(groups.map((g) => el.querySelector(`#${g.getAttribute('aria-labelledby')}`)?.textContent?.trim())).toEqual([
      'Lima Metropolitana (43)',
      'Callao (7)',
    ]);
    expect(groups[0].querySelectorAll('[role="option"]')).toHaveLength(43);
    expect(groups[1].querySelectorAll('[role="option"]')).toHaveLength(7);
    expect(optionNames()).toContain('Rímac');
    expect(optionNames()).toContain('Breña');
  });

  it('filters without case or accents, ñ as n, and says how many match', () => {
    create();
    type('BRENA');
    expect(optionNames()).toEqual(['Breña']);
    type('san juan');
    expect(optionNames()).toEqual(['San Juan de Lurigancho', 'San Juan de Miraflores']);
    expect(el.querySelector('[role="status"]')?.textContent).toBe('2 distritos coinciden.');
    type('peru');
    expect(optionNames()).toEqual(['Mi Perú']);
    expect([...el.querySelectorAll('[role="group"]')]).toHaveLength(1); // only Callao is left
    type('zzz');
    expect(options()).toHaveLength(0);
    expect(el.textContent).toContain('Ningún distrito coincide.');
  });

  it('arrows, Home and End move the active option; Enter chooses it and closes', () => {
    create();
    key('ArrowDown');
    expect(active()).toBe('Lima');
    key('ArrowDown');
    expect(active()).toBe('Ancón');
    key('ArrowUp');
    expect(active()).toBe('Lima');
    key('End');
    expect(active()).toBe('Mi Perú');
    key('Home');
    expect(active()).toBe('Lima');
    const enter = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true });
    input().dispatchEvent(enter);
    TestBed.tick();
    expect(enter.defaultPrevented).toBe(true); // never submits the form while choosing
    expect(host.control.value).toBe('150101');
    expect(input().value).toBe('Lima');
    expect(input().getAttribute('aria-expanded')).toBe('false');
    expect(input().hasAttribute('aria-activedescendant')).toBe(false);
  });

  it('opening again marks the chosen district as selected and active', () => {
    create();
    host.control.setValue('070102');
    TestBed.tick();
    expect(input().value).toBe('Bellavista');
    key('ArrowDown');
    expect(active()).toBe('Bellavista');
    expect(el.querySelector('[aria-selected="true"]')?.textContent?.trim()).toBe('Bellavista');
  });

  it('Escape closes and puts back the chosen district; nothing changes', () => {
    create();
    host.control.setValue('150110');
    TestBed.tick();
    type('mira');
    expect(input().getAttribute('aria-expanded')).toBe('true');
    key('Escape');
    expect(input().getAttribute('aria-expanded')).toBe('false');
    expect(input().value).toBe('Comas');
    expect(host.control.value).toBe('150110');
  });

  it('clicking an option chooses it', () => {
    create();
    key('ArrowDown');
    const rimac = options().find((o) => o.textContent?.trim() === 'Rímac') as HTMLElement;
    rimac.click();
    TestBed.tick();
    expect(host.control.value).toBe('150128');
    expect(input().value).toBe('Rímac');
  });

  it('leaving the field: an exact name chooses it, other text goes back to the value, and it is touched', () => {
    create();
    type('  la perla ');
    input().dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: el.querySelector('#after') }));
    TestBed.tick();
    expect(host.control.value).toBe('070104');
    expect(input().value).toBe('La Perla');
    expect(host.control.touched).toBe(true);

    type('Surco');
    input().dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: null }));
    TestBed.tick();
    expect(host.control.value).toBe('070104');
    expect(input().value).toBe('La Perla');
  });

  it('a value outside the list is treated as none', () => {
    create();
    host.control.setValue('150201');
    TestBed.tick();
    expect(input().value).toBe('');
  });

  it('as a filter it starts with "Todos los distritos", which is null', () => {
    create(true);
    expect(input().value).toBe('Todos los distritos');
    host.control.setValue('150110');
    TestBed.tick();
    key('ArrowDown');
    key('Home');
    expect(active()).toBe('Todos los distritos');
    key('Enter');
    expect(host.control.value).toBeNull();
    expect(input().value).toBe('Todos los distritos');
  });

  it('a disabled control disables the input and the toggle', () => {
    create();
    host.control.disable();
    TestBed.tick();
    expect(input().disabled).toBe(true);
    expect((el.querySelector('button.toggle') as HTMLButtonElement).disabled).toBe(true);
  });
});
