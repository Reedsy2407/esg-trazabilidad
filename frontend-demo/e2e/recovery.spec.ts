import { Page, expect, test } from '@playwright/test';

import { BackendOptions, EMAIL, RESET_CODE, fakeBackend } from './backend';
import { WIDTHS, checkA11y, expectNoSidewaysScroll } from './session';

const NEW_PASSWORD = 'una frase larga que solo yo uso';
const SENT = 'Si el correo está registrado, te enviamos un código de 8 dígitos. Vence en 10 minutos. Revisa también la carpeta de spam.';

type Writes = NonNullable<BackendOptions['writes']>;

/** From the login to step 1, signed out, with the fake services. */
async function openForgot(page: Page, options: BackendOptions = {}) {
  const writes: Writes = [];
  const unmocked: string[] = [];
  await fakeBackend(page, { ...options, writes, unmocked });
  await page.goto('/login');
  await page.getByRole('link', { name: '¿Olvidaste tu contraseña?' }).click();
  await expect(page).toHaveURL(/\/olvide-contrasena$/);
  await expect(page.getByRole('heading', { name: '¿Olvidaste tu contraseña?', level: 1 })).toBeFocused();
  return { writes, unmocked };
}

async function askForCode(page: Page, email = EMAIL) {
  await page.getByLabel('Correo electrónico').fill(email);
  await page.getByRole('button', { name: 'Enviar código' }).click();
  await expect(page).toHaveURL(/\/restablecer-contrasena$/);
  await expect(page.getByRole('heading', { name: 'Restablecer la contraseña', level: 1 })).toBeFocused();
}

async function fillReset(page: Page, code: string, password = NEW_PASSWORD, confirm = password) {
  await page.getByLabel('Código de 8 dígitos').fill(code);
  await page.getByLabel('Contraseña nueva', { exact: true }).fill(password);
  await page.getByLabel('Repite la contraseña nueva').fill(confirm);
}

test('the whole flow: ask for a code, paste it with a space, choose a password, and land on the login with the notice', async ({ page }) => {
  const { writes, unmocked } = await openForgot(page);
  await askForCode(page);

  // The fixed message, and the email carried over (not in the URL).
  await expect(page.getByRole('status').filter({ hasText: SENT })).toBeVisible();
  await expect(page.getByLabel('Correo electrónico')).toHaveValue(EMAIL);
  expect(page.url()).not.toContain(encodeURIComponent(EMAIL));
  expect(page.url()).not.toContain('@');

  await fillReset(page, '0427 1983');
  await page.getByLabel('Repite la contraseña nueva').press('Enter');
  await expect(page).toHaveURL(/\/login\?motivo=clave$/);
  await expect(page.getByText('Contraseña cambiada. Inicia sesión con la nueva.')).toBeVisible();

  expect(writes.map((w) => `${w.method} ${w.path.split('/').slice(-2).join('/')}`)).toEqual([
    'POST password-reset/request',
    'POST password-reset/confirm',
  ]);
  expect(writes[0].body).toEqual({ email: EMAIL });
  expect(writes[1].body).toEqual({ email: EMAIL, code: RESET_CODE, newPassword: NEW_PASSWORD });

  // The password and the code are nowhere they must not be: storage, URL, history state, DOM attributes.
  const places = await page.evaluate(() => ({
    storage: [...Object.keys(sessionStorage).map((k) => sessionStorage.getItem(k)), ...Object.keys(localStorage).map((k) => localStorage.getItem(k))].join('\n'),
    url: location.href,
    state: JSON.stringify(history.state),
    attributes: [...document.querySelectorAll('*')].flatMap((n) => [...n.attributes].map((a) => a.value)).join('\n'),
  }));
  for (const secret of [NEW_PASSWORD, RESET_CODE]) {
    expect(places.storage).not.toContain(secret);
    expect(places.url).not.toContain(secret);
    expect(places.state).not.toContain(secret);
    expect(places.attributes).not.toContain(secret);
  }
  expect(unmocked).toEqual([]);
});

