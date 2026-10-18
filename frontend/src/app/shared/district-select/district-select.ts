import { Component, ElementRef, computed, forwardRef, input, signal, viewChild } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

import { BrowseMode } from '../browse-mode';
import { DISTRICTS, DISTRICT_AREAS, District, DistrictArea, districtByName, districtByUbigeo, foldForSearch } from '../districts';

/** One row of the listbox: a district, or "Todos los distritos" (ubigeo null) when allowed. */
interface Option {
  readonly id: string;
  readonly ubigeo: string | null;
  readonly name: string;
}

interface Group {
  readonly area: DistrictArea;
  readonly labelId: string;
  readonly label: string;
  readonly options: readonly Option[];
}

let nextId = 0;

/**
 * District picker: an editable combobox with a grouped listbox (WAI-ARIA APG "combobox with
 * list autocomplete"). Typing filters the 50 districts of Lima Metropolitana and Callao without
 * case or accents (ñ counts as n); arrows, Home/End, Enter and Escape work as the pattern says;
 * focus stays in the input and the active option is announced through aria-activedescendant.
 *
 * Holds the ubigeo (or null) as a form value. With `allowAll` it starts with "Todos los
 * distritos" (null), for list filters.
 */
@Component({
  selector: 'app-district-select',
  templateUrl: './district-select.html',
  styleUrl: './district-select.css',
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => DistrictSelect), multi: true }],
  host: { '(focusout)': 'focusOut($event)' },
})
export class DistrictSelect implements ControlValueAccessor {
  /** The input's id, so a <label for> outside names it. */
  readonly inputId = input.required<string>();
  /** Offer "Todos los distritos" (null) first: for filters, never for a required field. */
  readonly allowAll = input(false);
  /** Ids of hints and errors that describe the field. */
  readonly describedBy = input<string | null>(null);
  readonly invalid = input(false);

  private readonly uid = `district-select-${++nextId}`;
  protected readonly listboxId = `${this.uid}-listbox`;
  protected readonly allOption: Option = { id: `${this.uid}-all`, ubigeo: null, name: 'Todos los distritos' };

  protected readonly value = signal<string | null>(null);
  protected readonly query = signal('');
  protected readonly open = signal(false);
  protected readonly activeId = signal<string | null>(null);
  protected readonly disabled = signal(false);
  /** True while the text is what the person typed, not the chosen district's name. */
  private readonly typing = signal(false);
  /** A click empties the box to browse the list; leaving without choosing puts the district back. */
  private readonly browse = new BrowseMode();

  private readonly inputEl = viewChild.required<ElementRef<HTMLInputElement>>('field');
  private readonly listboxEl = viewChild<ElementRef<HTMLElement>>('listbox');

  // Replaced by the forms API on registration; until then a change has nowhere to go.
  private onChange: (value: string | null) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  protected readonly selectedName = computed(() => {
    const value = this.value();
    return value === null ? (this.allowAll() ? this.allOption.name : '') : (districtByUbigeo(value)?.name ?? '');
  });

  /** What the input shows: the typed text while filtering, otherwise the chosen district. */
  protected readonly text = computed(() => (this.typing() ? this.query() : this.browse.show(this.selectedName())));

  protected readonly groups = computed<readonly Group[]>(() => {
    const filter = this.typing() ? foldForSearch(this.query()) : '';
    return DISTRICT_AREAS.map(({ area, label }) => {
      const all = DISTRICTS.filter((d) => d.area === area);
      return {
        area,
        labelId: `${this.uid}-${area}`,
        label: `${label} (${all.length})`,
        options: all.filter((d) => filter === '' || foldForSearch(d.name).includes(filter)).map((d) => this.toOption(d)),
      };
    }).filter((g) => g.options.length > 0);
  });

  protected readonly showAll = computed(() => this.allowAll() && (!this.typing() || foldForSearch(this.query()) === ''));

  /** Every visible option, in order: what the arrow keys walk through. */
  protected readonly options = computed<readonly Option[]>(() => [
    ...(this.showAll() ? [this.allOption] : []),
    ...this.groups().flatMap((g) => g.options),
  ]);

  protected readonly matches = computed(() => {
    if (!this.open()) {
      return '';
    }
    const count = this.groups().reduce((n, g) => n + g.options.length, 0);
    return count === 0 ? 'Ningún distrito coincide.' : `${count} ${count === 1 ? 'distrito coincide' : 'distritos coinciden'}.`;
  });

