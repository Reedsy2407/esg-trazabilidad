import { Page, expect, test } from '@playwright/test';

import { ASSOCIATIONS, BackendOptions, NEIGHBORS } from './backend';
import { WIDTHS, checkA11y, expectNoSidewaysScroll, signedIn } from './session';

const LUIS = NEIGHBORS[0];
const ROSA = NEIGHBORS[1];

async function open(page: Page, path: string, options: BackendOptions = {}) {
  const writes: NonNullable<BackendOptions['writes']> = [];
  const unmocked = await signedIn(page, path, { ...options, writes });
  return { writes, unmocked };
}

test('list, filter by state and by exact district, then open a neighbour from the top bar', async ({ page }) => {
  const { unmocked } = await open(page, '/empresas');
  await page.getByRole('navigation', { name: 'Secciones' }).getByRole('link', { name: 'Vecinos' }).click();
  await expect(page.getByRole('heading', { name: 'Vecinos', level: 1 })).toBeVisible();
  const rows = page.locator('table.ledger tbody tr');
  await expect(rows).toHaveCount(2);
  await expect(rows.first()).toContainText(LUIS.fullName);
  await expect(rows.first()).toContainText('Inactivo');

  await page.getByRole('navigation', { name: 'Filtrar por estado' }).getByRole('link', { name: 'Activos', exact: true }).click();
  await expect(page).toHaveURL(/estado=activos/);
  await expect(rows).toHaveCount(1);
  await expect(rows.first()).toContainText(ROSA.fullName);

  // Exact, as the backend filters: a different case finds nothing, and the page says how to widen it.
  await page.getByLabel('Distrito', { exact: true }).fill('cercado de lima');
  await page.getByRole('button', { name: 'Filtrar' }).click();
  await expect(page.getByText('Ningún vecino coincide con este filtro.')).toBeVisible();
  await expect(page.getByRole('status')).toHaveText('Ningún vecino coincide.');
  await page.getByLabel('Distrito', { exact: true }).fill('Cercado de Lima');
  await page.getByRole('button', { name: 'Filtrar' }).click();
  await expect(page).toHaveURL(/distrito=Cercado/);
  await expect(rows).toHaveCount(1);

  await rows.first().click({ position: { x: 500, y: 20 } });
  await expect(page).toHaveURL(new RegExp(`/vecinos/${ROSA.id}$`));
  await expect(page.getByRole('heading', { name: ROSA.fullName, level: 1 })).toBeVisible();
  expect(unmocked).toEqual([]);
});

