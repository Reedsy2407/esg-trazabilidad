import { expect, test } from '@playwright/test';

import { fakeBackend } from './backend';
import { signedIn } from './session';

test('an address the app does not have shows the 404 with the logo, and the way back depends on the session', async ({ page }) => {
  await fakeBackend(page);
  await page.goto('/esta-ruta-no-existe');
  await expect(page).toHaveTitle('Página no encontrada · Trazabilidad ESG');
  await expect(page.getByRole('heading', { name: 'No encontramos esta página', level: 1 })).toBeVisible();
  // The logo is the only identification on this page: it has its name.
  await expect(page.getByRole('img', { name: 'Trazabilidad ESG' })).toBeVisible();
  await page.getByRole('link', { name: 'Iniciar sesión' }).click();
  await expect(page).toHaveURL(/\/login$/);
});

test('signed in, the 404 leads back to the companies', async ({ page }) => {
  const unmocked = await signedIn(page, '/otra-ruta-que-no-existe');
  await expect(page.getByRole('heading', { name: 'No encontramos esta página', level: 1 })).toBeVisible();
  await page.getByRole('link', { name: 'Ir a Empresas' }).click();
  await expect(page).toHaveURL(/\/empresas$/);
  expect(unmocked).toEqual([]);
});
