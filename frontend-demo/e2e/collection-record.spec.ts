import AxeBuilder from '@axe-core/playwright';
import { Page, expect, test } from '@playwright/test';

import { ASSOCIATIONS, BackendOptions, EMAIL, NEIGHBORS, PASSWORD, SCHEDULES, fakeBackend } from './backend';

const ROSA = NEIGHBORS[1];

async function openForm(page: Page, options: BackendOptions = {}): Promise<void> {
  await fakeBackend(page, options);
  await page.goto('/login');
  await page.getByLabel('Correo electrónico').fill(EMAIL);
  await page.getByLabel('Contraseña').fill(PASSWORD);
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await expect(page).toHaveURL(/\/empresas$/);
  await page.getByRole('navigation', { name: 'Secciones' }).getByRole('link', { name: 'Registrar recojo' }).click();
  await expect(page).toHaveURL(/\/recojos\/nuevo$/);
  await expect(page.getByRole('heading', { name: 'Registrar recojo', level: 1 })).toBeVisible();
  // Both pickers have loaded.
  await expect(page.getByLabel('Vecino').locator('option')).toHaveCount(NEIGHBORS.length + 1);
  await expect(page.getByLabel('Asociación').locator('option')).toHaveCount(ASSOCIATIONS.length + 1);
}

async function expectNoSidewaysScroll(page: Page): Promise<void> {
  const { scrollWidth, innerWidth } = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  expect(scrollWidth).toBeLessThanOrEqual(innerWidth);
}

test('register a collection with a schedule: the request carries exactly the real fields', async ({ page }) => {
  const posts: { neighborId: string; body: unknown }[] = [];
  await openForm(page, { collectionPosts: posts });
  await expect(page.getByRole('link', { name: 'Registrar recojo' })).toHaveAttribute('aria-current', 'page');

  await page.getByLabel('Vecino').selectOption({ label: `${ROSA.fullName} · ${ROSA.district}` });
  await page.getByLabel('Asociación').selectOption({ label: ASSOCIATIONS[0].name });
  await page.getByLabel('Fecha del recojo').fill('2026-10-05');
  await page.getByLabel('Peso recolectado (kg)').fill('12.50');
  // Monday to Sunday, although the API sends them alphabetically (MONDAY, THURSDAY, WEDNESDAY).
  await expect(page.getByLabel('Cronograma (opcional)').locator('option')).toHaveText([
    'Sin cronograma',
    'Lunes 08:00',
    'Miércoles 07:00',
    'Jueves 15:30 (pausado)',
  ]);
  await page.getByLabel('Cronograma (opcional)').selectOption({ label: 'Lunes 08:00' });
  await page.getByRole('button', { name: 'Registrar recojo' }).click();

  const status = page.getByRole('status').filter({ hasText: 'Recojo registrado' });
  await expect(status).toContainText('12.50 kg');
  await expect(status).toContainText('05/10/2026');
  await expect(status).toContainText(ROSA.fullName);
  await expect(page.getByText('Recojo registrado')).toBeFocused();
  expect(posts).toEqual([
    {
      neighborId: ROSA.id,
      body: {
        associationId: ASSOCIATIONS[0].id,
        collectionDate: '2026-10-05',
        weightKg: 12.5,
        scheduleId: SCHEDULES[ROSA.id][0].id,
      },
    },
  ]);

  // Another one in the same round: neighbor, association and date stay; weight starts over.
  await page.getByRole('button', { name: 'Registrar otro recojo' }).click();
  await expect(page.getByLabel('Peso recolectado (kg)')).toBeFocused();
  await expect(page.getByLabel('Peso recolectado (kg)')).toHaveValue('');
  await expect(page.getByLabel('Fecha del recojo')).toHaveValue('2026-10-05');
});

