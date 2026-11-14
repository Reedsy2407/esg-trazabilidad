import { Page, expect, test } from '@playwright/test';

import { ASSOCIATIONS, BackendOptions, FIELD_RECYCLERS } from './backend';
import { checkA11y, expectNoSidewaysScroll, signedIn } from './session';

const ACTIVE = FIELD_RECYCLERS[0];
const INACTIVE = FIELD_RECYCLERS[1];
const path = (recyclerId: string) => `/asociaciones/${ASSOCIATIONS[0].id}/recicladores/${recyclerId}/cronogramas`;

async function open(page: Page, recyclerId = ACTIVE.id, options: BackendOptions = {}) {
  const writes: NonNullable<BackendOptions['writes']> = [];
  const unmocked = await signedIn(page, path(recyclerId), { ...options, writes });
  return { writes, unmocked };
}

async function pickLima(page: Page) {
  const combo = page.getByRole('combobox', { name: 'Distrito', exact: true });
  await combo.fill('lima');
  await expect(combo).toHaveAttribute('aria-expanded', 'true');
  await combo.press('Enter');
  await expect(page.getByRole('group', { name: /Cronogramas de Lima/ })).toBeVisible();
}

const row = (page: Page, name: string) => page.locator('.pick-list label', { hasText: name });

test('the console of a recycler leads to its schedules, which start with what it already has', async ({ page }) => {
  const writes: NonNullable<BackendOptions['writes']> = [];
  const unmocked = await signedIn(page, `/asociaciones/${ASSOCIATIONS[0].id}/recicladores/${ACTIVE.id}`, { writes });
  await page.getByRole('link', { name: 'Cronogramas asignados' }).click();
  await expect(page).toHaveURL(new RegExp(`/recicladores/${ACTIVE.id}/cronogramas$`));
  await expect(page.getByRole('heading', { name: `Cronogramas de ${ACTIVE.fullName}`, level: 1 })).toBeVisible();
  const assigned = page.locator('table.ledger tbody tr');
  await expect(assigned).toHaveCount(2);
  await expect(assigned.first()).toContainText('Carmen Rojas Vera');
  await expect(assigned.first()).toContainText('Miércoles');
  await expect(assigned.first()).toContainText('09:00');
  expect(unmocked).toEqual([]);
});

test('one by one: free and other recyclers\' schedules are told apart, a free one is assigned and the counts are shown', async ({ page }) => {
  const { writes, unmocked } = await open(page);
  await pickLima(page);
  await expect(row(page, 'Ana Flores Torres')).toContainText('Libre');
  await expect(row(page, 'Beto Díaz Soto')).toContainText('De otro reciclador');
  await expect(row(page, 'Carmen Rojas Vera')).toContainText('Ya es de este reciclador');
  await expect(row(page, 'Carmen Rojas Vera').getByRole('checkbox')).toBeDisabled();

  await row(page, 'Ana Flores Torres').getByRole('checkbox').check();
  await page.getByRole('button', { name: 'Asignar 1 marcado' }).click();

  await expect(page.getByRole('status').first()).toContainText('Asignación guardada. Se movieron 0 desde otro reciclador. Ahora tiene 3 en total.');
  expect(writes.filter((w) => w.method === 'PUT').map((w) => w.body)).toEqual([
    { scheduleIds: ['0192f3a8-e000-7000-8000-000000000003', '0192f3a8-e000-7000-8000-000000000005', '0192f3a8-e000-7000-8000-000000000001'] },
  ]);
  await expect(page.locator('table.ledger tbody tr')).toHaveCount(3);
  await expect(row(page, 'Ana Flores Torres')).toContainText('Ya es de este reciclador');
  expect(unmocked).toEqual([]);
});

test('moving another recycler\'s schedule is shown and confirmed first; the safe way out is the filled button', async ({ page }) => {
  const { writes, unmocked } = await open(page);
  await pickLima(page);
  await row(page, 'Beto Díaz Soto').getByRole('checkbox').check();
  await page.getByRole('button', { name: 'Asignar 1 marcado' }).click();

  const confirm = page.getByRole('group', { name: 'Confirmar el movimiento de cronogramas' });
  await expect(confirm).toContainText('de otro reciclador');
  await expect(confirm).toBeFocused();
  expect(writes).toEqual([]);
  await expect(confirm.getByRole('button', { name: 'No, volver' })).toHaveClass(/btn-primary/);
  await expect(confirm.getByRole('button', { name: 'Sí, mover y asignar' })).not.toHaveClass(/btn-primary/);

  await confirm.getByRole('button', { name: 'No, volver' }).click();
  await expect(confirm).toHaveCount(0);
  expect(writes).toEqual([]);

  await page.getByRole('button', { name: 'Asignar 1 marcado' }).click();
  await page.getByRole('button', { name: 'Sí, mover y asignar' }).click();
  await expect(page.getByRole('status').first()).toContainText('Se movieron 1 desde otro reciclador');
  expect(writes).toHaveLength(1);
  expect(unmocked).toEqual([]);
});

