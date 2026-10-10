import { PASSWORD_ALPHABET, PASSWORD_LENGTH, generateInitialPassword } from './initial-password';
import { durationText, sessionExpiryText } from './session-expiry';

describe('generateInitialPassword', () => {
  it('16 characters, none of the ambiguous ones', () => {
    for (let i = 0; i < 200; i++) {
      const password = generateInitialPassword();
      expect(password).toHaveLength(PASSWORD_LENGTH);
      expect(password).toMatch(/^[A-HJ-NP-Za-km-np-z2-9]{16}$/);
      expect(password).not.toMatch(/[0O1lIio]/);
    }
    expect(PASSWORD_ALPHABET).toHaveLength(55);
  });

  it('uses crypto.getRandomValues and skips the values that would bias the modulo', () => {
    const spy = vi.spyOn(crypto, 'getRandomValues');
    generateInitialPassword();
    expect(spy).toHaveBeenCalled();
    spy.mockRestore();

    const limit = Math.floor(0x1_0000_0000 / 55) * 55;
    let call = 0;
    const random = (bytes: Uint32Array) => {
      // First batch: all above the limit (rejected); then index 0 → 'A' every time.
      bytes.fill(call++ === 0 ? limit : 0);
      return bytes;
    };
    expect(generateInitialPassword(random)).toBe('A'.repeat(16));
    expect(call).toBe(2);
  });

  it('two passwords differ', () => {
    expect(generateInitialPassword()).not.toBe(generateInitialPassword());
  });
});

describe('session expiry, from the token', () => {
  const at = (iso: string) => Date.parse(iso);

  it('the time in Lima and the length from exp - iat, never an assumed hour', () => {
    const iat = at('2026-10-10T19:42:00Z'); // 14:42 in Lima
    expect(sessionExpiryText(iat + 3_600_000, iat)).toBe('Tu sesión vence a las 15:42 (dura 1 hora y no se renueva).');
    expect(sessionExpiryText(iat + 30 * 60_000, iat)).toBe('Tu sesión vence a las 15:12 (dura 30 minutos y no se renueva).');
    expect(sessionExpiryText(iat + 150 * 60_000, iat)).toBe('Tu sesión vence a las 17:12 (dura 2 horas y 30 minutos y no se renueva).');
  });

  it('without iat, only the time', () => {
    expect(sessionExpiryText(at('2026-10-10T20:42:00Z'), null)).toBe('Tu sesión vence a las 15:42 y no se renueva.');
  });

  it('durations in words', () => {
    expect(durationText(60_000)).toBe('1 minuto');
    expect(durationText(2 * 3_600_000)).toBe('2 horas');
    expect(durationText(3_660_000)).toBe('1 hora y 1 minuto');
  });
});
