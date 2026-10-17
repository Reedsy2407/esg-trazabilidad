import { Page, expect, test } from '@playwright/test';

import { BackendOptions, EMAIL, RESET_CODE, RESET_TOKEN, fakeBackend } from './backend';
import { WIDTHS, checkA11y, expectNoSidewaysScroll } from './session';

const NEW_PASSWORD = 'una frase larga que solo yo uso';
const SENT = 'Si el correo está registrado, te enviamos un código de 8 dígitos. Vence en 10 minutos. Revisa también la carpeta de spam.';

type Writes = NonNullable<BackendOptions['writes']>;

/** From the login to the email step, signed out, with the fake services. */
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

/** The email step, then the code step. */
async function askForCode(page: Page, email = EMAIL) {
  await page.getByLabel('Correo electrónico').fill(email);
  await page.getByRole('button', { name: 'Enviar código' }).click();
  await expect(page).toHaveURL(/\/restablecer-contrasena$/);
  await expect(page.getByRole('heading', { name: 'Revisa tu correo', level: 1 })).toBeFocused();
}

/** The code step, then the new-password step. */
async function verifyCode(page: Page, code = RESET_CODE) {
  await page.getByLabel('Código de 8 dígitos').fill(code);
  await page.getByRole('button', { name: 'Verificar código' }).click();
  await expect(page).toHaveURL(/\/nueva-contrasena$/);
  await expect(page.getByRole('heading', { name: 'Elige una contraseña nueva', level: 1 })).toBeFocused();
}

async function fillNew(page: Page, password = NEW_PASSWORD, confirm = password) {
  await page.getByLabel('Contraseña nueva', { exact: true }).fill(password);
  await page.getByLabel('Repite la contraseña nueva').fill(confirm);
}

test('the whole flow: email, code pasted with a space, then the new password, and the login with the notice', async ({ page }) => {
  const { writes, unmocked } = await openForgot(page);
  await askForCode(page);

  // Step 2: the email is fixed text with a link to change it; the message is the fixed one; nothing in the URL.
  await expect(page.getByTestId('fixed-email')).toHaveText(EMAIL);
  await expect(page.getByLabel('Correo electrónico')).toHaveCount(0);
  await expect(page.getByRole('link', { name: '¿No es tu correo? Cambiar' })).toBeVisible();
  await expect(page.getByRole('status').filter({ hasText: SENT })).toBeVisible();
  await expect(page.getByLabel('Contraseña nueva')).toHaveCount(0); // not asked until the code is verified
  expect(page.url()).not.toContain('@');

  await page.getByLabel('Código de 8 dígitos').fill('0427 1983');
  await page.getByLabel('Código de 8 dígitos').press('Enter');
  await expect(page).toHaveURL(/\/nueva-contrasena$/);
  await expect(page.getByRole('heading', { name: 'Elige una contraseña nueva', level: 1 })).toBeFocused();
  // The token exists only in memory: not in the URL, the history state, storage or the DOM.
  const midway = await page.evaluate(() => ({
    storage: [...Object.keys(sessionStorage).map((k) => sessionStorage.getItem(k)), ...Object.keys(localStorage).map((k) => localStorage.getItem(k))].join('\n'),
    url: location.href,
    state: JSON.stringify(history.state),
    html: document.documentElement.outerHTML,
  }));
  for (const where of [midway.storage, midway.url, midway.state, midway.html]) {
    expect(where).not.toContain(RESET_TOKEN);
    expect(where).not.toContain(RESET_TOKEN.split('.')[1]);
  }

  await fillNew(page);
  await page.getByLabel('Repite la contraseña nueva').press('Enter');
  await expect(page).toHaveURL(/\/login\?motivo=clave$/);
  await expect(page.getByText('Contraseña cambiada. Inicia sesión con la nueva.')).toBeVisible();

  expect(writes.map((w) => `${w.method} ${w.path.split('/').slice(-2).join('/')}`)).toEqual([
    'POST password-reset/request',
    'POST password-reset/verify',
    'POST password-reset/confirm',
  ]);
  expect(writes[0].body).toEqual({ email: EMAIL });
  expect(writes[1].body).toEqual({ email: EMAIL, code: RESET_CODE });
  expect(writes[2].body).toEqual({ resetToken: RESET_TOKEN, newPassword: NEW_PASSWORD });

  // The password, the code and the token are nowhere they must not be.
  const places = await page.evaluate(() => ({
    storage: [...Object.keys(sessionStorage).map((k) => sessionStorage.getItem(k)), ...Object.keys(localStorage).map((k) => localStorage.getItem(k))].join('\n'),
    url: location.href,
    state: JSON.stringify(history.state),
    attributes: [...document.querySelectorAll('*')].flatMap((n) => [...n.attributes].map((a) => a.value)).join('\n'),
  }));
  for (const secret of [NEW_PASSWORD, RESET_CODE, RESET_TOKEN]) {
    expect(places.storage).not.toContain(secret);
    expect(places.url).not.toContain(secret);
    expect(places.state).not.toContain(secret);
    expect(places.attributes).not.toContain(secret);
  }
  expect(unmocked).toEqual([]);
});

