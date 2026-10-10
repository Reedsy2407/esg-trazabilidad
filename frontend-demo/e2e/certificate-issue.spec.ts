import { Page, expect, test } from '@playwright/test';

import { ASSOCIATIONS, BackendOptions, COMPANIES, limaDate } from './backend';
import { WIDTHS, checkA11y, expectNoSidewaysScroll, signedIn } from './session';

const ENVASES = COMPANIES[0]; // association: Recicla Rímac, with a SIGERSOL record for 2026-09 and 8.25 kg collected that month

async function open(page: Page, options: BackendOptions = {}) {
  const writes: NonNullable<BackendOptions['writes']> = [];
  const unmocked = await signedIn(page, `/empresas/${ENVASES.id}`, { ...options, writes });
  await page.getByRole('link', { name: 'Emitir certificado' }).click();
  await expect(page.getByRole('heading', { name: 'Emitir certificado', level: 1 })).toBeVisible();
  await expect(page.getByText(ENVASES.name)).toBeVisible();
  return { writes, unmocked };
}

async function reviewMonth(page: Page, month: string) {
  await page.getByLabel('Mes', { exact: true }).fill(month);
  await page.getByRole('button', { name: 'Revisar el borrador' }).click();
}

test('issue a month: draft, checkbox, a button naming the period, one request, then the announced certificate', async ({ page }) => {
  const { writes, unmocked } = await open(page);
  await reviewMonth(page, '2026-09');

  const draft = page.getByRole('article', { name: 'Borrador del certificado, aún no emitido' });
  await expect(draft).toContainText('Borrador · aún no emitido');
  await expect(draft).toContainText('Setiembre 2026');
  await expect(draft).toContainText('8.25 kg');
  await expect(draft).toContainText('91.3 %');
  await expect(draft).toContainText(ASSOCIATIONS[0].name);
  await expect(draft).toHaveCSS('border-top-style', 'dashed');
  await expect(page.getByRole('heading', { name: '2. Borrador' })).toBeFocused();
  await expect(page.getByText('Emitir es definitivo')).toBeVisible();
  await expect(page.getByText('Las cifras se recalculan al emitir')).toBeVisible();

  const issue = page.getByRole('button', { name: 'Emitir certificado de Setiembre 2026' });
  await expect(issue).toBeDisabled();
  await page.getByLabel('Revisé la empresa, el período y las cifras').check();
  await issue.click();

  await expect(page.getByRole('status').filter({ hasText: 'Certificado emitido.' })).toBeVisible();
  await expect(page.getByRole('heading', { name: /Certificado de trazabilidad ESG/ })).toBeVisible();
  await expect(page.getByText('8.25 kg')).toBeVisible();
  expect(writes).toEqual([
    { method: 'POST', path: `/svc/reporting/tracked-companies/${ENVASES.id}/certificates`, body: { periodStart: '2026-09-01', periodEnd: '2026-09-30' } },
  ]);

  // Said once: not again after a reload, nor when a certificate is simply opened.
  await page.reload();
  await expect(page.getByRole('heading', { name: /Certificado de trazabilidad ESG/ })).toBeVisible();
  await expect(page.getByText('Certificado emitido.')).toBeHidden();

  // The new certificate is in the company's list; a second issue of the same month is refused before asking.
  await page.getByRole('link', { name: /Volver a/ }).click();
  await expect(page.locator('table a.row-link').first()).toHaveText('Setiembre 2026');
  await page.getByRole('link', { name: 'Emitir certificado' }).click();
  await reviewMonth(page, '2026-09');
  await expect(page.getByRole('alert')).toContainText('Esta empresa ya tiene el certificado de Setiembre 2026');
  await expect(page.getByRole('button', { name: /Emitir certificado de/ })).toHaveCount(0);
  expect(writes).toHaveLength(1);
  expect(unmocked).toEqual([]);
});

