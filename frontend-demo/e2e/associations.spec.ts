import { expect, test } from '@playwright/test';

import { ASSOCIATIONS, CERTIFICATIONS } from './backend';
import { WIDTHS, checkA11y, expectNoSidewaysScroll, signedIn } from './session';

const RIMAC = ASSOCIATIONS[0];
const COMAS = ASSOCIATIONS[1];

test('list, filter by state and open an association from the top bar', async ({ page }) => {
  const unmocked = await signedIn(page, '/empresas');
  await page.getByRole('navigation', { name: 'Secciones' }).getByRole('link', { name: 'Asociaciones' }).click();
  await expect(page).toHaveURL(/\/asociaciones$/);
  await expect(page.getByRole('heading', { name: 'Asociaciones', level: 1 })).toBeVisible();

  const rows = page.locator('table.ledger tbody tr');
  await expect(rows).toHaveCount(2);
  await expect(rows.first()).toContainText(RIMAC.name);
  await expect(rows.first()).toContainText(RIMAC.ruc);
  await expect(rows.first()).toContainText('Activa');
  await expect(rows.nth(1)).toContainText('Suspendida');
  await expect(page.getByText('Mostrando 1–2 de 2 asociaciones', { exact: true })).toBeVisible();

  const filter = page.getByRole('navigation', { name: 'Filtrar por estado' });
  await expect(filter.getByRole('link', { name: 'Todas' })).toHaveAttribute('aria-current', 'page');
  await filter.getByRole('link', { name: 'Suspendidas' }).click();
  await expect(page).toHaveURL(/\/asociaciones\?estado=suspendidas$/);
  await expect(rows).toHaveCount(1);
  await expect(rows.first()).toContainText(COMAS.name);
  await expect(filter.getByRole('link', { name: 'Suspendidas' })).toHaveAttribute('aria-current', 'page');

  await filter.getByRole('link', { name: 'Activas' }).click();
  await expect(rows).toHaveCount(1);
  // The whole row opens the association.
  await rows.first().click({ position: { x: 700, y: 20 } });
  await expect(page).toHaveURL(new RegExp(`/asociaciones/${RIMAC.id}$`));
  await expect(page.getByRole('heading', { name: RIMAC.name, level: 1 })).toBeVisible();
  expect(unmocked).toEqual([]);
});

