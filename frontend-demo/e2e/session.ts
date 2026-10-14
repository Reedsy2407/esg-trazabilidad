import AxeBuilder from '@axe-core/playwright';
import { Locator, Page, expect } from '@playwright/test';

import { BackendOptions, EMAIL, PASSWORD, fakeBackend } from './backend';

/**
 * Fake backend + sign in, then open `path` from inside the app. Returns the list of requests
 * that reached no fake route: a test ends with expectAllMocked() so nothing went to the real
 * services behind ng serve's proxy.
 */
export async function signedIn(page: Page, path: string, options: BackendOptions = {}): Promise<string[]> {
  const unmocked = options.unmocked ?? [];
  await fakeBackend(page, { ...options, unmocked });
  await page.goto('/login');
  await page.getByLabel('Correo electrónico').fill(EMAIL);
  await page.getByLabel('Contraseña').fill(PASSWORD);
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await expect(page).toHaveURL(/\/empresas$/);
  if (path !== '/empresas') {
    // In-app navigation keeps the session (sessionStorage) and the same fake routes.
    await page.evaluate((target) => {
      history.pushState({}, '', target);
      dispatchEvent(new PopStateEvent('popstate'));
    }, path);
  }
  return unmocked;
}

export async function checkA11y(page: Page): Promise<void> {
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(results.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`)).toEqual([]);
}

export async function expectNoSidewaysScroll(page: Page): Promise<void> {
  const { scrollWidth, innerWidth } = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  expect(scrollWidth).toBeLessThanOrEqual(innerWidth);
}

/**
 * The "Ingreso manual" tag never touches what precedes or follows it: a real space in the text
 * (Angular drops whitespace-only text nodes) and a visible gap on screen, in whatever `container` holds it.
 */
export async function expectManualTagSpaced(container: Locator): Promise<void> {
  const tag = container.locator('app-manual-tag');
  await expect(tag).toBeVisible();
  const { text, gaps } = await container.evaluate((el) => {
    const box = el.querySelector('app-manual-tag')!.getBoundingClientRect();
    const range = document.createRange();
    range.selectNodeContents(el);
    // Text boxes on the tag's line, left and right of it (the tag's own boxes excluded).
    const line = Array.from(range.getClientRects()).filter(
      (r) => r.width > 0 && r.bottom > box.top && r.top < box.bottom && (r.right <= box.left || r.left >= box.right),
    );
    const before = line.filter((r) => r.right <= box.left).map((r) => box.left - r.right);
    const after = line.filter((r) => r.left >= box.right).map((r) => r.left - box.right);
    return { text: (el.textContent ?? '').replace(/\s+/g, ' ').trim(), gaps: [...before, ...after] };
  });
  expect(text).toMatch(/(^|\S )Ingreso manual($| \S)/);
  expect(Math.min(...gaps)).toBeGreaterThanOrEqual(4);
}

export const WIDTHS = [360, 768, 1280] as const;

/** The collection form's neighbour picker: one editable combobox. */
export const neighborBox = (page: Page) => page.getByRole('combobox', { name: /^Vecino/ });
export const neighborList = (page: Page) => page.getByRole('listbox', { name: 'Vecinos' });

/** Open the list and click the neighbour's row. */
export async function chooseNeighbor(page: Page, label: string): Promise<void> {
  await neighborBox(page).click();
  await neighborList(page).getByRole('option', { name: label, exact: true }).click();
}