test('a period that has not ended in Lima is refused, saying from when it can be issued', async ({ page }) => {
  const { writes, unmocked } = await open(page);
  const thisMonth = limaDate(0).slice(0, 7);
  await reviewMonth(page, thisMonth);
  await expect(page.getByRole('alert')).toContainText(/Este período aún no termina\. Podrás emitirlo desde el 01\/\d{2}\/\d{4}\./);
  await expect(page.getByRole('article', { name: /Borrador/ })).toHaveCount(0);

  await page.getByLabel('Otro rango').check();
  await page.getByLabel('Desde').fill(limaDate(-5));
  await page.getByLabel('Hasta').fill(limaDate(0));
  await page.getByRole('button', { name: 'Revisar el borrador' }).click();
  const tomorrow = limaDate(1).split('-').reverse().join('/');
  await expect(page.getByRole('alert')).toHaveText(`Este período aún no termina. Podrás emitirlo desde el ${tomorrow}.`);
  expect(writes).toEqual([]);
  expect(unmocked).toEqual([]);
});

test('0 kg is refused: no collections in the period', async ({ page }) => {
  const { unmocked } = await open(page);
  // 1–20 September: covered by the SIGERSOL record, but the month's only collection is on the 28th.
  await page.getByLabel('Otro rango').check();
  await page.getByLabel('Desde').fill('2026-09-01');
  await page.getByLabel('Hasta').fill('2026-09-20');
  await page.getByRole('button', { name: 'Revisar el borrador' }).click();
  await expect(page.getByRole('alert')).toContainText('No hay recojos de la asociación registrados en 01/09/2026 – 20/09/2026 (0 kg)');
  await expect(page.getByLabel('Revisé la empresa, el período y las cifras')).toHaveCount(0);
  expect(unmocked).toEqual([]);
});

test('without SIGERSOL coverage there is no button, only the prefilled form', async ({ page }) => {
  const { unmocked } = await open(page);
  await reviewMonth(page, '2026-08');
  await expect(page.getByRole('alert')).toContainText('No hay un registro SIGERSOL de la asociación que cubra todo Agosto 2026');
  await expect(page.getByRole('button', { name: /Emitir certificado de/ })).toHaveCount(0);
  await page.getByRole('link', { name: 'Registrar el dato SIGERSOL de este período' }).click();
  await expect(page.getByLabel('Asociación')).toHaveValue(ENVASES.associationId);
  await expect(page.getByLabel('Desde')).toHaveValue('2026-08-01');
  await expect(page.getByLabel('Hasta')).toHaveValue('2026-08-31');
  expect(unmocked).toEqual([]);
});

test('RPT-004 and RPT-005 from the service, and an unknown outcome', async ({ page }) => {
  for (const [failure, text] of [
    [{ status: 409, code: 'RPT-004' }, 'ya tiene un certificado que se superpone con Setiembre 2026'],
    [{ status: 409, code: 'RPT-005' }, 'No hay un registro SIGERSOL de la asociación que cubra todo Setiembre 2026'],
    [{ status: 504, code: null }, 'No pudimos confirmar si el certificado se emitió'],
  ] as const) {
    const { writes, unmocked } = await open(page, { issueFailure: failure });
    await reviewMonth(page, '2026-09');
    await page.getByLabel('Revisé la empresa, el período y las cifras').check();
    await page.getByRole('button', { name: 'Emitir certificado de Setiembre 2026' }).click();
    const alert = page.locator('.issue-problem');
    await expect(alert).toContainText(text);
    await expect(alert).toBeFocused();
    await expect(page.getByLabel('Revisé la empresa, el período y las cifras')).not.toBeChecked();
    expect(writes).toHaveLength(1);
    expect(unmocked).toEqual([]);
    await page.unrouteAll({ behavior: 'ignoreErrors' });
    await page.evaluate(() => sessionStorage.clear());
  }
});

for (const width of WIDTHS) {
  test(`issue screen at ${width} px: no sideways scroll, axe clean, with the draft and the confirmation`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    const { unmocked } = await open(page);
    await expectNoSidewaysScroll(page);
    await checkA11y(page);
    await reviewMonth(page, '2026-09');
    await expect(page.getByRole('button', { name: 'Emitir certificado de Setiembre 2026' })).toBeVisible();
    await expectNoSidewaysScroll(page);
    await checkA11y(page);
    expect(unmocked).toEqual([]);
  });
}
