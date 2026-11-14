import { Page, expect, test } from '@playwright/test';

import { ASSOCIATIONS, BackendOptions, FIELD_RECYCLERS, WORKDAY_IDS } from './backend';
import { checkA11y, expectNoSidewaysScroll, signedIn } from './session';

const ACTIVE = FIELD_RECYCLERS[0];
const base = `/asociaciones/${ASSOCIATIONS[0].id}/recicladores/${ACTIVE.id}`;
const list = `${base}/jornadas`;
const detail = (id: string) => `${list}/${id}`;

async function open(page: Page, path: string, options: BackendOptions = {}) {
  const writes: NonNullable<BackendOptions['writes']> = [];
  const workdayReads: string[] = [];
  const unmocked = await signedIn(page, path, { ...options, writes, workdayReads });
  return { writes, workdayReads, unmocked };
}

test('from the recycler console to its workdays, and from a workday row to its detail', async ({ page }) => {
  const { unmocked } = await open(page, base);
  await page.getByRole('link', { name: 'Jornadas y recorridos' }).click();
  await expect(page.getByRole('heading', { name: `Jornadas de ${ACTIVE.fullName}`, level: 1 })).toBeVisible();
  const rows = page.locator('table.ledger tbody tr');
  await expect(rows).toHaveCount(3);
  await expect(rows.first()).toContainText('14/11/2026');
  await expect(rows.first()).toContainText('en curso');
  await expect(rows.first()).toContainText('Sin ubicación');
  await expect(rows.nth(1)).toContainText('13/11/2026');
  await expect(rows.nth(1)).toContainText('08:05–12:30');
  await expect(rows.nth(1)).toContainText('18.45 kg');

  await rows.nth(1).getByRole('link').click();
  await expect(page).toHaveURL(new RegExp(`/jornadas/${WORKDAY_IDS.closed}$`));
  await expect(page.getByRole('heading', { name: /Jornada del 13\/11\/2026/, level: 1 })).toBeVisible();
  expect(unmocked).toEqual([]);
});

test('the date filter narrows the list in Lima days, and clearing it brings everything back', async ({ page }) => {
  const { unmocked } = await open(page, list);
  const rows = page.locator('table.ledger tbody tr');
  await expect(rows).toHaveCount(3);
  await page.getByLabel('Desde').fill('2026-11-13');
  await expect(rows).toHaveCount(2);
  await page.getByLabel('Hasta').fill('2026-11-13');
  await expect(rows).toHaveCount(1);
  await expect(rows.first()).toContainText('13/11/2026');
  await page.getByLabel('Hasta').fill('2026-07-01');
  await expect(page.getByText('La fecha «Desde» es posterior a «Hasta»')).toBeVisible();
  await expect(page.getByText('Ninguna jornada empezó en esas fechas')).toBeVisible();
  await page.getByRole('button', { name: 'Quitar filtro' }).click();
  await expect(rows).toHaveCount(3);
  await expect(page.getByLabel('Desde')).toBeFocused();
  expect(unmocked).toEqual([]);
});

test('the detail draws the route without a map, lists the check-ins and shows the photos', async ({ page }) => {
  const { unmocked } = await open(page, detail(WORKDAY_IDS.closed));
  const trace = page.getByRole('img', { name: /Recorrido de 40 puntos/ });
  await expect(trace).toBeVisible();
  await expect(trace.locator('.stop')).toHaveCount(3);
  await expect(page.locator('iframe, [class*="leaflet"], [class*="mapbox"]')).toHaveCount(0);

  const rows = page.locator('table.ledger tbody tr');
  await expect(rows).toHaveCount(4);
  await expect(rows.first()).toContainText('Ana Flores Torres');
  await expect(rows.first()).toContainText('5.20 kg');
  await expect(rows.first()).toContainText('Plástico PET');
  await expect(rows.first()).toContainText('Sí, ±18 m');
  await expect(rows.nth(2)).toContainText('Borrada por la retención');
  await expect(rows.nth(3)).toContainText('Sin foto');
  await expect(rows.nth(3)).toContainText('No');
  await expect(rows.first().locator('img')).toBeVisible();
  await expect(page.locator('table img')).toHaveCount(2);
  // The signed URL is never text on the page.
  expect(await page.locator('main').innerText()).not.toContain('data:image');
  expect(unmocked).toEqual([]);
});

test('"Recargar fotos" asks the service again and the photos come back', async ({ page }) => {
  const { workdayReads, unmocked } = await open(page, detail(WORKDAY_IDS.closed));
  await expect(page.locator('table img')).toHaveCount(2);
  const before = workdayReads.length;
  await page.getByRole('button', { name: 'Recargar fotos' }).click();
  await expect.poll(() => workdayReads.length).toBeGreaterThan(before);
  await expect(page.locator('table img')).toHaveCount(2);
  await expect(page.getByRole('heading', { name: 'Check-ins' })).toBeFocused();
  expect(unmocked).toEqual([]);
});

test('an expired photo is hidden and the screen says how to get it back', async ({ page }) => {
  await page.clock.install();
  await open(page, detail(WORKDAY_IDS.closed));
  await expect(page.locator('table img')).toHaveCount(2);
  await page.clock.fastForward('04:50');
  await expect(page.locator('table img')).toHaveCount(0);
  await expect(page.getByRole('alert')).toContainText('Recargar fotos');
  await expect(page.locator('table').getByText('Foto vencida')).toHaveCount(2);
  await page.getByRole('button', { name: 'Recargar fotos' }).click();
  await expect(page.locator('table img')).toHaveCount(2);
});