test('a code that does not work says one thing; the fifth sends the person back to the email step', async ({ page }) => {
  const { writes, unmocked } = await openForgot(page);
  await askForCode(page);

  for (let attempt = 1; attempt <= 4; attempt++) {
    await page.getByLabel('Código de 8 dígitos').fill(`1111000${attempt}`);
    await page.getByRole('button', { name: 'Verificar código' }).click();
    await expect(page.getByText('El código no es válido o venció. Pide uno nuevo.')).toBeVisible();
    await expect(page.getByLabel('Código de 8 dígitos')).toBeFocused();
    await expect(page.getByLabel('Código de 8 dígitos')).toHaveAttribute('aria-invalid', 'true');
  }
  await page.getByLabel('Código de 8 dígitos').fill('11110005');
  await page.getByRole('button', { name: 'Verificar código' }).click();

  await expect(page).toHaveURL(/\/olvide-contrasena$/);
  await expect(page.getByText('Escribiste varios códigos que no sirven. Pide un código nuevo.')).toBeVisible();
  await expect(page.getByLabel('Correo electrónico')).toHaveValue(EMAIL);
  expect(writes.filter((w) => w.path.endsWith('/verify'))).toHaveLength(5);
  expect(unmocked).toEqual([]);
});

test('field errors are announced next to the field and focus goes to it; nothing is sent', async ({ page }) => {
  const { writes } = await openForgot(page);
  await page.getByRole('button', { name: 'Enviar código' }).click();
  await expect(page.getByText('Escribe tu correo electrónico.')).toBeVisible();
  await expect(page.getByLabel('Correo electrónico')).toBeFocused();
  expect(writes).toEqual([]);

  await askForCode(page);
  await page.getByRole('button', { name: 'Verificar código' }).click();
  await expect(page.getByText('Escribe el código de 8 dígitos que te enviamos.')).toBeVisible();
  await expect(page.getByLabel('Código de 8 dígitos')).toBeFocused();
  expect(writes.filter((w) => w.path.endsWith('/verify'))).toEqual([]);

  await verifyCode(page);
  await page.getByRole('button', { name: 'Cambiar contraseña' }).click();
  await expect(page.getByText('Escribe la contraseña.')).toBeVisible();
  await expect(page.getByText('Repite la contraseña nueva.')).toBeVisible();
  await expect(page.getByLabel('Contraseña nueva', { exact: true })).toBeFocused();

  await fillNew(page, 'corta', 'otra');
  await page.getByRole('button', { name: 'Cambiar contraseña' }).click();
  await expect(page.getByText('Debe tener al menos 15 caracteres.')).toBeVisible();
  await expect(page.getByText('Las dos contraseñas no coinciden.')).toBeVisible();
  expect(writes.filter((w) => w.path.endsWith('/confirm'))).toEqual([]);
});

test('the show-passwords toggle reveals both fields', async ({ page }) => {
  await openForgot(page);
  await askForCode(page);
  await verifyCode(page);
  await fillNew(page);

  await expect(page.getByLabel('Contraseña nueva', { exact: true })).toHaveAttribute('type', 'password');
  await page.getByLabel('Mostrar las contraseñas').check();
  await expect(page.getByLabel('Contraseña nueva', { exact: true })).toHaveAttribute('type', 'text');
  await expect(page.getByLabel('Repite la contraseña nueva')).toHaveAttribute('type', 'text');
});

test('a password the service refuses is said under its field and the person stays to type another', async ({ page }) => {
  const { writes } = await openForgot(page, { resetConfirmFailure: { status: 400, code: 'AUTH-007' } });
  await askForCode(page);
  await verifyCode(page);

  await fillNew(page);
  await page.getByRole('button', { name: 'Cambiar contraseña' }).click();

  await expect(page.getByText('El servicio no acepta esa contraseña')).toBeVisible();
  await expect(page.getByLabel('Contraseña nueva', { exact: true })).toBeFocused();
  await expect(page).toHaveURL(/\/nueva-contrasena$/);
  expect(writes.filter((w) => w.path.endsWith('/confirm'))).toHaveLength(1);
});

