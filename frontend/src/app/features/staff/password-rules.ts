/**
 * The password rules auth-service enforces (AUTH-007), repeated here so the form can say what is
 * wrong before sending. NIST SP 800-63B for a password that is the only authenticator: 15 to 64
 * characters, any character (spaces and accents too), no composition rules. The service also
 * refuses common passwords and ones built from the email; only it can say that (AUTH-007).
 */
export const PASSWORD_MIN = 15;
export const PASSWORD_MAX = 64;

export const PASSWORD_RULES_TEXT =
  'Entre 15 y 64 caracteres, sin reglas de letras o símbolos: una frase larga con espacios sirve. ' +
  'No puede ser una contraseña común ni incluir tu correo.';

export type PasswordProblem = 'required' | 'short' | 'long';

/** What is wrong with the length, or null. Counts characters the way the service does (code points). */
export function passwordProblem(password: string): PasswordProblem | null {
  if (password === '') {
    return 'required';
  }
  const length = [...password.normalize('NFKC')].length;
  if (length < PASSWORD_MIN) {
    return 'short';
  }
  return length > PASSWORD_MAX ? 'long' : null;
}

export function passwordProblemText(problem: PasswordProblem): string {
  switch (problem) {
    case 'required':
      return 'Escribe la contraseña.';
    case 'short':
      return `Debe tener al menos ${PASSWORD_MIN} caracteres.`;
    case 'long':
      return `Debe tener como máximo ${PASSWORD_MAX} caracteres.`;
  }
}
