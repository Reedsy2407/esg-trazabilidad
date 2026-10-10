import { BrowserContext, Page, expect, test } from '@playwright/test';

import { BackendOptions, EMAIL } from './backend';
import { WIDTHS, checkA11y, expectNoSidewaysScroll, signedIn } from './session';

const NEW = { fullName: 'Luis Ramos Huamán', email: 'luis.ramos@asociacion.pe' };

async function openForm(page: Page, context: BrowserContext, options: BackendOptions = {}) {
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  const writes: NonNullable<BackendOptions['writes']> = [];
  const console: string[] = [];
  page.on('console', (message) => console.push(message.text()));
  page.on('pageerror', (error) => console.push(String(error)));
  const unmocked = await signedIn(page, '/empresas', { ...options, writes });
  // Mi sesión is reached from the email in the top bar; no sixth section in the nav.
  await page.getByRole('link', { name: `Mi sesión: ${EMAIL}` }).click();
  await expect(page.getByRole('heading', { name: 'Mi sesión', level: 1 })).toBeVisible();
  await page.getByRole('link', { name: 'Crear una cuenta del personal' }).click();
  await expect(page.getByRole('heading', { name: 'Crear una cuenta del personal', level: 1 })).toBeVisible();
  return { writes, console, unmocked };
}

async function fillAndCreate(page: Page) {
  await page.getByLabel('Nombre completo').fill(NEW.fullName);
  await page.getByLabel('Correo').fill(NEW.email);
  await page.getByLabel('Entiendo que la cuenta tendrá acceso completo').check();
  await page.getByRole('button', { name: 'Crear cuenta' }).click();
}

/** Everywhere the password must never be: storage, URL, router state, console, DOM attributes. */
async function expectNowhere(page: Page, password: string, console: string[]) {
  const places = await page.evaluate(() => ({
    storage: [...Object.keys(sessionStorage).map((k) => `${k}=${sessionStorage.getItem(k)}`), ...Object.keys(localStorage).map((k) => `${k}=${localStorage.getItem(k)}`)].join('\n'),
    url: location.href,
    state: JSON.stringify(history.state),
    attributes: [...document.querySelectorAll('*')].flatMap((n) => [...n.attributes].map((a) => a.value)).join('\n'),
  }));
  expect(places.storage).not.toContain(password);
  expect(places.url).not.toContain(password);
  expect(places.state).not.toContain(password);
  expect(places.attributes).not.toContain(password);
  expect(console.join('\n')).not.toContain(password);
}

test('Mi sesión: the account from /auth/me, and the expiry derived from the token (here, 30 minutes)', async ({ page }) => {
  const unmocked = await signedIn(page, '/empresas', { tokenLifetimeSeconds: 1800 });
  await page.getByRole('link', { name: `Mi sesión: ${EMAIL}` }).click();
  await expect(page.getByText(/^Tu sesión vence a las \d{2}:\d{2} \(dura 30 minutos y no se renueva\)\.$/)).toBeVisible();
  await expect(page.getByText('Ana Paredes Quispe')).toBeVisible();
  await expect(page.getByText('14/08/2026')).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Secciones' }).getByRole('link')).toHaveCount(5);
  // The token itself is never on the page.
  const token = await page.evaluate(() => JSON.parse(sessionStorage.getItem('esg.session') ?? '{}').token as string);
  expect(await page.content()).not.toContain(token);
  await page.getByRole('main').getByRole('button', { name: 'Cerrar sesión' }).click();
  await expect(page).toHaveURL(/\/login$/);
  expect(unmocked).toEqual([]);
});