test('when the token no longer works the person goes back to the code step with a clear message and the same email', async ({ page }) => {
  await openForgot(page, { resetConfirmFailure: { status: 400, code: 'AUTH-011' } });
  await askForCode(page);
  await verifyCode(page);

  await fillNew(page);
  await page.getByRole('button', { name: 'Cambiar contraseña' }).click();

  await expect(page).toHaveURL(/\/restablecer-contrasena$/);
  await expect(page.getByRole('status').filter({ hasText: 'La verificación venció' })).toBeVisible();
  await expect(page.getByTestId('fixed-email')).toHaveText(EMAIL);
  // A new code can be asked for at once: nothing was sent from this screen.
  await expect(page.getByRole('button', { name: 'Enviar otro código' })).toBeEnabled();
});

test('a reload on the new-password step loses the token, so the person starts again from the email', async ({ page }) => {
  await openForgot(page);
  await askForCode(page);
  await verifyCode(page);

  await page.reload();

  await expect(page).toHaveURL(/\/olvide-contrasena$/);
  await expect(page.getByRole('heading', { name: '¿Olvidaste tu contraseña?', level: 1 })).toBeVisible();
});

test('"¿No es tu correo? Cambiar" goes back to the email step with the email filled in', async ({ page }) => {
  await openForgot(page);
  await askForCode(page);

  await page.getByRole('link', { name: '¿No es tu correo? Cambiar' }).click();

  await expect(page).toHaveURL(/\/olvide-contrasena$/);
  await expect(page.getByLabel('Correo electrónico')).toHaveValue(EMAIL);
});

test('too many tries from this address say how long to wait, on the email step and on the code step', async ({ page }) => {
  await openForgot(page, { resetRequestFailure: { status: 429, code: 'AUTH-012', retryAfter: 1800 } });
  await page.getByLabel('Correo electrónico').fill(EMAIL);
  await page.getByRole('button', { name: 'Enviar código' }).click();
  await expect(page.getByRole('alert')).toHaveText('Demasiados intentos. Prueba de nuevo en 30 minutos.');
  await expect(page).toHaveURL(/\/olvide-contrasena$/);

  const second = await page.context().newPage();
  await fakeBackend(second, { resetVerifyFailure: { status: 429, code: 'AUTH-012', retryAfter: 600 } });
  await second.goto('/olvide-contrasena');
  await second.getByLabel('Correo electrónico').fill(EMAIL);
  await second.getByRole('button', { name: 'Enviar código' }).click();
  await expect(second).toHaveURL(/\/restablecer-contrasena$/);
  await second.getByLabel('Código de 8 dígitos').fill(RESET_CODE);
  await second.getByRole('button', { name: 'Verificar código' }).click();
  await expect(second.getByRole('alert')).toHaveText('Demasiados intentos. Prueba de nuevo en 10 minutos.');
});

test('a bare gateway error never claims the code was not sent', async ({ page }) => {
  await openForgot(page, { resetRequestFailure: { status: 504, code: null } });
  await page.getByLabel('Correo electrónico').fill(EMAIL);
  await page.getByRole('button', { name: 'Enviar código' }).click();
  await expect(page.getByRole('alert')).toContainText('No pudimos confirmar si se envió el código');
  await expect(page).toHaveURL(/\/olvide-contrasena$/);
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

test('keyboard only: the order of the email step follows the page and Enter sends', async ({ page }) => {
  const { writes } = await openForgot(page);
  await page.keyboard.press('Tab');
  await expect(page.getByLabel('Correo electrónico')).toBeFocused();
  await page.keyboard.type(EMAIL);
  await page.keyboard.press('Tab');
  await expect(page.getByRole('button', { name: 'Enviar código' })).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(page.getByRole('link', { name: 'Volver a iniciar sesión' })).toBeFocused();

  await page.getByLabel('Correo electrónico').press('Enter');
  await expect(page).toHaveURL(/\/restablecer-contrasena$/);
  expect(writes).toHaveLength(1);
});

for (const width of WIDTHS) {
  test(`the three steps at ${width} px: no sideways scrolling and AXE clean, empty and with errors`, async ({ page }) => {
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

    await askForCode(page);
    await expectNoSidewaysScroll(page);
    await checkA11y(page);
    await page.getByRole('button', { name: 'Verificar código' }).click();
    await expect(page.getByText('Escribe el código de 8 dígitos que te enviamos.')).toBeVisible();
    await expectNoSidewaysScroll(page);
    await checkA11y(page);

    await verifyCode(page);
    await expectNoSidewaysScroll(page);
    await checkA11y(page);
    await page.getByRole('button', { name: 'Cambiar contraseña' }).click();
    await expect(page.getByText('Escribe la contraseña.')).toBeVisible();
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
