import { expect, test } from '@playwright/test';

import { COMPANIES, EMAIL, PASSWORD, fakeBackend } from './backend';

test('warm services, wrong password, then sign in and open a company', async ({ page }) => {
  await fakeBackend(page);
  await page.goto('/');

  // All four services answer at once: the warm-up screen is skipped.
  await expect(page).toHaveURL(/\/login$/);

  await page.getByLabel('Correo electrónico').fill(EMAIL);
  await page.getByLabel('Contraseña').fill('otra-clave');
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await expect(page.getByRole('alert')).toHaveText('El correo o la contraseña no son correctos.');
  await expect(page).toHaveURL(/\/login$/);
  // Only the credentials message, not also the empty-field one; the password field is ready to retype.
  await expect(page.getByText('Escribe tu contraseña.')).toHaveCount(0);
  await expect(page.getByLabel('Contraseña')).toBeFocused();
  await expect(page.getByLabel('Contraseña')).toHaveValue('');

  await page.getByLabel('Contraseña').fill(PASSWORD);
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();

  await expect(page).toHaveURL(/\/empresas$/);
  await expect(page.getByRole('heading', { name: 'Empresas', level: 1 })).toBeVisible();
  await expect(page.getByText(EMAIL)).toBeVisible();
  const rows = page.locator('a.card');
  await expect(rows).toHaveCount(2);
  await expect(rows.first()).toContainText(COMPANIES[0].name);
  await expect(rows.first()).toContainText(COMPANIES[0].ruc);
  await expect(rows.first()).toContainText('Activa');
  await expect(rows.nth(1)).toContainText('Inactiva');
  await expect(page.getByText('Mostrando 1–2 de 2 empresas')).toBeVisible();

  // The whole stub opens the company, by mouse or keyboard.
  await rows.nth(1).click({ position: { x: 600, y: 30 } });
  await expect(page).toHaveURL(new RegExp(`/empresas/${COMPANIES[1].id}$`));
  await expect(page.getByRole('heading', { name: COMPANIES[1].name })).toBeVisible();
  await expect(page.getByText(COMPANIES[1].ruc)).toBeVisible();

  await page.getByRole('link', { name: 'Volver a empresas' }).click();
  await page.getByRole('link', { name: COMPANIES[0].name }).focus();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(new RegExp(`/empresas/${COMPANIES[0].id}$`));

  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await expect(page).toHaveURL(/\/login$/);
});

test('too many attempts: the lock is announced and the button waits for Retry-After', async ({ page }) => {
  await fakeBackend(page, { lockedFor: 3 });
  await page.goto('/login');

  await page.getByLabel('Correo electrónico').fill(EMAIL);
  await page.getByLabel('Contraseña').fill(PASSWORD);
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();

  await expect(page.getByRole('status')).toHaveText('Demasiados intentos. Podrás volver a intentarlo en 3 segundos.');
  await expect(page.getByRole('button', { name: 'Iniciar sesión' })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Iniciar sesión' })).toBeEnabled({ timeout: 5_000 });
});

test('a session the backend rejects (AUTH-000) ends on the login with a notice', async ({ page }) => {
  await fakeBackend(page, { rejectTokens: true });
  await page.goto('/login');

  await page.getByLabel('Correo electrónico').fill(EMAIL);
  await page.getByLabel('Contraseña').fill(PASSWORD);
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();

  await expect(page).toHaveURL(/\/login\?motivo=sesion$/);
  await expect(page.getByText('Tu sesión terminó. Vuelve a iniciar sesión para continuar.')).toBeVisible();
});

test('a protected page without a session goes to the login', async ({ page }) => {
  await fakeBackend(page);
  await page.goto('/empresas');

  await expect(page).toHaveURL(/\/login$/);
});

test('on a 360 px phone the companies are stacked cards and the page never scrolls sideways', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 800 });
  await fakeBackend(page);
  await page.goto('/login');
  await page.getByLabel('Correo electrónico').fill(EMAIL);
  await page.getByLabel('Contraseña').fill(PASSWORD);
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await expect(page).toHaveURL(/\/empresas$/);
  await expect(page.getByText('Mostrando 1–2 de 2 empresas')).toBeVisible();

  const { scrollWidth, innerWidth } = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  expect(scrollWidth).toBeLessThanOrEqual(innerWidth);

  // The same stubs as on a desktop, stacked: each shows name, state and RUC and opens the company.
  const card = page.locator('a.card').filter({ hasText: COMPANIES[1].name });
  await expect(card).toContainText('Inactiva');
  await expect(card).toContainText(COMPANIES[1].ruc);
  expect((await card.boundingBox())?.height ?? 0).toBeGreaterThanOrEqual(44);
  await card.click();
  await expect(page).toHaveURL(new RegExp(`/empresas/${COMPANIES[1].id}$`));
});

