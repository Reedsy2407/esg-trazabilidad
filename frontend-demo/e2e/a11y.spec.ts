import AxeBuilder from '@axe-core/playwright';
import { Page, expect, test } from '@playwright/test';

import { EMAIL, PASSWORD, fakeBackend } from './backend';

/**
 * AXE (WCAG 2.1 A/AA) on the three screens of this slice. Serious and
 * critical violations fail the test; minor and moderate ones are attached to
 * the report so they stay visible without blocking.
 */
async function checkA11y(page: Page, screen: string): Promise<void> {
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  const blocking = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
  const minor = results.violations.filter((v) => v.impact !== 'serious' && v.impact !== 'critical');
  if (minor.length > 0) {
    test.info().annotations.push({
      type: `axe minor (${screen})`,
      description: minor.map((v) => `${v.id} [${v.impact}] x${v.nodes.length}`).join('; '),
    });
  }
  expect(
    blocking.map((v) => ({ id: v.id, impact: v.impact, targets: v.nodes.map((n) => n.target.join(' ')) })),
    `${screen}: serious/critical AXE violations`,
  ).toEqual([]);
}

test('a11y: "Preparando el sistema" while services wake up', async ({ page }) => {
  // Two services never answer, so the screen stays visible.
  await page.route('**/svc/collection/actuator/health/liveness', () => new Promise(() => undefined));
  await page.route('**/svc/reporting/actuator/health/liveness', () => new Promise(() => undefined));
  await page.route('**/svc/auth/actuator/health/liveness', (r) => r.fulfill({ status: 200, body: '{"status":"UP"}' }));
  await page.route('**/svc/recycler/actuator/health/liveness', (r) => r.fulfill({ status: 200, body: '{"status":"UP"}' }));
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Preparando el sistema' })).toBeVisible();

  await checkA11y(page, 'warm-up');
});

test('a11y: login, empty and with field errors', async ({ page }) => {
  await fakeBackend(page);
  await page.goto('/login');
  await expect(page.getByLabel('Correo electrónico')).toBeVisible();
  await checkA11y(page, 'login');

  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await expect(page.getByText('Escribe tu correo electrónico.')).toBeVisible();
  await checkA11y(page, 'login with errors');
});

test('a11y: companies list', async ({ page }) => {
  await fakeBackend(page);
  await page.goto('/login');
  await page.getByLabel('Correo electrónico').fill(EMAIL);
  await page.getByLabel('Contraseña').fill(PASSWORD);
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await expect(page.getByText('Mostrando 1–2 de 2 empresas')).toBeVisible();

  await checkA11y(page, 'companies');
});

test('a11y: companies as stacked cards on a 360 px phone', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 800 });
  await fakeBackend(page);
  await page.goto('/login');
  await page.getByLabel('Correo electrónico').fill(EMAIL);
  await page.getByLabel('Contraseña').fill(PASSWORD);
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await expect(page.locator('a.card').first()).toBeVisible();

  await checkA11y(page, 'companies (cards)');
});

test('a11y: "Preparando el sistema" on a 360 px phone while a service wakes up', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 800 });
  await page.route('**/svc/collection/actuator/health/liveness', () => new Promise(() => undefined));
  for (const service of ['auth', 'recycler', 'reporting']) {
    await page.route(`**/svc/${service}/actuator/health/liveness`, (r) => r.fulfill({ status: 200, body: '{"status":"UP"}' }));
  }
  await page.goto('/');
  await expect(page.locator('tr[data-status="waking"]')).toContainText('Despertando');

  await checkA11y(page, 'warm-up (360 px)');
});