test('without a schedule the request sends scheduleId: null; inactive and suspended entries are labeled', async ({ page }) => {
  const posts: { neighborId: string; body: unknown }[] = [];
  await openForm(page, { collectionPosts: posts });
  await expect(page.getByLabel('Vecino')).toContainText(`${NEIGHBORS[0].fullName} · ${NEIGHBORS[0].district} (inactivo)`);
  await expect(page.getByLabel('Asociación')).toContainText(`${ASSOCIATIONS[1].name} (suspendida)`);

  await page.getByLabel('Vecino').selectOption({ label: `${NEIGHBORS[0].fullName} · ${NEIGHBORS[0].district} (inactivo)` });
  await expect(page.getByText('Este vecino no tiene cronogramas.')).toBeVisible();
  await page.getByLabel('Asociación').selectOption({ label: ASSOCIATIONS[0].name });
  await page.getByLabel('Peso recolectado (kg)').fill('3');
  await page.getByRole('button', { name: 'Registrar recojo' }).click();

  await expect(page.getByRole('status').filter({ hasText: 'Recojo registrado' })).toContainText('3.00 kg');
  expect((posts[0].body as { scheduleId: unknown }).scheduleId).toBeNull();
});

test('empty and malformed fields are explained next to each field, by keyboard, and nothing is sent', async ({ page }) => {
  const posts: { neighborId: string; body: unknown }[] = [];
  await openForm(page, { collectionPosts: posts });

  await page.getByLabel('Peso recolectado (kg)').fill('12,5');
  await page.getByLabel('Peso recolectado (kg)').press('Enter'); // submits the form
  await expect(page.getByText('Elige el vecino del recojo.')).toBeVisible();
  await expect(page.getByLabel('Vecino')).toBeFocused(); // the first field to fix
  await expect(page.getByText('Elige la asociación que hizo el recojo.')).toBeVisible();
  await expect(page.getByText('Escribe solo números, con punto y hasta 2 decimales')).toBeVisible();
  await expect(page.getByLabel('Peso recolectado (kg)')).toHaveAttribute('aria-invalid', 'true');

  await page.getByLabel('Peso recolectado (kg)').fill('0');
  await expect(page.getByText('El peso debe ser mayor que cero.')).toBeVisible();
  expect(posts).toEqual([]);
});

test('COL-009 (association with an expired certification) is explained and nothing typed is lost', async ({ page }) => {
  await openForm(page, { collectionFailure: { status: 409, code: 'COL-009' } });
  await page.getByLabel('Vecino').selectOption({ label: `${ROSA.fullName} · ${ROSA.district}` });
  await page.getByLabel('Asociación').selectOption({ label: ASSOCIATIONS[0].name });
  await page.getByLabel('Peso recolectado (kg)').fill('8.25');
  await page.getByRole('button', { name: 'Registrar recojo' }).click();

  await expect(page.getByRole('alert')).toHaveText('La asociación tiene una certificación vencida y no puede registrar recojos.');
  await expect(page.getByLabel('Peso recolectado (kg)')).toHaveValue('8.25');
  await expect(page.getByRole('button', { name: 'Registrar recojo' })).toBeEnabled();
});

test('if the associations fail to load, the form says so and offers a retry', async ({ page }) => {
  await fakeBackend(page, { associationsStatus: 503 });
  await page.goto('/login');
  await page.getByLabel('Correo electrónico').fill(EMAIL);
  await page.getByLabel('Contraseña').fill(PASSWORD);
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await expect(page).toHaveURL(/\/empresas$/);
  await page.goto('/recojos/nuevo');

  await expect(page.getByRole('alert')).toContainText('No se pudo cargar las asociaciones (X-500).');
  await expect(page.getByRole('alert').getByRole('button', { name: 'Volver a intentar' })).toBeVisible();
});

for (const width of [360, 768, 1280]) {
  test(`collection form at ${width} px: no sideways scrolling, 44 px controls, AXE clean (empty and with errors)`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await openForm(page);
    await expectNoSidewaysScroll(page);

    for (const label of ['Vecino', 'Asociación', 'Fecha del recojo', 'Peso recolectado (kg)', 'Cronograma (opcional)']) {
      const box = await page.getByLabel(label).boundingBox();
      expect(box?.height, label).toBeGreaterThanOrEqual(44);
    }

    const axe = async () => {
      const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
      return results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical').map((v) => v.id);
    };
    expect(await axe()).toEqual([]);

    await page.getByRole('button', { name: 'Registrar recojo' }).click();
    await expect(page.getByText('Elige el vecino del recojo.')).toBeVisible();
    await expectNoSidewaysScroll(page);
    expect(await axe()).toEqual([]);
  });
}
