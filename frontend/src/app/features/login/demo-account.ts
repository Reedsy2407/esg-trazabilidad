/**
 * The public demonstration account: a STAFF user with fictitious data, offered on the login screen and in the README so
 * anyone can try the system in one click. The password is public on purpose and protects nothing but fictitious data.
 * To rotate it, reset the account from «Cuentas del personal» (as an ADMIN), sign in once to choose the new password,
 * and change it here and in the README (and in .gitleaks.toml, which allows exactly these two lines).
 */
export const DEMO_ACCOUNT = {
  email: 'demo@esg-trazabilidad.example',
  password: 'bev2Yp2RyZuDqwJnuB7N',
} as const;
