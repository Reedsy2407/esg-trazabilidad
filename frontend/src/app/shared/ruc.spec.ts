import { isValidRuc } from './ruc';

describe('isValidRuc', () => {
  it('accepts well-formed RUCs', () => {
    expect(isValidRuc('20131312955')).toBe(true);
    expect(isValidRuc('20131380951')).toBe(true);
  });

  it('refuses a wrong check digit, a wrong prefix, a wrong length and non-digits', () => {
    expect(isValidRuc('20131312950')).toBe(false);
    expect(isValidRuc('30131312955')).toBe(false);
    expect(isValidRuc('2013131295')).toBe(false);
    expect(isValidRuc('2013131295a')).toBe(false);
    expect(isValidRuc('')).toBe(false);
  });
});
