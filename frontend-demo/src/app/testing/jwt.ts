/** An unsigned test JWT; the frontend only reads the payload, the backend verifies signatures. */
export function testJwt(claims: { email: string; exp: number; iat?: number }): string {
  const encode = (value: object) =>
    btoa(JSON.stringify(value)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub: 'staff-1', ...claims })}.signature`;
}

export const inOneHour = (): number => Math.floor(Date.now() / 1000) + 3600;