test('the whole district: confirmed, then the three counts the service answered', async ({ page }) => {
  const { writes, unmocked } = await open(page);
  await pickLima(page);
  await page.getByRole('button', { name: 'Asignar todo Lima' }).click();
  const confirm = page.getByRole('group', { name: 'Confirmar la asignación del distrito' });
  await expect(confirm).toContainText('se omiten');
  expect(writes).toEqual([]);
  await confirm.getByRole('button', { name: 'Sí, asignar el distrito' }).click();

  const result = page.getByRole('region', { name: 'Resultado en Lima' });
  await expect(result.locator('dd')).toHaveText(['2', '1', '1']);
  await expect(result).toContainText('Asignados');
  await expect(result).toContainText('Omitidos: de otro reciclador');
  expect(writes).toEqual([{ method: 'POST', path: '/svc/collection/assignments/bulk', body: { recyclerId: ACTIVE.id, districtUbigeo: '150101' } }]);

  // Repeating changes nothing: the free ones are gone.
  await page.getByRole('button', { name: 'Asignar todo Lima' }).click();
  await page.getByRole('button', { name: 'Sí, asignar el distrito' }).click();
  await expect(result.locator('dd')).toHaveText(['0', '1', '1']);
  expect(unmocked).toEqual([]);
});

test('a district with no schedules says so, and an inactive recycler cannot be assigned anything', async ({ page }) => {
  const { unmocked } = await open(page, INACTIVE.id);
  await expect(page.getByText('Este reciclador está inactivo')).toBeVisible();
  await expect(page.getByText('Todavía no tiene cronogramas.')).toBeVisible();
  await pickLima(page);
  await expect(row(page, 'Ana Flores Torres').getByRole('checkbox')).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Asignar todo Lima' })).toBeDisabled();

  const combo = page.getByRole('combobox', { name: 'Distrito', exact: true });
  await combo.fill('comas');
  await combo.press('Enter');
  await expect(page.getByText('Comas no tiene cronogramas activos de vecinos activos.')).toBeVisible();
  expect(unmocked).toEqual([]);
});

test('a rejected assignment is an alert with the reason, and nothing is reported as done', async ({ page }) => {
  await open(page, ACTIVE.id, { assignmentFailure: { status: 403, code: 'COL-019' } });
  await pickLima(page);
  await row(page, 'Ana Flores Torres').getByRole('checkbox').check();
  await page.getByRole('button', { name: 'Asignar 1 marcado' }).click();
  await expect(page.getByRole('alert')).toContainText('El reciclador no está activo');
  await expect(page.getByRole('status').first()).toHaveText('');
});

test('a service that does not answer is an alert with a retry', async ({ page }) => {
  await open(page, ACTIVE.id, { fieldStatus: 503 });
  await expect(page.getByRole('alert').first()).toContainText('No se pudieron cargar los cronogramas asignados');
  await expect(page.getByRole('button', { name: 'Volver a intentar' }).first()).toBeVisible();
});

for (const width of [360, 768, 1280, 1920]) {
  test(`the schedules screen has no sideways scroll and no AXE violations at ${width} px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await open(page);
    await pickLima(page);
    await row(page, 'Beto Díaz Soto').getByRole('checkbox').check();
    await page.getByRole('button', { name: 'Asignar 1 marcado' }).click();
    await expect(page.getByRole('group', { name: 'Confirmar el movimiento de cronogramas' })).toBeVisible();
    await expectNoSidewaysScroll(page);
    await checkA11y(page);
    // Touch targets of at least 44 px for every control on the screen.
    const small = await page.evaluate(() =>
      [...document.querySelectorAll<HTMLElement>('main button, main a.btn, main label.pick, main input[type=text], main [role=combobox]')]
        .map((el) => ({ name: el.textContent?.trim().slice(0, 30), h: el.getBoundingClientRect().height }))
        .filter((c) => c.h > 0 && c.h < 44),
    );
    expect(small).toEqual([]);
  });
}
