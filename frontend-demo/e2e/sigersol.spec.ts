import { Page, expect, test } from '@playwright/test';

import { ASSOCIATIONS, BackendOptions, COMPANIES } from './backend';
import { WIDTHS, checkA11y, expectManualTagSpaced, expectNoSidewaysScroll, signedIn } from './session';

const RIMAC = ASSOCIATIONS[0];

async function open(page: Page, path: string, options: BackendOptions = {}) {
  const writes: NonNullable<BackendOptions['writes']> = [];
  const unmocked = await signedIn(page, path, { ...options, writes });
  return { writes, unmocked };
}

test('the records are always labelled as manual entry, newest period first, filtered by association', async ({ page }) => {
  const { unmocked } = await open(page, '/empresas');
  await page.getByRole('navigation', { name: 'Secciones' }).getByRole('link', { name: 'SIGERSOL' }).click();
  await expect(page.getByRole('heading', { name: 'Registros SIGERSOL', level: 1 })).toBeVisible();
  await expect(page.locator('app-manual-tag')).toHaveText('Ingreso manual');
  await expect(page.getByText('Cifras declaradas por el operador, ingresadas a mano; esta aplicación no se conecta con SIGERSOL ni las verifica.')).toBeVisible();

  const rows = page.locator('table.ledger tbody tr');
  await expect(rows).toHaveCount(2);
  await expect(rows.first()).toContainText('Setiembre 2026');
  await expect(rows.first()).toContainText('91.25 %');
  await expect(rows.first()).toContainText('No declarado');
  // A record made before v3 has no origin: said plainly, never guessed.
  await expect(rows.first()).toContainText('Dato anterior al registro de origen');
  await expect(rows.nth(1)).toContainText('Con comprobante referenciado');
  await expect(rows.nth(1)).toContainText('a.paredes@example.com');
  await expect(rows.nth(1)).toContainText('Diciembre 2024');
  await expect(rows.nth(1)).toContainText('12,500.00 kg');
  await expect(rows.nth(1)).toContainText('copiado por A. Paredes');

  await page.getByLabel('Asociación', { exact: true }).selectOption({ label: ASSOCIATIONS[1].name });
  await expect(page).toHaveURL(new RegExp(`asociacion=${ASSOCIATIONS[1].id}`));
  await expect(page.getByText('Esta asociación todavía no tiene registros SIGERSOL.')).toBeVisible();
  expect(unmocked).toEqual([]);
});

test('register a record: the request is exactly the form, and it shows up as manual entry', async ({ page }) => {
  const { writes, unmocked } = await open(page, `/sigersol?asociacion=${ASSOCIATIONS[1].id}`);
  await page.getByRole('link', { name: 'Registrar dato SIGERSOL' }).click();
  await expect(page.getByRole('heading', { name: 'Registrar dato SIGERSOL', level: 1 })).toBeVisible();
  await expect(page.getByLabel('Asociación')).toHaveValue(ASSOCIATIONS[1].id);

  await page.getByRole('button', { name: 'Registrar dato SIGERSOL' }).click();
  await expect(page.getByText('Escribe las dos fechas del período.')).toBeVisible();
  await expect(page.getByText('Escribe el porcentaje de cumplimiento.')).toBeVisible();
  await expect(page.getByLabel('Desde')).toBeFocused();
  expect(writes).toEqual([]);
  // The hint stays tied to the dates while they are in error.
  await expect(page.getByLabel('Desde')).toHaveAttribute('aria-describedby', 'period-hint period-error');

  await page.getByLabel('Desde').fill('2026-10-01');
  await page.getByLabel('Hasta').fill('2026-10-31');
  await expect(page.getByText('Ambas fechas incluidas: Octubre 2026.')).toBeVisible();
  await page.getByLabel('Cumplimiento de jerarquía (declarado)').fill('84.25');
  await page.getByLabel('Kilos declarados (opcional)').fill('3200.5');
  await page.getByLabel('Nota sobre la fuente (opcional)').fill('Reporte mensual de octubre');
  await page.getByLabel('Origen del dato').selectOption('REFERENCED_PROOF');
  await page.getByLabel(/Referencia del comprobante/).fill('constancia-octubre.png');
  await page.getByRole('button', { name: 'Registrar dato SIGERSOL' }).click();

  await expect(page).toHaveURL(new RegExp(`/sigersol\\?asociacion=${ASSOCIATIONS[1].id}$`));
  await expect(page.getByRole('status').filter({ hasText: 'Dato SIGERSOL registrado como ingreso manual.' })).toBeVisible();
  await expect(page.locator('table.ledger tbody tr')).toHaveCount(1);
  await expect(page.locator('table.ledger tbody tr')).toContainText('Octubre 2026');
  await expect(page.locator('table.ledger tbody tr')).toContainText('Con comprobante referenciado');
  await expect(page.locator('table.ledger tbody tr')).toContainText('constancia-octubre.png');
  expect(writes).toEqual([
    {
      method: 'POST',
      path: '/svc/reporting/sigersol-syncs',
      body: {
        associationId: ASSOCIATIONS[1].id,
        periodStart: '2026-10-01',
        periodEnd: '2026-10-31',
        hierarchyCompliancePercent: 84.25,
        officialKilosDeclared: 3200.5,
        sourceNote: 'Reporte mensual de octubre',
        origin: 'REFERENCED_PROOF',
        reference: 'constancia-octubre.png',
      },
    },
  ]);
  expect(unmocked).toEqual([]);
});

