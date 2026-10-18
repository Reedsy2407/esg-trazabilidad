const WEIGHTS = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2];
const PREFIXES = ['10', '15', '16', '17', '20'];

/**
 * The same local check as the backend's Ruc (shared-kernel): 11 digits, a prefix of 10, 15, 16, 17
 * or 20, and the modulo-11 check digit. It says the number is well formed, not that SUNAT has it.
 */
export function isValidRuc(value: string): boolean {
  if (!/^\d{11}$/.test(value) || !PREFIXES.includes(value.slice(0, 2))) {
    return false;
  }
  const sum = WEIGHTS.reduce((total, weight, i) => total + Number(value[i]) * weight, 0);
  const remainder = 11 - (sum % 11);
  const digit = remainder === 10 ? 0 : remainder === 11 ? 1 : remainder;
  return digit === Number(value[10]);
}
