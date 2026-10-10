import AxeBuilder from '@axe-core/playwright';
import { Page, expect } from '@playwright/test';

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

export const WIDTHS = [360, 768, 1280] as const;