test('a period sharing even one day with another record of the association is refused (RPT-006)', async ({ page }) => {
  const { writes, unmocked } = await open(page, `/sigersol/nuevo?asociacion=${RIMAC.id}`);
  await expect(page.getByLabel('Asociación')).toHaveValue(RIMAC.id);
  await page.getByLabel('Desde').fill('2026-09-30');
  await page.getByLabel('Hasta').fill('2026-10-31');
  await page.getByLabel('Cumplimiento de jerarquía (declarado)').fill('90');
  await page.getByRole('button', { name: 'Registrar dato SIGERSOL' }).click();
  const alert = page.getByRole('alert');
  await expect(alert).toContainText('ya tiene un registro SIGERSOL que se superpone con ese período (compartir un solo día también cuenta)');
  await alert.getByRole('link', { name: 'Ver los registros de esta asociación' }).click();
  await expect(page).toHaveURL(new RegExp(`/sigersol\\?asociacion=${RIMAC.id}$`));
  expect(writes).toHaveLength(1);
  expect(unmocked).toEqual([]);
});

test('the prefilled form from an address (V5 links here on RPT-005)', async ({ page }) => {
  const { unmocked } = await open(page, `/sigersol/nuevo?asociacion=${RIMAC.id}&desde=2026-10-01&hasta=2026-10-31`);
  await expect(page.getByLabel('Asociación')).toHaveValue(RIMAC.id);
  await expect(page.getByLabel('Desde')).toHaveValue('2026-10-01');
  await expect(page.getByLabel('Hasta')).toHaveValue('2026-10-31');
  expect(unmocked).toEqual([]);
});

test('no answer: an unknown outcome, never a failure', async ({ page }) => {
  const { unmocked } = await open(page, `/sigersol/nuevo?asociacion=${RIMAC.id}&desde=2026-10-01&hasta=2026-10-31`);
  await page.getByLabel('Cumplimiento de jerarquía (declarado)').fill('90');
  await page.route('**/svc/reporting/sigersol-syncs', (route) => route.fulfill({ status: 502, body: 'Bad Gateway' }));
  await page.getByRole('button', { name: 'Registrar dato SIGERSOL' }).click();
  await expect(page.getByRole('alert')).toHaveText(
    'No pudimos confirmar si el dato SIGERSOL quedó registrado. Revisa los registros de la asociación antes de volver a intentarlo.Ver los registros de esta asociación',
  );
  expect(unmocked).toEqual([]);
});

