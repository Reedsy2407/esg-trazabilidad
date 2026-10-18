// Creates the smoke-test staff account through the real "Crear una cuenta del personal" screen.
//
//   node scripts/smoke-create-user.mjs [frontend-url]
//
// Needs the e-mail already chosen (scripts/smoke-cred.ps1 -Mode Init). Opens a VISIBLE Chromium
// at the login page and waits (up to 5 minutes) for a person to sign in there: this script never
// asks for, reads or types anyone's password (the person must be an ADMIN: only an admin creates accounts). Once a session exists it fills the form with the
// stored e-mail, reads the password the SCREEN generated (the form takes none: it is made in the
// browser and shown once), pipes it straight into smoke-cred.ps1 -Mode Set (DPAPI) and signs out.
// The password is never printed, logged, passed as an argument or written to a file in clear.
import { execFileSync, spawnSync } from 'node:child_process';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const require = createRequire(join(here, '..', 'frontend', 'package.json'));
const { chromium } = require('@playwright/test');

const BASE = process.argv[2] ?? 'https://esg-frontend-egcf.onrender.com';
const ps1 = join(here, 'smoke-cred.ps1');
const powershell = (args, input) =>
  spawnSync('powershell', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ps1, ...args], { input, encoding: 'utf8' });

const email = execFileSync('powershell', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ps1, '-Mode', 'Get', '-Field', 'Email'], {
  encoding: 'utf8',
}).trim();
if (!email.startsWith('smoke-')) throw new Error('the stored e-mail does not start with smoke-');

const browser = await chromium.launch({ headless: false });
const page = await (await browser.newContext({ locale: 'es-PE' })).newPage();
try {
  await page.goto(`${BASE}/login`);
  console.log('Waiting up to 5 minutes for someone to sign in in the open window...');
  await page.waitForURL((url) => !url.pathname.endsWith('/login'), { timeout: 300_000 });
  await page.getByRole('navigation', { name: 'Secciones' }).waitFor({ timeout: 60_000 });

  await page.getByRole('link', { name: /^Mi sesión/ }).click();
  await page.getByRole('link', { name: 'Crear una cuenta del personal' }).click();
  await page.getByRole('heading', { name: 'Crear una cuenta del personal', level: 1 }).waitFor({ timeout: 60_000 });
  await page.getByLabel('Nombre completo').fill('Prueba de humo (automatica)');
  await page.getByLabel('Correo').fill(email);
  await page.getByLabel('Entiendo que la cuenta tendrá acceso completo').check();
  await page.getByRole('button', { name: 'Crear cuenta' }).click();

  await page.locator('.secret').waitFor({ timeout: 150_000 });
  await page.getByRole('button', { name: 'Mostrar' }).click();
  // The screen shows a mask until "Mostrar" takes effect: wait for the real text, and store
  // nothing unless it has the shape of a generated password (16 unambiguous letters/digits).
  await page.waitForFunction(() => {
    const code = document.querySelector('.secret code.mono');
    return !!code && /^[A-Za-z2-9]{16}$/.test((code.textContent ?? '').trim());
  }, undefined, { timeout: 15_000 });
  const password = (await page.locator('.secret code.mono').innerText()).trim();
  if (!/^[A-Za-z2-9]{16}$/.test(password)) throw new Error('the screen showed no usable password');
  const stored = powershell(['-Mode', 'Set'], password);
  if (stored.status !== 0) throw new Error('could not store the password (DPAPI)');
  console.log('Account created; password stored encrypted.');

  await page.getByRole('button', { name: /^Ya la (entregué|comprobé)/ }).click();
  await page.getByRole('link', { name: /^Mi sesión/ }).click();
  await page.getByRole('main').getByRole('button', { name: 'Cerrar sesión' }).click();
  await page.waitForURL(/\/login$/, { timeout: 30_000 });
  console.log('Signed out.');
} finally {
  await browser.close();
}
