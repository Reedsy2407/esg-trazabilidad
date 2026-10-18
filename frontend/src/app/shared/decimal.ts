import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * A decimal as the backend's numeric(p, s) column stores it: up to `integerDigits` digits, a dot,
 * up to `decimals` decimals. A dot, never a comma, as the app shows figures ("12,480.50 kg").
 * Empty is valid (`required` speaks for it). Errors: { format } or { min } / { max }.
 */
export function decimalValidator(integerDigits: number, decimals: number, min: number, max?: number): ValidatorFn {
  const pattern = new RegExp(`^\\d{1,${integerDigits}}(\\.\\d{1,${decimals}})?$`);
  return (control: AbstractControl<string>): ValidationErrors | null => {
    const value = control.value.trim();
    if (value === '') {
      return null;
    }
    if (!pattern.test(value)) {
      return { format: true };
    }
    const n = Number(value);
    if (n < min) {
      return { min: true };
    }
    return max !== undefined && n > max ? { max: true } : null;
  };
}
