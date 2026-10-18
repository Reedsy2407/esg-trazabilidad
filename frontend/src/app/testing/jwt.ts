/** An unsigned test JWT; the frontend only reads the payload, the backend verifies signatures. */
export function testJwt(claims: {
  email: string;
  exp: number;
  iat?: number;
  /** auth-service adds ADMIN or STAFF; a token without it counts as STAFF. */
  role?: string;
  /** auth-service adds `pwdChange: true` only while the person still has to choose their own password. */
  pwdChange?: boolean;
}): string {
  const encode = (value: object) =>
    btoa(JSON.stringify(value)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub: 'staff-1', ...claims })}.signature`;
}

export const inOneHour = (): number => Math.floor(Date.now() / 1000) + 3600;
