import { expect, test } from '@playwright/test';

import { BackendOptions, CERTIFICATES, COMPANIES, EMAIL } from './backend';
import { WIDTHS, checkA11y, expectNoSidewaysScroll, signedIn } from './session';

const LATEST = CERTIFICATES[0]; // December 2024
const CERT_PATH = (id: string) => `/empresas/${COMPANIES[0].id}/certificados/${id}`;

async function open(page: import('@playwright/test').Page, path: string, options: BackendOptions = {}) {
  const writes: NonNullable<BackendOptions['writes']> = [];
  const unmocked = await signedIn(page, path, { ...options, writes });
  return { writes, unmocked };
}

async function annul(page: import('@playwright/test').Page, reason: string) {
  await page.getByRole('button', { name: 'Anular certificado' }).click();
  const action = page.getByRole('button', { name: /^Anular el certificado de/ });
  await expect(action).toBeDisabled(); // the confirmation comes first
  await page.getByLabel('Motivo').fill(reason);
  await page.getByLabel('Entiendo que la anulación no se puede deshacer').check();
  await action.click();
}

test('annul a certificate: reason and confirmation first, then ANULADO with when, who and why, numbers untouched', async ({ page }) => {
  const { writes, unmocked } = await open(page, CERT_PATH(LATEST.id));
  await expect(page.locator('article.sheet .stamp')).toHaveCount(0);

  await annul(page, 'Se emitió con kilos erróneos');

  const sheet = page.locator('article.sheet');
  await expect(sheet.locator('.stamp')).toHaveText('Anulado');
  await expect(sheet.locator('.annulment')).toContainText(EMAIL);
  await expect(sheet.locator('.annulment')).toContainText('Motivo: Se emitió con kilos erróneos');
  await expect(sheet).toContainText('12,480.50 kg'); // the frozen number is still there
  await expect(page.getByRole('status').filter({ hasText: 'Certificado anulado.' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Anular certificado' })).toHaveCount(0);
  // Only the reason travels: who and when are the server's.
  expect(writes).toEqual([
    { method: 'POST', path: `/svc/reporting/tracked-companies/${COMPANIES[0].id}/certificates/${LATEST.id}/annul`, body: { reason: 'Se emitió con kilos erróneos' } },
  ]);
  expect(unmocked).toEqual([]);
});

test('an annulled certificate stays in the company list, marked, and out of the summary', async ({ page }) => {
  await open(page, CERT_PATH(LATEST.id));
  await annul(page, 'Datos erróneos');
  await expect(page.locator('article.sheet .stamp')).toBeVisible();

  await page.getByRole('link', { name: `Volver a ${COMPANIES[0].name}` }).click();
  const row = page.getByRole('row').filter({ hasText: 'Diciembre 2024' });
  await expect(row.locator('.annulled-tag')).toHaveText('Anulado');
  // The summary now describes November, the latest period still certified.
  await expect(page.locator('section[aria-labelledby="summary-title"]')).toContainText('Noviembre 2024');
  await expect(page.getByRole('row').filter({ hasText: 'Noviembre 2024' }).locator('.annulled-tag')).toHaveCount(0);
});

test('RPT-019 and an unknown outcome are told apart', async ({ page }) => {
  await open(page, CERT_PATH(LATEST.id), { annulFailure: { status: 409, code: 'RPT-019' } });
  await annul(page, 'Motivo');
  await expect(page.getByRole('alert')).toContainText('ya estaba anulado');
  await expect(page.locator('article.sheet .stamp')).toHaveCount(0);
});

test('a gateway answer never claims the annulment failed', async ({ page }) => {
  await open(page, CERT_PATH(LATEST.id), { annulFailure: { status: 502, code: null } });
  await annul(page, 'Motivo');
  await expect(page.getByRole('alert')).toContainText('No pudimos confirmar si el certificado se anuló');
  await expect(page.getByRole('alert')).toBeFocused();
});

test('an empty reason is refused before anything is sent', async ({ page }) => {
  const { writes } = await open(page, CERT_PATH(LATEST.id));
  await page.getByRole('button', { name: 'Anular certificado' }).click();
  await page.getByLabel('Motivo').fill('   ');
  await page.getByLabel('Entiendo que la anulación no se puede deshacer').check();
  await page.getByRole('button', { name: /^Anular el certificado de/ }).click();
  await expect(page.getByText('Escribe el motivo.')).toBeVisible();
  expect(writes).toEqual([]);
});

for (const width of WIDTHS) {
  test(`annulment at ${width} px: no sideways scroll and no AXE violations, panel open and annulled`, async ({ page }) => {
    await page.setViewportSize({ width, height: 800 });
    await open(page, CERT_PATH(LATEST.id));
    await page.getByRole('button', { name: 'Anular certificado' }).click();
    await expect(page.getByLabel('Motivo')).toBeVisible();
    await expectNoSidewaysScroll(page);
    await page.evaluate(() => Promise.all(document.querySelector('article.sheet')!.getAnimations().map((a) => a.finished)));
    await checkA11y(page);

    await page.getByLabel('Motivo').fill('Datos erróneos');
    await page.getByLabel('Entiendo que la anulación no se puede deshacer').check();
    await page.getByRole('button', { name: /^Anular el certificado de/ }).click();
    await expect(page.locator('article.sheet .stamp')).toBeVisible();
    await expectNoSidewaysScroll(page);
    await checkA11y(page);
  });
}
