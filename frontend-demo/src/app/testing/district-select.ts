/**
 * Chooses a district in an app-district-select the way a person does with the keyboard: types
 * part of the name (the list filters) and presses Enter on the first match.
 */
export function pickDistrict(root: HTMLElement, inputId: string, text: string): void {
  const input = root.querySelector(`#${inputId}`) as HTMLInputElement;
  input.focus();
  input.value = text;
  input.dispatchEvent(new Event('input'));
  input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
}
