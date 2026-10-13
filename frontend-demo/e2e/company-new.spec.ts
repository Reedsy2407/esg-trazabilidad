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

test('register a company: validated RUC, the name comes from SUNAT, exactly the request fields, then it opens and is listed', async ({
  page,
}) => {
  const { writes, unmocked } = await open(page);

  await expect(page.getByLabel('Razón social')).toHaveCount(0);
  await page.getByLabel('RUC').fill('2051234');
  await page.getByRole('button', { name: 'Registrar empresa' }).click();
  await expect(page.getByText('El RUC debe tener exactamente 11 dígitos (tiene 7).')).toBeVisible();
  await expect(page.getByText('Elige la asociación.')).toBeVisible();
  await expect(page.getByLabel('RUC')).toBeFocused();
  expect(writes).toEqual([]);

  await page.getByLabel('RUC').fill('20 548 712 336');
  await page.getByLabel('Asociación que recoge sus residuos').selectOption({ label: ASSOCIATIONS[0].name });
  await page.getByLabel(/Persona de contacto/).fill('Rosa Quispe');
  await page.getByRole('button', { name: 'Registrar empresa' }).click();

  await expect(page.getByRole('heading', { name: 'PLÁSTICOS ANDINOS S.A.C.', level: 1 })).toBeVisible();
  await expect(page.getByRole('status').filter({ hasText: 'Empresa registrada.' })).toBeVisible();
  await expect(page.getByRole('region', { name: 'SUNAT y contacto' })).toContainText(/Contacto\s*Rosa Quispe/);
  expect(writes).toEqual([
    {
      method: 'POST',
      path: '/svc/reporting/tracked-companies',
      body: { ruc: '20548712336', associationId: ASSOCIATIONS[0].id, contactName: 'Rosa Quispe' },
    },
  ]);

  await page.getByRole('link', { name: 'Volver a empresas' }).click();
  await expect(page.locator('a.card')).toHaveCount(COMPANIES.length + 1);
  await expect(page.getByRole('link', { name: /PLÁSTICOS ANDINOS/ })).toBeVisible();
  expect(unmocked).toEqual([]);
});

test('SUNAT refuses or does not answer: the message says what happened and the form keeps what was typed', async ({ page }) => {
  const { unmocked } = await open(page, { companyFailure: { status: 503, code: 'RPT-011' } });
  await page.getByLabel('RUC').fill('20548712336');
  await page.getByLabel('Asociación que recoge sus residuos').selectOption({ label: ASSOCIATIONS[0].name });
  await page.getByRole('button', { name: 'Registrar empresa' }).click();
  await expect(page.getByRole('alert')).toHaveText('No se pudo consultar SUNAT en este momento. Vuelve a intentarlo en un rato.');
  await expect(page.getByLabel('RUC')).toHaveValue('20548712336');
  expect(unmocked).toEqual([]);
});

test('SUNAT down: register as unverified with a typed name, then verify it from the company page', async ({ page }) => {
  const { writes, unmocked } = await open(page, { companyFailure: { status: 503, code: 'RPT-011' } });
  await page.getByLabel('RUC').fill('20548712336');
  await page.getByLabel('Asociación que recoge sus residuos').selectOption({ label: ASSOCIATIONS[0].name });
  await expect(page.getByRole('button', { name: 'Registrar como no verificada' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Registrar empresa' }).click();
  await expect(page.getByRole('alert')).toContainText('No se pudo consultar SUNAT');

  await page.getByRole('button', { name: 'Registrar como no verificada' }).click();
  await expect(page.getByText('Escribe la razón social de la empresa.')).toBeVisible();
  await checkA11y(page);
  await page.getByLabel('Razón social').fill('Plásticos Andinos (a mano)');
  await page.getByRole('button', { name: 'Registrar como no verificada' }).click();

  await expect(page.getByRole('heading', { name: 'Plásticos Andinos (a mano)', level: 1 })).toBeVisible();
  await expect(page.getByText('No verificada', { exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Emitir certificado' })).toHaveCount(0);
  expect(writes.at(-1)).toEqual({
    method: 'POST',
    path: '/svc/reporting/tracked-companies/unverified',
    body: { ruc: '20548712336', associationId: ASSOCIATIONS[0].id, name: 'Plásticos Andinos (a mano)' },
  });

  await page.getByRole('button', { name: 'Verificar con SUNAT' }).click();
  await expect(page.getByRole('heading', { name: 'PLÁSTICOS ANDINOS S.A.C.', level: 1 })).toBeVisible();
  await expect(page.getByText('Empresa verificada con SUNAT.')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Emitir certificado' })).toBeVisible();
  expect(unmocked).toEqual([]);
});

test('Verificar con SUNAT while SUNAT is down leaves the company unverified and says so', async ({ page }) => {
  const { unmocked } = await open(page, {
    companyFailure: { status: 503, code: 'RPT-011' },
    refreshFailure: { status: 503, code: 'RPT-011' },
  });
  await page.getByLabel('RUC').fill('20548712336');
  await page.getByLabel('Asociación que recoge sus residuos').selectOption({ label: ASSOCIATIONS[0].name });
  await page.getByRole('button', { name: 'Registrar empresa' }).click();
  await page.getByLabel('Razón social').fill('Plásticos Andinos (a mano)');
  await page.getByRole('button', { name: 'Registrar como no verificada' }).click();
  await page.getByRole('button', { name: 'Verificar con SUNAT' }).click();
  await expect(page.getByRole('alert')).toContainText('sigue sin verificar');
  await expect(page.getByText('No verificada', { exact: true })).toBeVisible();
  expect(unmocked).toEqual([]);
});

test('a RUC already registered (RPT-002) says so plainly and points to the list', async ({ page }) => {
  const { writes, unmocked } = await open(page);
  await page.getByLabel('RUC').fill(COMPANIES[0].ruc);
  await page.getByLabel('Asociación que recoge sus residuos').selectOption({ label: ASSOCIATIONS[0].name });
  await page.getByRole('button', { name: 'Registrar empresa' }).click();

  const alert = page.getByRole('alert');
  await expect(alert).toContainText(
    `Ya hay una empresa registrada con el RUC ${COMPANIES[0].ruc}. No se puede registrar dos veces: búscala en la lista de empresas.`,
  );
  // Editing the RUC clears the message.
  await page.getByLabel('RUC').fill('20548712336');
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
  await page.getByLabel('RUC').fill('20548712336');
  await page.getByLabel('Asociación que recoge sus residuos').selectOption({ label: ASSOCIATIONS[1].name + ' (suspendida)' });
  await page.getByRole('button', { name: 'Registrar empresa' }).click();
  await expect(page.getByRole('alert')).toHaveText(
    'No pudimos confirmar si la empresa quedó registrada. Revisa la lista de empresas antes de volver a intentarlo.',
  );
  await expect(page.getByLabel('RUC')).toHaveValue('20548712336');
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
