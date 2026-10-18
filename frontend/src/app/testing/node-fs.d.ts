declare module 'node:fs' {
  export function readFileSync(path: string, encoding: 'utf-8'): string;
}