test('pages of 20, announced once loaded', async ({ page }) => {
  const { unmocked } = await open(page, '/vecinos', { neighborCount: 25 });
  await expect(page.getByText('Mostrando 1–20 de 25 vecinos', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Siguiente' }).click();
  await expect(page).toHaveURL(/pagina=2/);
  await expect(page.getByRole('status')).toHaveText('Mostrando 21–25 de 25 vecinos.');
  expect(unmocked).toEqual([]);
});

test('the neighbours list fails: the reason and a retry', async ({ page }) => {
  const { unmocked } = await open(page, '/vecinos', { neighborsStatus: 503 });
  await expect(page.getByRole('alert')).toContainText('No se pudieron cargar los vecinos: el servicio de recojos no respondió (HTTP 503).');
  await page.getByRole('button', { name: 'Volver a intentar' }).click();
  await expect(page.getByRole('alert')).toContainText('(HTTP 503)'); // still failing: asked again, same answer
  expect(unmocked).toEqual([]);
});

test('register a neighbour: required fields first, then exactly the request fields, and it opens', async ({ page }) => {
  const { writes, unmocked } = await open(page, '/vecinos');
  await page.getByRole('link', { name: 'Registrar vecino' }).click();
  await expect(page.getByRole('heading', { name: 'Registrar vecino', level: 1 })).toBeVisible();

  await page.getByLabel('Nombre completo').fill('   ');
  await page.getByRole('button', { name: 'Registrar vecino' }).click();
  await expect(page.getByText('Escribe el nombre del vecino.')).toBeVisible();
  await expect(page.getByText('Escribe la dirección donde se recoge.')).toBeVisible();
  await expect(page.getByLabel('Nombre completo')).toBeFocused();
  expect(writes).toEqual([]);

  await page.getByLabel('Nombre completo').fill('  Carmen Flores Ccama ');
  await page.getByLabel('Dirección').fill('Jr. Ancash 812');
  await page.getByLabel('Teléfono (opcional)').fill('  ');
  await page.getByRole('button', { name: 'Registrar vecino' }).click();

  await expect(page.getByRole('heading', { name: 'Carmen Flores Ccama', level: 1 })).toBeVisible();
  await expect(page.getByRole('status').filter({ hasText: 'Vecino registrado.' })).toBeVisible();
  expect(writes).toEqual([
    {
      method: 'POST',
      path: '/svc/collection/neighbors',
      body: { fullName: 'Carmen Flores Ccama', address: 'Jr. Ancash 812', district: null, phone: null },
    },
  ]);
  await expect(page.getByText('Este vecino todavía no tiene cronogramas.')).toBeVisible();
  await expect(page.getByText('Este vecino todavía no tiene recojos registrados.')).toBeVisible();
  expect(unmocked).toEqual([]);
});

test('register a neighbour: an unknown outcome warns against duplicating and keeps the form', async ({ page }) => {
  const { writes } = await open(page, '/vecinos/nuevo', { neighborFailure: { status: 504, code: null } });
  await page.getByLabel('Nombre completo').fill('Carmen Flores');
  await page.getByLabel('Dirección').fill('Jr. Ancash 812');
  await page.getByRole('button', { name: 'Registrar vecino' }).click();
  await expect(page.getByRole('alert')).toHaveText(
    'No pudimos confirmar si el vecino quedó registrado. Revisa la lista de vecinos antes de volver a intentarlo, para no duplicarlo.',
  );
  await expect(page.getByLabel('Nombre completo')).toHaveValue('Carmen Flores');
  expect(writes).toHaveLength(1);
});

test('schedules: Monday to Sunday; pause, reactivate, and a cancel that asks first', async ({ page }) => {
  const { writes, unmocked } = await open(page, `/vecinos/${ROSA.id}`);
  const list = page.getByRole('list', { name: 'Cronogramas del vecino' });
  await expect(list.locator('li .when')).toHaveText(['Lunes 08:00', 'Miércoles 07:00', 'Jueves 15:30']);
  await expect(list.locator('li .state')).toHaveText(['Activo', 'Activo', 'Pausado']);

  await page.getByRole('button', { name: 'Pausar cronograma del lunes 08:00' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Cronograma del lunes 08:00 pausado.' })).toBeAttached();
  await expect(list.locator('li .state').first()).toHaveText('Pausado');

  await page.getByRole('button', { name: 'Reactivar cronograma del lunes 08:00' }).click();
  await expect(list.locator('li .state').first()).toHaveText('Activo');

  // Cancel is final: it asks, and "No" sends nothing and returns focus to the button.
  await page.getByRole('button', { name: 'Cancelar cronograma del jueves 15:30' }).click();
  await expect(page.getByText('¿Cancelar el cronograma del jueves 15:30? Es definitivo')).toBeFocused();
  await page.getByRole('button', { name: 'No, mantenerlo' }).click();
  await expect(page.getByRole('button', { name: 'Cancelar cronograma del jueves 15:30' })).toBeFocused();
  const before = writes.length;
  await page.getByRole('button', { name: 'Cancelar cronograma del jueves 15:30' }).click();
  await page.getByRole('button', { name: 'Sí, cancelar cronograma' }).click();
  await expect(list.locator('li .state').nth(2)).toHaveText('Cancelado');
  await expect(page.getByRole('button', { name: /cronograma del jueves 15:30/ })).toHaveCount(0);

  const paths = writes.map((w) => `${w.method} ${w.path.split('/').slice(-1)[0]}`);
  expect(paths).toEqual(['PATCH pause', 'PATCH reactivate', 'PATCH cancel']);
  expect(before).toBe(2);
  expect(unmocked).toEqual([]);
});

test('schedules: add one, and a second active one the same day is refused with COL-002', async ({ page }) => {
  const { writes, unmocked } = await open(page, `/vecinos/${ROSA.id}`);
  await expect(page.locator('.schedules li')).toHaveCount(3);

  await page.getByRole('button', { name: 'Agregar cronograma' }).click();
  await expect(page.getByText('Elige el día del recojo.')).toBeVisible();
  expect(writes).toEqual([]);

  await page.getByLabel('Día').selectOption({ label: 'Lunes' });
  await page.getByLabel('Hora').fill('10:30');
  await page.getByRole('button', { name: 'Agregar cronograma' }).click();
  await expect(page.getByRole('alert')).toHaveText(
    'Este vecino ya tiene un cronograma activo el lunes. Pausa o cancela ese antes de agregar otro.',
  );

  await page.getByLabel('Día').selectOption({ label: 'Sábado' });
  await page.getByRole('button', { name: 'Agregar cronograma' }).click();
  await expect(page.locator('.schedules li .when')).toHaveText(['Lunes 08:00', 'Miércoles 07:00', 'Jueves 15:30', 'Sábado 10:30']);
  await expect(page.getByLabel('Día')).toHaveValue('');
  expect(writes.map((w) => w.body)).toEqual([
    { dayOfWeek: 'MONDAY', time: '10:30' },
    { dayOfWeek: 'SATURDAY', time: '10:30' },
  ]);
  expect(unmocked).toEqual([]);
});

test('collections: newest first with association and schedule names, filtered by dates', async ({ page }) => {
  const { unmocked } = await open(page, `/vecinos/${ROSA.id}`);
  const rows = page.locator('section[aria-labelledby="records-title"] table.ledger tbody tr');
  await expect(rows).toHaveCount(3);
  await expect(rows.first()).toContainText('05/10/2026');
  await expect(rows.first()).toContainText('12.50 kg');
  await expect(rows.first()).toContainText(ASSOCIATIONS[0].name);
  await expect(rows.first()).toContainText('Lunes 08:00');
  await expect(rows.nth(2)).toContainText(ASSOCIATIONS[1].name);

  await page.getByLabel('Desde').fill('2026-10-06');
  await page.getByLabel('Hasta').fill('2026-10-01');
  await page.getByRole('button', { name: 'Filtrar' }).click();
  await expect(page.getByRole('alert')).toHaveText('La fecha «desde» debe ser anterior o igual a «hasta».');

  await page.getByLabel('Desde').fill('2026-09-01');
  await page.getByLabel('Hasta').fill('2026-09-30');
  await page.getByRole('button', { name: 'Filtrar' }).click();
  await expect(rows).toHaveCount(1);
  await expect(rows.first()).toContainText('28/09/2026');

  await page.getByLabel('Desde').fill('2025-01-01');
  await page.getByLabel('Hasta').fill('2025-01-31');
  await page.getByRole('button', { name: 'Filtrar' }).click();
  await expect(page.getByText('No hay recojos en esas fechas.')).toBeVisible();
  await page.getByRole('button', { name: 'Quitar filtro' }).click();
  await expect(rows).toHaveCount(3);
  expect(unmocked).toEqual([]);
});

test('unknown neighbour, and blocks that fail on their own', async ({ page }) => {
  const { unmocked } = await open(page, `/vecinos/${ROSA.id}`, { schedulesStatus: 503, recordsStatus: 502 });
  await expect(page.getByRole('heading', { name: ROSA.fullName, level: 1 })).toBeVisible();
  await expect(page.getByRole('alert').filter({ hasText: 'los cronogramas' })).toContainText(
    'No se pudieron cargar los cronogramas: el servicio de recojos no respondió (HTTP 503).',
  );
  await expect(page.getByRole('alert').filter({ hasText: 'los recojos' })).toContainText('(HTTP 502)');

  await page.evaluate(() => {
    history.pushState({}, '', '/vecinos/0192f3a8-a000-7000-8000-0000000000aa');
    dispatchEvent(new PopStateEvent('popstate'));
  });
  await expect(page.getByRole('alert')).toContainText('Este vecino no existe.');
  expect(unmocked).toEqual([]);
});

test('collection form: from a neighbour, search, remembered association and the session log', async ({ page }) => {
  const { unmocked } = await open(page, `/vecinos/${ROSA.id}`);
  await page.getByRole('link', { name: 'Registrar recojo de este vecino' }).click();
  await expect(page).toHaveURL(new RegExp(`/recojos/nuevo\\?vecino=${ROSA.id}$`));
  const neighbor = page.getByLabel('Vecino', { exact: true });
  await expect(neighbor).toHaveValue(ROSA.id);
  await expect(page.getByText('Aún no registras recojos en esta sesión.')).toBeVisible();

  // Search: accents and case don't matter; the chosen neighbour stays in the list.
  await page.getByLabel('Buscar vecino (por nombre o distrito)').fill('huaman');
  await expect(page.getByRole('status').filter({ hasText: 'coincide' })).toHaveText('1 vecino coincide con «huaman».');
  await expect(neighbor.locator('option')).toHaveText(['Elige un vecino', `${LUIS.fullName} · ${LUIS.district} (inactivo)`, `${ROSA.fullName} · ${ROSA.district}`]);
  await page.getByLabel('Buscar vecino (por nombre o distrito)').fill('zzz');
  await expect(page.getByRole('status').filter({ hasText: 'coincide' })).toHaveText('Ningún vecino coincide con «zzz».');
  await page.getByLabel('Buscar vecino (por nombre o distrito)').fill('');

  await page.getByLabel('Asociación').selectOption({ label: ASSOCIATIONS[0].name });
  await page.getByLabel('Peso recolectado (kg)').fill('7.75');
  await page.getByRole('button', { name: 'Registrar recojo' }).click();
  await expect(page.getByText('Recojo registrado')).toBeVisible();

  const log = page.getByRole('region', { name: 'Recojos registrados en esta sesión' });
  await expect(log.locator('li')).toHaveCount(1);
  await expect(log.locator('li')).toContainText('7.75 kg');
  await expect(log.locator('li')).toContainText(ROSA.fullName);
  await expect(log.locator('li')).toContainText(ASSOCIATIONS[0].name);

  // A new visit to the form: the association used last time is chosen, the log is still there.
  await page.reload();
  await expect(page.getByLabel('Asociación')).toHaveValue(ASSOCIATIONS[0].id);
  await expect(log.locator('li')).toHaveCount(1);

  // Signing out empties the log.
  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await expect(page).toHaveURL(/\/login$/);
  expect(await page.evaluate(() => sessionStorage.getItem('esg.bitacora'))).toBeNull();
  expect(unmocked).toEqual([]);
});

for (const width of WIDTHS) {
  test(`neighbour screens at ${width} px: no sideways scroll, axe clean`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    const { unmocked } = await open(page, '/vecinos');
    await expect(page.getByText('Mostrando 1–2 de 2 vecinos', { exact: true })).toBeVisible();
    await expectNoSidewaysScroll(page);
    await checkA11y(page);

    await page.getByRole('link', { name: 'Registrar vecino' }).click();
    await page.getByRole('button', { name: 'Registrar vecino' }).click();
    await expect(page.getByText('Escribe el nombre del vecino.')).toBeVisible();
    await expectNoSidewaysScroll(page);
    await checkA11y(page);

    await page.evaluate((id) => {
      history.pushState({}, '', `/vecinos/${id}`);
      dispatchEvent(new PopStateEvent('popstate'));
    }, ROSA.id);
    await expect(page.locator('.schedules li')).toHaveCount(3);
    await page.getByRole('button', { name: 'Cancelar cronograma del lunes 08:00' }).click();
    await expect(page.getByRole('button', { name: 'Sí, cancelar cronograma' })).toBeVisible();
    await expectNoSidewaysScroll(page);
    await checkA11y(page);
    for (const button of await page.locator('main button:visible, main a.btn:visible').all()) {
      expect((await button.boundingBox())?.height ?? 0).toBeGreaterThanOrEqual(44);
    }
    expect(unmocked).toEqual([]);
  });
}