test('pages of 20, kept with the filter in the address', async ({ page }) => {
  const unmocked = await signedIn(page, '/asociaciones', { associationCount: 23 });
  await expect(page.getByText('Mostrando 1–20 de 23 asociaciones', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Siguiente' }).click();
  await expect(page).toHaveURL(/pagina=2/);
  await expect(page.getByText('Mostrando 21–23 de 23 asociaciones', { exact: true })).toBeVisible();
  await expect(page.getByText('Página 2 de 2')).toBeVisible();
  // Announced once loaded, by the polite region that is always on the page.
  await expect(page.getByRole('status')).toHaveText('Mostrando 21–23 de 23 asociaciones.');
  // Changing the filter starts again at page 1.
  await page.getByRole('link', { name: 'Activas' }).click();
  await expect(page).toHaveURL(/\/asociaciones\?estado=activas$/);
  await expect(page.getByText('Mostrando 1–20 de 22 asociaciones', { exact: true })).toBeVisible();
  expect(unmocked).toEqual([]);
});

test('detail: optional data and certifications with vigente / por vencer / vencido', async ({ page }) => {
  const unmocked = await signedIn(page, `/asociaciones/${RIMAC.id}`);
  await expect(page.getByRole('heading', { name: RIMAC.name, level: 1 })).toBeVisible();
  await expect(page.getByText(RIMAC.contactEmail)).toBeVisible();

  const rows = page.locator('table.ledger tbody tr');
  await expect(rows).toHaveCount(CERTIFICATIONS.length);
  await expect(rows.locator('app-certification-chip')).toHaveText(['Vencido', 'Por vencer', 'Vigente']);
  await expect(rows.nth(0)).toContainText('Venció hace 35 días');
  await expect(rows.nth(1)).toContainText('Vence en 12 días');
  await expect(page.getByText('vence en menos de 30 días (regla de esta aplicación')).toBeVisible();

  // The colours are the reserved state tokens, and only on the chips.
  const colors = await rows.locator('app-certification-chip').evaluateAll((chips) =>
    chips.map((chip) => getComputedStyle(chip).color),
  );
  expect(colors).toEqual(['rgb(179, 38, 30)', 'rgb(154, 91, 0)', 'rgb(26, 122, 72)']);
  expect(unmocked).toEqual([]);
});

test('detail: no certifications, and an unknown association or id', async ({ page }) => {
  const unmocked = await signedIn(page, `/asociaciones/${COMAS.id}`);
  await expect(page.getByText('Esta asociación todavía no tiene certificaciones registradas.')).toBeVisible();

  await page.evaluate(() => {
    history.pushState({}, '', '/asociaciones/0192f3a8-0000-7000-8000-0000000000aa');
    dispatchEvent(new PopStateEvent('popstate'));
  });
  await expect(page.getByRole('alert')).toContainText('Esta asociación no existe.');
  await expect(page.getByRole('heading', { name: 'Certificaciones' })).toHaveCount(0);

  await page.evaluate(() => {
    history.pushState({}, '', '/asociaciones/no-es-un-uuid');
    dispatchEvent(new PopStateEvent('popstate'));
  });
  await expect(page.getByRole('alert')).toContainText('Esta asociación no existe.');
  expect(unmocked).toEqual([]);
});

test('detail: failing certifications keep the header and offer a retry', async ({ page }) => {
  const unmocked = await signedIn(page, `/asociaciones/${RIMAC.id}`, { certificationsStatus: 503 });
  await expect(page.getByRole('heading', { name: RIMAC.name, level: 1 })).toBeVisible();
  await expect(page.getByRole('alert')).toContainText('No se pudieron cargar las certificaciones: el servicio de recicladores no respondió (HTTP 503).');
  await expect(page.getByRole('button', { name: 'Volver a intentar' })).toBeVisible();
  expect(unmocked).toEqual([]);
});

test('list: a failing service offers a retry', async ({ page }) => {
  const unmocked = await signedIn(page, '/asociaciones', { associationsStatus: 503 });
  await expect(page.getByRole('alert')).toContainText('No se pudieron cargar las asociaciones: el servicio de recicladores no respondió (HTTP 503).');
  await expect(page.getByRole('button', { name: 'Volver a intentar' })).toBeVisible();
  expect(unmocked).toEqual([]);
});

test('register an association: a RUC that fails the check digit is stopped, a good one opens its page', async ({ page }) => {
  const unmocked = await signedIn(page, '/asociaciones');
  await page.getByRole('link', { name: 'Registrar asociación' }).click();
  await expect(page).toHaveURL(/\/asociaciones\/nueva$/);

  await page.getByLabel('Nombre de la asociación').fill('Recicladores del Sur');
  await page.getByLabel('RUC').fill('20131312950');
  await page.getByRole('button', { name: 'Registrar asociación' }).click();
  await expect(page.getByText('Este RUC no es válido: revisa los dígitos.')).toBeVisible();
  await expect(page).toHaveURL(/\/asociaciones\/nueva$/);

  await page.getByLabel('RUC').fill('20131312955');
  await page.getByRole('button', { name: 'Registrar asociación' }).click();
  await expect(page.getByRole('heading', { name: 'Recicladores del Sur', level: 1 })).toBeVisible();
  await expect(page.getByText('Activa')).toBeVisible();
  await expect(page.getByText('Esta asociación todavía no tiene certificaciones registradas.')).toBeVisible();
  expect(unmocked).toEqual([]);
});

test('suspend and reactivate an association, with a confirmation each time', async ({ page }) => {
  const unmocked = await signedIn(page, `/asociaciones/${RIMAC.id}`);
  await expect(page.locator('.meta app-status-label')).toHaveText(/Activa/);
  await page.getByRole('button', { name: 'Suspender asociación' }).click();
  await expect(page.getByText('se deja de poder registrar recojos')).toBeVisible();
  await page.getByRole('button', { name: 'No, volver' }).click();
  await expect(page.locator('.meta app-status-label')).toHaveText(/Activa/);

  await page.getByRole('button', { name: 'Suspender asociación' }).click();
  await page.getByRole('button', { name: 'Sí, suspender' }).click();
  await expect(page.locator('.meta app-status-label')).toHaveText(/Suspendida/);
  await page.getByRole('button', { name: 'Reactivar asociación' }).click();
  await page.getByRole('button', { name: 'Sí, reactivar' }).click();
  await expect(page.locator('.meta app-status-label')).toHaveText(/Activa/);
  expect(unmocked).toEqual([]);
});

test('register and renew a certification', async ({ page }) => {
  const unmocked = await signedIn(page, `/asociaciones/${COMAS.id}`);
  await page.getByRole('button', { name: 'Registrar certificación' }).click();
  await page.getByLabel('Tipo de certificación').fill('ISO 14001');
  await page.getByLabel('Fecha de emisión').fill('2026-05-01');
  await page.getByLabel('Fecha de vencimiento').fill('2026-04-01');
  await page.getByRole('button', { name: 'Guardar certificación' }).click();
  await expect(page.getByText('El vencimiento debe ser posterior a la emisión.')).toBeVisible();

  await page.getByLabel('Fecha de vencimiento').fill('2099-05-01');
  await page.getByRole('button', { name: 'Guardar certificación' }).click();
  const rows = page.locator('table.ledger tbody tr');
  await expect(rows).toHaveCount(1);
  await expect(rows.first()).toContainText('ISO 14001');

  await page.getByRole('button', { name: 'Renovar ISO 14001' }).first().click();
  await page.getByLabel('Nueva fecha de vencimiento').fill('2100-05-01');
  await page.getByRole('button', { name: 'Renovar certificación' }).click();
  await expect(rows.first()).toContainText('01/05/2100');
  expect(unmocked).toEqual([]);
});

for (const width of WIDTHS) {
  test(`new association and an association with its forms open at ${width} px: no sideways scroll, axe clean`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    const unmocked = await signedIn(page, '/asociaciones/nueva');
    await expect(page.getByRole('heading', { name: 'Registrar asociación', level: 1 })).toBeVisible();
    await expectNoSidewaysScroll(page);
    await checkA11y(page);

    await page.evaluate((target) => {
      history.pushState({}, '', target);
      dispatchEvent(new PopStateEvent('popstate'));
    }, `/asociaciones/${RIMAC.id}`);
    await page.getByRole('button', { name: 'Registrar certificación' }).click();
    await page.getByRole('button', { name: 'Renovar Autorización de segregación en fuente' }).first().click();
    await expectNoSidewaysScroll(page);
    await checkA11y(page);
    expect(unmocked).toEqual([]);
  });
}

for (const width of WIDTHS) {
  test(`associations at ${width} px: no sideways scroll, axe clean`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    const unmocked = await signedIn(page, '/asociaciones');
    await expect(page.getByText('Mostrando 1–2 de 2 asociaciones', { exact: true })).toBeVisible();
    await expectNoSidewaysScroll(page);
    await checkA11y(page);

    await page.getByRole('link', { name: RIMAC.name }).first().click();
    await expect(page.locator('app-certification-chip:visible').first()).toBeVisible();
    await expectNoSidewaysScroll(page);
    await checkA11y(page);
    expect(unmocked).toEqual([]);
  });
}