test('create an account: the generated password is shown on request, copied, handed over, and never stored anywhere', async ({ page, context }) => {
  const { writes, console, unmocked } = await openForm(page, context);
  expect(await page.locator('input[type="password"]').count()).toBe(0);
  await expect(page.locator('form')).toHaveAttribute('autocomplete', 'off');

  await fillAndCreate(page);
  await expect(page.getByRole('heading', { name: `Cuenta creada para ${NEW.email}` })).toBeFocused();
  expect(writes).toHaveLength(1);
  const { password, ...rest } = writes[0].body as { password: string; email: string; fullName: string };
  expect(rest).toEqual(NEW);
  expect(password).toMatch(/^[A-HJ-NP-Za-km-np-z2-9]{16}$/);

  // Hidden until asked for.
  await expect(page.getByText(password)).toHaveCount(0);
  await page.getByRole('button', { name: 'Mostrar' }).click();
  await expect(page.locator('.secret code')).toHaveText(password);
  await page.getByRole('button', { name: 'Copiar' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Contraseña copiada.' })).toBeAttached();
  expect(await page.evaluate(() => navigator.clipboard.readText())).toBe(password);
  await expectNowhere(page, password, console);

  // The checks below can fail: a planted attribute and a logged probe are both caught.
  await page.evaluate((p) => document.body.setAttribute('data-probe', p), password);
  await expect(expectNowhere(page, password, console)).rejects.toThrow();
  await page.evaluate(() => document.body.removeAttribute('data-probe'));
  await page.evaluate(() => console.log('sonda-de-consola'));
  await expect.poll(() => console.join('\n')).toContain('sonda-de-consola');

  await page.getByRole('button', { name: 'Ya la entregué' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'La contraseña se borró de esta pantalla.' })).toBeAttached();
  // The clipboard this screen filled is overwritten when the password is dropped.
  await expect.poll(() => page.evaluate(() => navigator.clipboard.readText())).toBe('');
  expect(await page.content()).not.toContain(password);
  await expectNowhere(page, password, console);
  expect(unmocked).toEqual([]);
});

test('an unknown outcome keeps the password and retries with the same one; AUTH-002 then only says an account exists', async ({ page, context }) => {
  const { writes, console, unmocked } = await openForm(page, context, { staffFailures: ['applied'] });
  await fillAndCreate(page);
  await expect(page.getByRole('heading', { name: 'No pudimos confirmar si la cuenta se creó' })).toBeFocused();
  await page.getByRole('button', { name: 'Mostrar' }).click();
  const password = (writes[0].body as { password: string }).password;
  await expect(page.locator('.secret code')).toHaveText(password);

  await page.getByLabel('Entiendo que la cuenta tendrá acceso completo').check();
  await page.getByRole('button', { name: 'Volver a intentar con los mismos datos' }).click();
  // An account exists, but the screen can't know it is the one it created: it says so and asks to check.
  await expect(page.getByRole('heading', { name: `Ya existe una cuenta con el correo ${NEW.email}` })).toBeVisible();
  await expect(page.getByText(/compruébalo iniciando sesión con ese correo y esta contraseña en una ventana privada/)).toBeVisible();
  // "Descartar" asks in this state too.
  await page.getByRole('button', { name: 'Descartar', exact: true }).click();
  await expect(page.getByText('nadie podrá volver a ver su contraseña')).toBeFocused();
  await page.getByRole('button', { name: 'No, mantenerla' }).click();
  await expect(page.getByRole('button', { name: 'Descartar', exact: true })).toBeFocused();
  expect(writes).toHaveLength(2);
  expect(writes[1].body).toEqual(writes[0].body);
  await expect(page.locator('.secret code')).toHaveText(password);
  await expectNowhere(page, password, console);

  // Leaving the page forgets it: coming back shows an empty form.
  await page.getByRole('link', { name: 'Volver a mi sesión' }).click();
  await page.getByRole('link', { name: 'Crear una cuenta del personal' }).click();
  await expect(page.getByLabel('Nombre completo')).toHaveValue('');
  expect(await page.content()).not.toContain(password);
  expect(unmocked).toEqual([]);
});

test('a bare 504 (nothing created), "Descartar" asks first; then a retry creates it with the same password', async ({ page, context }) => {
  const { writes, console, unmocked } = await openForm(page, context, { staffFailures: [504, 'pass'] });
  await fillAndCreate(page);
  await expect(page.getByRole('heading', { name: 'No pudimos confirmar si la cuenta se creó' })).toBeVisible();
  await page.getByRole('button', { name: 'Descartar', exact: true }).click();
  await expect(page.getByText('nadie podrá volver a ver su contraseña')).toBeVisible();
  await page.getByRole('button', { name: 'No, mantenerla' }).click();
  await page.getByLabel('Entiendo que la cuenta tendrá acceso completo').check();
  await page.getByRole('button', { name: 'Volver a intentar con los mismos datos' }).click();
  await expect(page.getByRole('heading', { name: `Cuenta creada para ${NEW.email}` })).toBeVisible();
  expect(writes).toHaveLength(2);
  expect(writes[1].body).toEqual(writes[0].body);
  await expectNowhere(page, (writes[0].body as { password: string }).password, console);
  expect(unmocked).toEqual([]);
});

test('Mi sesión: when /auth/me fails, the reason and a retry; the expiry still shows', async ({ page }) => {
  const unmocked = await signedIn(page, '/empresas', { meStatus: 503 });
  await page.getByRole('link', { name: `Mi sesión: ${EMAIL}` }).click();
  await expect(page.getByRole('alert')).toContainText(
    'No se pudieron cargar los datos de tu cuenta: el servicio de autenticación no respondió (HTTP 503).',
  );
  await expect(page.getByRole('button', { name: 'Volver a intentar' })).toBeVisible();
  await expect(page.getByText(/dura 1 hora y no se renueva/)).toBeVisible();
  expect(unmocked).toEqual([]);
});

test('checks before sending: confirmation, and an email the backend would reject with a 500', async ({ page, context }) => {
  const { writes, unmocked } = await openForm(page, context);
  await page.getByLabel('Nombre completo').fill(NEW.fullName);
  await page.getByLabel('Correo').fill('luis@asociacion');
  await page.getByRole('button', { name: 'Crear cuenta' }).click();
  await expect(page.getByText('Escribe un correo completo, con un punto en el dominio')).toBeVisible();
  await expect(page.getByText('Marca la casilla para confirmar.')).toBeVisible();
  expect(writes).toEqual([]);

  // An email already registered: said plainly, and no password left behind.
  await page.getByLabel('Correo').fill(EMAIL);
  await page.getByLabel('Entiendo que la cuenta tendrá acceso completo').check();
  await page.getByRole('button', { name: 'Crear cuenta' }).click();
  await expect(page.getByRole('alert')).toHaveText(`Ya existe una cuenta del personal con el correo ${EMAIL}.`);
  await expect(page.locator('.secret')).toHaveCount(0);
  expect(unmocked).toEqual([]);
});

for (const width of WIDTHS) {
  test(`Mi sesión and staff screens at ${width} px: no sideways scroll, axe clean`, async ({ page, context }) => {
    await page.setViewportSize({ width, height: 900 });
    const { unmocked } = await openForm(page, context);
    await page.getByRole('button', { name: 'Crear cuenta' }).click();
    await expectNoSidewaysScroll(page);
    await checkA11y(page);
    await fillAndCreate(page);
    await page.getByRole('button', { name: 'Mostrar' }).click();
    await expectNoSidewaysScroll(page);
    await checkA11y(page);
    await page.getByRole('link', { name: 'Volver a mi sesión' }).click();
    await expect(page.getByText('Ana Paredes Quispe')).toBeVisible();
    await expectNoSidewaysScroll(page);
    await checkA11y(page);
    expect(unmocked).toEqual([]);
  });
}