test('the company summary and the certificate say the compliance is declared, not an official SIGERSOL figure', async ({ page }) => {
  const { unmocked } = await open(page, `/empresas/${COMPANIES[0].id}`);
  await expect(
    page.getByText('El cumplimiento de jerarquía es un dato declarado por el operador de la asociación; no es una cifra oficial de SIGERSOL ni la verifica esta plataforma.'),
  ).toBeVisible();
  await page.locator('table a.row-link').first().click();
  const note = page.locator('article.sheet .source');
  await expect(note).toContainText('El cumplimiento de jerarquía es un dato declarado por el operador de la asociación (origen: ');
  await expect(note).toContainText('; no es una cifra oficial de SIGERSOL ni la verifica esta plataforma.');
  await expect(page.getByText('Cumplimiento de jerarquía (declarado)')).toBeVisible();
  expect(unmocked).toEqual([]);
});

test('the list and the form say the figures are declared and typed in; the proof reference is asked for only with a referenced proof', async ({ page }) => {
  const { writes, unmocked } = await open(page, `/sigersol/nuevo?asociacion=${RIMAC.id}&desde=2026-10-01&hasta=2026-10-31`);
  await expect(
    page.getByText('Cifras declaradas por el operador, ingresadas a mano; esta aplicación no se conecta con SIGERSOL ni las verifica.'),
  ).toBeVisible();
  await expect(page.getByLabel(/Referencia del comprobante/)).toHaveCount(0);

  await page.getByLabel('Cumplimiento de jerarquía (declarado)').fill('90');
  await page.getByLabel('Origen del dato').selectOption('REFERENCED_PROOF');
  const reference = page.getByLabel(/Referencia del comprobante/);
  await expect(reference).toBeVisible();
  await expect(page.getByText('Queda como «referenciado, no verificado por SIGERSOL».')).toBeVisible();
  await page.getByRole('button', { name: 'Registrar dato SIGERSOL' }).click();
  await expect(page.getByText('Escribe la referencia del comprobante: el código de envío, o el número y la fecha.')).toBeVisible();
  await expect(reference).toBeFocused();
  expect(writes).toEqual([]);

  await reference.fill('Informe 2025 · envío 8841');
  await page.getByRole('button', { name: 'Registrar dato SIGERSOL' }).click();
  await expect(page).toHaveURL(new RegExp(`/sigersol\\?asociacion=${RIMAC.id}$`));
  await expect(page.getByText('Cifras declaradas por el operador, ingresadas a mano; esta aplicación no se conecta con SIGERSOL ni las verifica.')).toBeVisible();
  expect(writes[0]).toMatchObject({ body: { origin: 'REFERENCED_PROOF', reference: 'Informe 2025 · envío 8841' } });
  expect(unmocked).toEqual([]);
});

test('the list fails: the reason and a retry', async ({ page }) => {
  const { unmocked } = await open(page, '/sigersol', { sigersolStatus: 503 });
  await expect(page.getByRole('alert')).toContainText(
    'No se pudieron cargar los registros SIGERSOL: el servicio de reportes no respondió (HTTP 503).',
  );
  await expect(page.getByRole('button', { name: 'Volver a intentar' })).toBeVisible();
  expect(unmocked).toEqual([]);
});

for (const width of WIDTHS) {
  test(`SIGERSOL screens at ${width} px: no sideways scroll, axe clean`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    const { unmocked } = await open(page, '/sigersol');
    await expect(page.getByText('Mostrando 1–2 de 2 registros', { exact: true })).toBeVisible();
    await expectManualTagSpaced(page.locator('.sub'));
    await expectNoSidewaysScroll(page);
    await checkA11y(page);
    await page.getByRole('link', { name: 'Registrar dato SIGERSOL' }).click();
    await expectManualTagSpaced(page.locator('.lead'));
    await page.getByRole('button', { name: 'Registrar dato SIGERSOL' }).click();
    await expect(page.getByText('Escribe el porcentaje de cumplimiento.')).toBeVisible();
    await expectNoSidewaysScroll(page);
    await checkA11y(page);
    expect(unmocked).toEqual([]);
  });
}
