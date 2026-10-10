import AxeBuilder from '@axe-core/playwright';
import { Page, expect, test } from '@playwright/test';

import { COMPANIES, EMAIL, PASSWORD, fakeBackend } from './backend';

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
  // Only collection-service is slow; the others may still be in flight for a moment, so target its row.
  await expect(page.locator('tr[data-status="waking"]').filter({ hasText: 'collection-service' })).toContainText('Despertando');

  await checkA11y(page, 'warm-up (360 px)');
});

for (const width of [1280, 360]) {
  test(`a11y: company page at ${width} px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await fakeBackend(page);
    await page.goto('/login');
    await page.getByLabel('Correo electrónico').fill(EMAIL);
    await page.getByLabel('Contraseña').fill(PASSWORD);
    await page.getByRole('button', { name: 'Iniciar sesión' }).click();
    await expect(page).toHaveURL(/\/empresas$/);
    await page.goto(`/empresas/${COMPANIES[0].id}`);
    await expect(page.locator('app-kilos-chart rect.bar')).toHaveCount(12);

    await checkA11y(page, `company (${width} px)`);
  });
}

/** WCAG relative luminance contrast of two "rgb(r, g, b)" strings. */
function contrast(a: string, b: string): number {
  const lum = (rgb: string) => {
    const [r, g, bl] = (rgb.match(/\d+/g) ?? []).slice(0, 3).map((v) => {
      const c = Number(v) / 255;
      return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
    });
    return 0.2126 * r + 0.7152 * g + 0.0722 * bl;
  };
  const [hi, lo] = [lum(a), lum(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

test('a11y: keyboard focus on the dark bar is visible (outline at least 3:1 against the bar)', async ({ page }) => {
  await fakeBackend(page);
  await page.goto('/login');
  await page.getByLabel('Correo electrónico').fill(EMAIL);
  await page.getByLabel('Contraseña').fill(PASSWORD);
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await expect(page).toHaveURL(/\/empresas$/);

  for (const name of ['Trazabilidad ESG', 'Empresas', 'Registrar recojo', 'Cerrar sesión']) {
    const target = name === 'Cerrar sesión' ? page.getByRole('button', { name }) : page.getByRole('link', { name, exact: true });
    await target.focus();
    await page.keyboard.press('Shift+Tab');
    await page.keyboard.press('Tab'); // keyboard modality, so :focus-visible applies
    const { outline, width, style, bar, ring, band } = await target.evaluate((el) => {
      const s = getComputedStyle(el);
      const header = el.closest('header')!;
      const r = el.getBoundingClientRect();
      const grow = parseFloat(s.outlineOffset) + parseFloat(s.outlineWidth);
      return {
        outline: s.outlineColor,
        width: s.outlineWidth,
        style: s.outlineStyle,
        bar: getComputedStyle(header).backgroundColor,
        ring: { top: r.top - grow, bottom: r.bottom + grow },
        band: { top: header.getBoundingClientRect().top, bottom: header.getBoundingClientRect().bottom },
      };
    });
    expect(style, name).toBe('solid');
    expect(parseFloat(width), name).toBeGreaterThanOrEqual(2);
    expect(contrast(outline, bar), `${name}: ${outline} on ${bar}`).toBeGreaterThanOrEqual(3);
    // The whole ring is drawn on the bar: none of it falls off-screen or onto the page.
    expect(ring.top, `${name}: ring top`).toBeGreaterThanOrEqual(band.top);
    expect(ring.bottom, `${name}: ring bottom`).toBeLessThanOrEqual(band.bottom);
  }
});

for (const width of [360, 768, 1280]) {
  test(`login at ${width} px: no sideways scrolling, the ticket and statement centred as a set, AXE clean`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await fakeBackend(page);
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: 'Trazabilidad ESG' })).toBeVisible();

    const layout = await page.evaluate(() => {
      const set = document.querySelector('.set')!.getBoundingClientRect();
      return { scroll: document.documentElement.scrollWidth, inner: innerWidth, top: set.top, bottom: innerHeight - set.bottom, tall: set.height > innerHeight };
    });
    expect(layout.scroll).toBeLessThanOrEqual(layout.inner);
    // AXE judges the printed ticket, not a half-revealed one.
    await page.evaluate(() => Promise.all(document.getAnimations().map((a) => a.finished)));
    if (!layout.tall) {
      // Vertically centred as one set: equal room above and below (within a few px).
      expect(Math.abs(layout.top - layout.bottom)).toBeLessThanOrEqual(4);
    }
    await checkA11y(page, `login ${width}`);
  });
}
