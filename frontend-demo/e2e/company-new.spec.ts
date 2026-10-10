import { Page, expect, test } from '@playwright/test';

import { ASSOCIATIONS, BackendOptions, COMPANIES } from './backend';
import { WIDTHS, checkA11y, expectNoSidewaysScroll, signedIn } from './session';

async function open(page: Page, options: BackendOptions = {}) {
  const writes: NonNullable<BackendOptions['writes']> = [];
  const unmocked = await signedIn(page, '/empresas', { ...options, writes });
  await page.getByRole('link', { name: 'Registrar empresa' }).click();
  await expect(page.getByRole('heading', { name: 'Registrar empresa', level: 1 })).toBeVisible();
  await expect(page.getByLabel('Asociación que recoge sus residuos').locator('option')).toHaveCount(ASSOCIATIONS.length + 1);
  return { writes, unmocked };
}

test('register a company: validated RUC, exactly the request fields, then it opens and is listed', async ({ page }) => {
  const { writes, unmocked } = await open(page);

  await page.getByLabel('RUC').fill('2051234');
  await page.getByRole('button', { name: 'Registrar empresa' }).click();
  await expect(page.getByText('Escribe la razón social de la empresa.')).toBeVisible();
  await expect(page.getByText('El RUC debe tener exactamente 11 dígitos (tiene 7).')).toBeVisible();
  await expect(page.getByText('Elige la asociación.')).toBeVisible();
  await expect(page.getByLabel('Razón social')).toBeFocused();
  expect(writes).toEqual([]);

  await page.getByLabel('Razón social').fill('Plásticos Andinos S.A.C.');
  await page.getByLabel('RUC').fill('20 548 712 335');
  await page.getByLabel('Asociación que recoge sus residuos').selectOption({ label: ASSOCIATIONS[0].name });
  await page.getByRole('button', { name: 'Registrar empresa' }).click();

  await expect(page.getByRole('heading', { name: 'Plásticos Andinos S.A.C.', level: 1 })).toBeVisible();
  await expect(page.getByRole('status').filter({ hasText: 'Empresa registrada.' })).toBeVisible();
  expect(writes).toEqual([
    {
      method: 'POST',
      path: '/svc/reporting/tracked-companies',
      body: { name: 'Plásticos Andinos S.A.C.', ruc: '20548712335', associationId: ASSOCIATIONS[0].id },
    },
  ]);

  await page.getByRole('link', { name: 'Volver a empresas' }).click();
  await expect(page.locator('a.card')).toHaveCount(COMPANIES.length + 1);
  await expect(page.getByRole('link', { name: /Plásticos Andinos/ })).toBeVisible();
  expect(unmocked).toEqual([]);
});

test('a RUC already registered (RPT-002) says so plainly and points to the list', async ({ page }) => {
  const { writes, unmocked } = await open(page);
  await page.getByLabel('Razón social').fill('Otra razón social');
  await page.getByLabel('RUC').fill(COMPANIES[0].ruc);
  await page.getByLabel('Asociación que recoge sus residuos').selectOption({ label: ASSOCIATIONS[0].name });
  await page.getByRole('button', { name: 'Registrar empresa' }).click();

  const alert = page.getByRole('alert');
  await expect(alert).toContainText(
    `Ya hay una empresa registrada con el RUC ${COMPANIES[0].ruc}. No se puede registrar dos veces: búscala en la lista de empresas.`,
  );
  // Editing the RUC clears the message.
  await page.getByLabel('RUC').fill('20548712335');
  await expect(alert).toHaveCount(0);
  await page.getByLabel('RUC').fill(COMPANIES[0].ruc);
  await page.getByRole('button', { name: 'Registrar empresa' }).click();
  await alert.getByRole('link', { name: 'Ver la lista de empresas' }).click();
  await expect(page).toHaveURL(/\/empresas$/);
  expect(writes).toHaveLength(2);
  expect(unmocked).toEqual([]);
});

test('no answer: the outcome is unknown and the form keeps what was typed', async ({ page }) => {
  const { unmocked } = await open(page, { companyFailure: { status: 504, code: null } });
  await page.getByLabel('Razón social').fill('Plásticos Andinos S.A.C.');
  await page.getByLabel('RUC').fill('20548712335');
  await page.getByLabel('Asociación que recoge sus residuos').selectOption({ label: ASSOCIATIONS[1].name + ' (suspendida)' });
  await page.getByRole('button', { name: 'Registrar empresa' }).click();
  await expect(page.getByRole('alert')).toHaveText(
    'No pudimos confirmar si la empresa quedó registrada. Revisa la lista de empresas antes de volver a intentarlo.',
  );
  await expect(page.getByLabel('RUC')).toHaveValue('20548712335');
  expect(unmocked).toEqual([]);
});

test('the empty list invites to register the first company', async ({ page }) => {
  const unmocked = await signedIn(page, '/empresas', { companyLists: ['empty'] });
  await page.getByRole('link', { name: 'Registra la primera' }).click();
  await expect(page).toHaveURL(/\/empresas\/nueva$/);
  expect(unmocked).toEqual([]);
});

for (const width of WIDTHS) {
  test(`register company at ${width} px: no sideways scroll, axe clean, empty and with errors`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    const { unmocked } = await open(page);
    await expectNoSidewaysScroll(page);
    await checkA11y(page);
    await page.getByRole('button', { name: 'Registrar empresa' }).click();
    await expect(page.getByText('Elige la asociación.')).toBeVisible();
    await expectNoSidewaysScroll(page);
    await checkA11y(page);
    expect(unmocked).toEqual([]);
  });
}