  writeValue(value: string | null): void {
    this.value.set(districtByUbigeo(value) === undefined ? null : value);
    this.typing.set(false);
    this.browse.stop();
  }

  registerOnChange(fn: (value: string | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.disabled.set(disabled);
    if (disabled) {
      this.close();
    }
  }

  /** A click on the box: the shown district is hidden while browsing (the choice itself stays). */
  protected onClick(): void {
    this.browse.start(this.selectedName());
    if (!this.open()) {
      this.toggle();
    }
  }

  /** Focus by keyboard selects the text, so typing replaces it instead of continuing it. */
  protected onFocus(event: FocusEvent): void {
    (event.target as HTMLInputElement).select();
  }

  protected onInput(event: Event): void {
    this.browse.stop();
    this.query.set((event.target as HTMLInputElement).value);
    this.typing.set(true);
    this.openList(false);
    this.activeId.set(this.options()[0]?.id ?? null);
  }

  protected onKeydown(event: KeyboardEvent): void {
    const options = this.options();
    const index = options.findIndex((o) => o.id === this.activeId());
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        if (!this.open()) {
          this.openList(!event.altKey);
        } else if (!event.altKey) {
          this.activate(options[Math.min(index + 1, options.length - 1)] ?? options[0]);
        }
        return;
      case 'ArrowUp':
        event.preventDefault();
        if (!this.open()) {
          this.openList(false);
          this.activate(options[options.length - 1]);
        } else {
          this.activate(options[Math.max(index - 1, 0)] ?? options[0]);
        }
        return;
      case 'Home':
      case 'End':
        if (this.open() && options.length > 0) {
          event.preventDefault();
          this.activate(event.key === 'Home' ? options[0] : options[options.length - 1]);
        }
        return;
      case 'Enter':
        if (this.open()) {
          event.preventDefault(); // never submits the form while choosing
          const active = options[index];
          if (active !== undefined) {
            this.choose(active);
          }
        }
        return;
      case 'Escape':
        if (this.open()) {
          event.preventDefault();
          this.close();
          this.typing.set(false);
          this.browse.stop();
        } else if (this.typing()) {
          this.typing.set(false);
        }
        return;
    }
  }

  protected toggle(): void {
    if (this.disabled()) {
      return;
    }
    if (this.open()) {
      this.close();
    } else {
      this.openList(true);
    }
    this.inputEl().nativeElement.focus();
  }

  protected choose(option: Option): void {
    this.typing.set(false);
    this.browse.stop();
    this.close();
    if (option.ubigeo !== this.value()) {
      this.value.set(option.ubigeo);
      this.onChange(option.ubigeo);
    }
    this.inputEl().nativeElement.focus();
  }

  /**
   * Leaving the field: a typed name that is exactly a district chooses it; anything else goes back
   * to the chosen district, so the input never shows text that isn't the value.
   */
  protected focusOut(event: FocusEvent): void {
    const next = event.relatedTarget as Node | null;
    if (next !== null && (event.currentTarget as HTMLElement).contains(next)) {
      return;
    }
    if (this.typing()) {
      const exact = districtByName(this.query());
      if (exact !== undefined && exact.ubigeo !== this.value()) {
        this.value.set(exact.ubigeo);
        this.onChange(exact.ubigeo);
      }
      this.typing.set(false);
    }
    this.browse.stop();
    this.close();
    this.onTouched();
  }

  /** Keeps focus in the input when an option is pressed with the mouse. */
  protected keepFocus(event: MouseEvent): void {
    event.preventDefault();
  }

  private openList(activateSelected: boolean): void {
    if (this.disabled()) {
      return;
    }
    this.open.set(true);
    if (activateSelected) {
      const options = this.options();
      this.activate(options.find((o) => o.ubigeo === this.value()) ?? options[0]);
    }
  }

  private close(): void {
    this.open.set(false);
    this.activeId.set(null);
  }

  private activate(option: Option | undefined): void {
    this.activeId.set(option?.id ?? null);
    if (option !== undefined) {
      setTimeout(() => this.listboxEl()?.nativeElement.querySelector(`[id="${option.id}"]`)?.scrollIntoView?.({ block: 'nearest' }));
    }
  }

  private toOption(d: District): Option {
    return { id: `${this.uid}-${d.ubigeo}`, ubigeo: d.ubigeo, name: d.name };
  }
}
