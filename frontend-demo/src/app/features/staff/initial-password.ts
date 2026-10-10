/**
 * Letters and digits that can't be confused when read aloud or copied by hand: no 0/O/o, no
 * 1/l/I/i. 55 symbols, so 16 of them carry about 92 bits.
 */
export const PASSWORD_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789';
export const PASSWORD_LENGTH = 16;

/**
 * The initial password of a new staff account, made in the browser with crypto.getRandomValues
 * (product owner's decision: it is never typed). Rejection sampling, so every symbol is equally
 * likely. It lives only in the component's memory: never stored, logged, put in the URL or in
 * router state. `random` is for tests.
 */
export function generateInitialPassword(random: (bytes: Uint32Array) => Uint32Array = (b) => crypto.getRandomValues(b)): string {
  const n = PASSWORD_ALPHABET.length;
  const limit = Math.floor(0x1_0000_0000 / n) * n; // values at or above it would bias the modulo
  let out = '';
  while (out.length < PASSWORD_LENGTH) {
    const batch = random(new Uint32Array(PASSWORD_LENGTH));
    for (const value of batch) {
      if (value < limit && out.length < PASSWORD_LENGTH) {
        out += PASSWORD_ALPHABET[value % n];
      }
    }
  }
  return out;
}