test('a code that does not work says one thing; the fifth sends the person back to step 1 with their email', async ({ page }) => {
  const { writes, unmocked } = await openForgot(page);
  await askForCode(page);

  for (let attempt = 1; attempt <= 4; attempt++) {
    await fillReset(page, `1111000${attempt}`);
    await page.getByRole('button', { name: 'Cambiar contraseña' }).click();
    await expect(page.getByText('El código no es válido o venció. Pide uno nuevo.')).toBeVisible();
    await expect(page.getByLabel('Código de 8 dígitos')).toBeFocused();
    await expect(page.getByLabel('Código de 8 dígitos')).toHaveAttribute('aria-invalid', 'true');
  }
  await fillReset(page, '11110005');
  await page.getByRole('button', { name: 'Cambiar contraseña' }).click();

  await expect(page).toHaveURL(/\/olvide-contrasena$/);
  await expect(page.getByText('Escribiste varios códigos que no sirven. Pide un código nuevo.')).toBeVisible();
  await expect(page.getByLabel('Correo electrónico')).toHaveValue(EMAIL);
  expect(writes.filter((w) => w.path.endsWith('/confirm'))).toHaveLength(5);
  expect(unmocked).toEqual([]);
});

test('field errors are announced next to the field and focus goes to the first one; nothing is sent', async ({ page }) => {
  const { writes } = await openForgot(page);
  await page.getByRole('button', { name: 'Enviar código' }).click();
  await expect(page.getByText('Escribe tu correo electrónico.')).toBeVisible();
  await expect(page.getByLabel('Correo electrónico')).toBeFocused();
  expect(writes).toEqual([]);

  await askForCode(page);
  await page.getByRole('button', { name: 'Cambiar contraseña' }).click();
  await expect(page.getByText('Escribe el código de 8 dígitos que te enviamos.')).toBeVisible();
  await expect(page.getByText('Escribe la contraseña.')).toBeVisible();
  await expect(page.getByLabel('Código de 8 dígitos')).toBeFocused();

  await fillReset(page, RESET_CODE, 'corta', 'otra');
  await page.getByRole('button', { name: 'Cambiar contraseña' }).click();
  await expect(page.getByText('Debe tener al menos 15 caracteres.')).toBeVisible();
  await expect(page.getByText('Las dos contraseñas no coinciden.')).toBeVisible();
  expect(writes.filter((w) => w.path.endsWith('/confirm'))).toEqual([]);
});

test('a password the service refuses is said under its field, and the code stays usable', async ({ page }) => {
  const { writes } = await openForgot(page, { resetConfirmFailure: { status: 400, code: 'AUTH-007' } });
  await askForCode(page);

  await fillReset(page, RESET_CODE);
  await page.getByRole('button', { name: 'Cambiar contraseña' }).click();

  await expect(page.getByText('El servicio no acepta esa contraseña')).toBeVisible();
  await expect(page.getByLabel('Contraseña nueva', { exact: true })).toBeFocused();
  await expect(page).toHaveURL(/\/restablecer-contrasena$/);
  expect(writes.filter((w) => w.path.endsWith('/confirm'))).toHaveLength(1);
});

test('too many tries from this address say how long to wait, on both steps', async ({ page }) => {
  await openForgot(page, { resetRequestFailure: { status: 429, code: 'AUTH-012', retryAfter: 1800 } });
  await page.getByLabel('Correo electrónico').fill(EMAIL);
  await page.getByRole('button', { name: 'Enviar código' }).click();
  await expect(page.getByRole('alert')).toHaveText('Demasiados intentos. Prueba de nuevo en 30 minutos.');
  await expect(page).toHaveURL(/\/olvide-contrasena$/);
});

