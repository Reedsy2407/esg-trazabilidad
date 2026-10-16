import { signal } from '@angular/core';

/**
 * "Clear on click, restore on leave" for a combobox that already holds a choice.
 *
 * Clicking a box that shows a chosen value empties what it shows, so the whole list can be browsed
 * (and typing starts from nothing) without erasing the choice: it only counts as changed once
 * another option is picked or the person types. Leaving the box without choosing restores the
 * chosen text. Every combobox in the app uses this one rule so they behave alike.
 *
 * It holds one flag and no text: the component keeps its own text and choice, asks
 * {@link show} what to put in the input, and calls {@link stop} when a choice is made, typing
 * starts or focus leaves.
 */
export class BrowseMode {
  private readonly active = signal(false);

  /** True while the input is shown empty to browse the list. */
  readonly browsing = this.active.asReadonly();

  /** A click (or a tap) on the box. Does nothing when there is no chosen text to clear. */
  start(chosenText: string): void {
    if (chosenText !== '') {
      this.active.set(true);
    }
  }

  /** A choice was made, the person started typing, or focus left the box: the input shows its own text again. */
  stop(): void {
    this.active.set(false);
  }

  /** What the input shows: nothing while browsing, the given text otherwise. */
  show(text: string): string {
    return this.active() ? '' : text;
  }
}
