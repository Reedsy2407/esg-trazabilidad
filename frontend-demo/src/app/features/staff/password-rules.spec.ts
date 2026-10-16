import { PASSWORD_MAX, PASSWORD_MIN, passwordProblem, passwordProblemText } from './password-rules';

describe('password rules (the same ones auth-service enforces)', () => {
  it('needs 15 to 64 characters and nothing else locally', () => {
    expect(passwordProblem('')).toBe('required');
    expect(passwordProblem('a'.repeat(PASSWORD_MIN - 1))).toBe('short');
    expect(passwordProblem('a'.repeat(PASSWORD_MIN))).toBeNull();
    expect(passwordProblem('a'.repeat(PASSWORD_MAX))).toBeNull();
    expect(passwordProblem('a'.repeat(PASSWORD_MAX + 1))).toBe('long');
  });

  it('accepts spaces and accents: no composition rules', () => {
    expect(passwordProblem('una frase con espacios y acentos: camión')).toBeNull();
    expect(passwordProblem('abcdfghjkmnpqrs')).toBeNull(); // 15 lowercase letters
  });

  it('counts a character the way the service does (NFKC, code points)', () => {
    expect(passwordProblem('é'.repeat(14))).toBe('short'); // 14 characters once normalised
    expect(passwordProblem('😀'.repeat(15))).toBeNull(); // 15 code points
  });

  it('has a message for each problem', () => {
    expect(passwordProblemText('required')).toBe('Escribe la contraseña.');
    expect(passwordProblemText('short')).toContain('al menos 15');
    expect(passwordProblemText('long')).toContain('como máximo 64');
  });
});