test('a bare gateway error never claims the code was not sent, and "Ya tengo un código" goes to step 2', async ({ page }) => {
  await openForgot(page, { resetRequestFailure: { status: 504, code: null } });
  await page.getByLabel('Correo electrónico').fill(EMAIL);
  await page.getByRole('button', { name: 'Enviar código' }).click();
  await expect(page.getByRole('alert')).toContainText('No pudimos confirmar si se envió el código');

  await page.getByRole('link', { name: 'Ya tengo un código' }).click();
  await expect(page).toHaveURL(/\/restablecer-contrasena$/);
  await expect(page.getByLabel('Correo electrónico')).toHaveValue('');
  // No message claims a code was sent when the person arrives without asking.
  await expect(page.getByText(SENT)).toHaveCount(0);
});

test('the resend button waits 60 seconds, announces only the end, and sends a new request after it', async ({ page }) => {
  await page.clock.install({ time: new Date('2026-10-16T15:00:00Z') });
  const { writes } = await openForgot(page);
  await askForCode(page);

  const resend = page.getByRole('button', { name: 'Enviar otro código' });
  await expect(resend).toBeDisabled();
  await expect(page.getByText(/Podrás pedir otro código en 60 s/)).toBeVisible();
  const live = page.locator('.secondary [aria-live="polite"]');
  await expect(live).toHaveText('');

  await page.clock.fastForward(30_000);
  await expect(page.getByText(/Podrás pedir otro código en 30 s/)).toBeVisible();
  await expect(live).toHaveText('');

  await page.clock.fastForward(31_000);
  await expect(resend).toBeEnabled();
  await expect(live).toHaveText('Ya puedes pedir un código nuevo.');

  await resend.click();
  await expect(page.getByText('Si el correo está registrado, te enviamos un código nuevo.')).toBeVisible();
  await expect(resend).toBeDisabled();
  expect(writes.filter((w) => w.path.endsWith('/request'))).toHaveLength(2);
});

test('keyboard only: the order of step 1 follows the page and Enter sends', async ({ page }) => {
  const { writes } = await openForgot(page);
  await page.keyboard.press('Tab');
  await expect(page.getByLabel('Correo electrónico')).toBeFocused();
  await page.keyboard.type(EMAIL);
  await page.keyboard.press('Tab');
  await expect(page.getByRole('button', { name: 'Enviar código' })).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(page.getByRole('link', { name: 'Ya tengo un código' })).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(page.getByRole('link', { name: 'Volver a iniciar sesión' })).toBeFocused();

  await page.getByLabel('Correo electrónico').press('Enter');
  await expect(page).toHaveURL(/\/restablecer-contrasena$/);
  expect(writes).toHaveLength(1);
});

for (const width of WIDTHS) {
  test(`both steps at ${width} px: no sideways scrolling and AXE clean, empty and with errors`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await fakeBackend(page);

    await page.goto('/olvide-contrasena');
    await expect(page.getByRole('heading', { name: '¿Olvidaste tu contraseña?', level: 1 })).toBeVisible();
    await expectNoSidewaysScroll(page);
    await checkA11y(page);
    await page.getByRole('button', { name: 'Enviar código' }).click();
    await expect(page.getByText('Escribe tu correo electrónico.')).toBeVisible();
    await expectNoSidewaysScroll(page);
    await checkA11y(page);

    await page.goto('/restablecer-contrasena');
    await expect(page.getByRole('heading', { name: 'Restablecer la contraseña', level: 1 })).toBeVisible();
    await expectNoSidewaysScroll(page);
    await checkA11y(page);
    await page.getByRole('button', { name: 'Cambiar contraseña' }).click();
    await expect(page.getByText('Escribe el código de 8 dígitos que te enviamos.')).toBeVisible();
    await expectNoSidewaysScroll(page);
    await checkA11y(page);
  });
}

test('the login offers the link, reachable by keyboard after the sign-in button', async ({ page }) => {
  await fakeBackend(page);
  await page.goto('/login');
  const button = page.getByRole('button', { name: 'Iniciar sesión' });
  await button.focus();
  await page.keyboard.press('Tab');
  await expect(page.getByRole('link', { name: '¿Olvidaste tu contraseña?' })).toBeFocused();
  await checkA11y(page);
});