test('on a 360 px phone the warm-up rows stack: state and timer stay inside the sheet', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 800 });
  // collection-service is slow: its row stays "Despertando" with a running timer.
  await page.route('**/svc/collection/actuator/health/liveness', () => new Promise(() => undefined));
  for (const service of ['auth', 'recycler', 'reporting']) {
    await page.route(`**/svc/${service}/actuator/health/liveness`, (r) =>
      r.fulfill({ status: 200, contentType: 'application/json', body: '{"status":"UP"}' }),
    );
  }
  await page.goto('/');
  // Target the slow service's row: the other three may still be in flight for a moment.
  const waking = page.locator('tr[data-status="waking"]').filter({ hasText: 'collection-service' });
  await expect(waking).toContainText('Despertando');
  await expect(waking.locator('.time')).toHaveText(/^\d\d:\d\d$/);

  const layout = await page.evaluate(() => {
    const sheet = document.querySelector('.sheet') as HTMLElement;
    const style = getComputedStyle(sheet);
    const sheetBox = sheet.getBoundingClientRect();
    const row = Array.from(document.querySelectorAll('tr[data-status="waking"]')).find((tr) =>
      tr.textContent?.includes('collection-service'),
    ) as HTMLElement;
    const status = row.querySelector('.status') as HTMLElement;
    const time = row.querySelector('.time') as HTMLElement;
    return {
      scrollWidth: document.documentElement.scrollWidth,
      innerWidth: window.innerWidth,
      sheetInnerRight: sheetBox.right - parseFloat(style.borderRightWidth) - parseFloat(style.paddingRight),
      timeRight: time.getBoundingClientRect().right,
      gap: time.getBoundingClientRect().left - status.getBoundingClientRect().right,
      sameLine: Math.abs(time.getBoundingClientRect().top - status.getBoundingClientRect().top) < 4,
    };
  });
  expect(layout.scrollWidth).toBeLessThanOrEqual(layout.innerWidth);
  expect(layout.timeRight).toBeLessThanOrEqual(layout.sheetInnerRight);
  // On the same line, state and timer keep at least the 8 px gap ("Despertando 00:03", never "Despertando00:03").
  if (layout.sameLine) {
    expect(layout.gap).toBeGreaterThanOrEqual(8);
  }
});

test('each company stub is a 44 px+ target with a visible focus ring drawn inside it', async ({ page }) => {
  await fakeBackend(page);
  await page.goto('/login');
  await page.getByLabel('Correo electrónico').fill(EMAIL);
  await page.getByLabel('Contraseña').fill(PASSWORD);
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  const stub = page.locator('a.card').first();
  await expect(stub).toBeVisible();
  await stub.focus();
  await page.keyboard.press('Shift+Tab');
  await page.keyboard.press('Tab');
  const ring = await stub.evaluate((el) => {
    const s = getComputedStyle(el);
    return { style: s.outlineStyle, width: parseFloat(s.outlineWidth), offset: parseFloat(s.outlineOffset), height: el.getBoundingClientRect().height };
  });
  expect(ring.style).toBe('solid');
  expect(ring.width).toBeGreaterThanOrEqual(2);
  expect(ring.offset).toBeLessThan(0); // inside the stub, where the serrated mask can't clip it
  expect(ring.height).toBeGreaterThanOrEqual(44);
});

test('"Actualizar la lista": says "Actualizando…" and waits while loading, then announces "Lista actualizada."', async ({ page }) => {
  await fakeBackend(page, { companyLists: ['empty', 'full'], companyListDelayMs: 600 });
  await page.goto('/login');
  await page.getByLabel('Correo electrónico').fill(EMAIL);
  await page.getByLabel('Contraseña').fill(PASSWORD);
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await expect(page.getByText('Todavía no hay empresas registradas para trazabilidad.')).toBeVisible();
  await expect(page.getByRole('status').filter({ hasText: 'Lista actualizada.' })).toHaveCount(0); // the first load is silent

  await page.getByRole('button', { name: 'Actualizar la lista' }).click();
  const busy = page.getByRole('button', { name: 'Actualizando…' });
  await expect(busy).toBeVisible();
  await expect(busy).toHaveAttribute('aria-disabled', 'true');
  await expect(busy).toBeFocused(); // still focused while it works

  await expect(page.locator('a.card')).toHaveCount(2);
  await expect(page.getByRole('status').filter({ hasText: 'Lista actualizada.' })).toHaveCount(1);
  await expect(page.locator('ul.stubs')).toBeFocused(); // focus moves to the refreshed list
});

test('"Actualizar la lista" that fails: the error block says why (role=alert), the polite region stays silent', async ({ page }) => {
  await fakeBackend(page, { companyLists: ['empty', 'fail'] });
  await page.goto('/login');
  await page.getByLabel('Correo electrónico').fill(EMAIL);
  await page.getByLabel('Contraseña').fill(PASSWORD);
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await page.getByRole('button', { name: 'Actualizar la lista' }).click();

  await expect(page.getByRole('alert')).toContainText('No se pudo cargar la lista de empresas.');
  await expect(page.getByRole('button', { name: 'Volver a intentar' })).toBeFocused();
  await expect(page.getByRole('status').filter({ hasText: 'Lista actualizada.' })).toHaveCount(0);
});