test('after the retention the route is the summary, and the photos are marked as deleted', async ({ page }) => {
  const { unmocked } = await open(page, detail(WORKDAY_IDS.summarized));
  await expect(page.getByRole('img', { name: /118 puntos, 3,18 km \(resumen simplificado\)/ })).toBeVisible();
  await expect(page.getByText('ya pasaron su plazo de retención')).toBeVisible();
  await expect(page.locator('table tbody tr')).toHaveCount(2);
  await expect(page.locator('table').getByText('Borrada por la retención')).toHaveCount(2);
  await expect(page.getByRole('button', { name: 'Recargar fotos' })).toHaveCount(0);
  expect(unmocked).toEqual([]);
});

test('a workday without location has no route and no check-ins', async ({ page }) => {
  const { unmocked } = await open(page, detail(WORKDAY_IDS.open));
  await expect(page.getByText('se hizo sin ubicación')).toBeVisible();
  await expect(page.getByText('todavía no tiene check-ins')).toBeVisible();
  await expect(page.getByRole('img', { name: /Recorrido/ })).toHaveCount(0);
  expect(unmocked).toEqual([]);
});

test('an unknown workday is an alert with a retry', async ({ page }) => {
  await open(page, detail('0192f3a8-d000-7000-8000-0000000000ff'));
  await expect(page.getByRole('alert')).toContainText('No se encontró la jornada');
  await expect(page.getByRole('button', { name: 'Volver a intentar' })).toBeVisible();
});

test('only an ADMIN can run the retention; it is confirmed and its counts are shown', async ({ page }) => {
  const { writes, unmocked } = await open(page, list);
  await page.getByRole('button', { name: 'Ejecutar retención' }).click();
  const confirm = page.getByRole('group', { name: 'Confirmar la retención' });
  await expect(confirm).toBeFocused();
  await expect(confirm.getByRole('button', { name: 'No, volver' })).toHaveClass(/btn-primary/);
  expect(writes).toEqual([]);
  await confirm.getByRole('button', { name: 'No, volver' }).click();
  await expect(page.getByRole('button', { name: 'Ejecutar retención' })).toBeFocused();
  expect(writes).toEqual([]);

  await page.getByRole('button', { name: 'Ejecutar retención' }).click();
  await page.getByRole('button', { name: 'Sí, ejecutar la retención' }).click();
  const result = page.locator('#retention-result');
  await expect(result.locator('dd')).toHaveText(['1', '40', '2', '0']);
  await expect(result).toContainText('90 días');
  expect(writes).toEqual([{ method: 'POST', path: '/svc/collection/retention/run', body: null }]);
  expect(unmocked).toEqual([]);
});

test('STAFF does not see the retention at all', async ({ page }) => {
  const { writes } = await open(page, list, { role: 'STAFF' });
  await expect(page.locator('table.ledger tbody tr')).toHaveCount(3);
  await expect(page.getByRole('button', { name: 'Ejecutar retención' })).toHaveCount(0);
  await expect(page.getByText('Retención de datos')).toHaveCount(0);
  expect(writes).toEqual([]);
});

test('a retention that fails says so and is not reported as done', async ({ page }) => {
  await open(page, list, { retentionFailure: { status: 504, code: null } });
  await page.getByRole('button', { name: 'Ejecutar retención' }).click();
  await page.getByRole('button', { name: 'Sí, ejecutar la retención' }).click();
  await expect(page.getByRole('alert')).toContainText('seguro volver a ejecutarla');
  await expect(page.locator('#retention-result')).toHaveCount(0);
});

test('a service that does not answer is an alert with a retry on the list', async ({ page }) => {
  await open(page, list, { fieldStatus: 503 });
  await expect(page.getByRole('alert')).toContainText('No se pudieron cargar las jornadas');
});

const SCREENS = [
  { name: 'la lista de jornadas', path: list },
  { name: 'el detalle de una jornada', path: detail(WORKDAY_IDS.closed) },
  { name: 'el detalle tras la retención', path: detail(WORKDAY_IDS.summarized) },
];
for (const width of [360, 768, 1280, 1920]) {
  for (const screen of SCREENS) {
    test(`${screen.name}: sin scroll lateral, sin violaciones AXE y objetivos de 44 px a ${width} px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await open(page, screen.path);
      await expect(page.locator('h1')).toBeVisible();
      await expect(page.locator('table.ledger tbody tr:visible, ul.entries li:visible').first()).toBeVisible();
      await expectNoSidewaysScroll(page);
      await checkA11y(page);
      const small = await page.evaluate(() =>
        [...document.querySelectorAll<HTMLElement>('main button, main a.btn, main a.row-link, main a.entry, main input')]
          .map((el) => ({ name: (el.textContent || el.getAttribute('aria-label') || el.id || '').trim().slice(0, 30), h: el.getBoundingClientRect().height }))
          .filter((c) => c.h > 0 && c.h < 44),
      );
      // A row link stretches over its whole row; its own box is a text line and does not count.
      expect(small.filter((c) => !/^\d{2}\/\d{2}\/\d{4}$/.test(c.name))).toEqual([]);
    });
  }
}
