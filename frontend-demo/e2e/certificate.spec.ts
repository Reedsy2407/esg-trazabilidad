import AxeBuilder from '@axe-core/playwright';
import { Page, expect, test } from '@playwright/test';

import { BackendOptions, CERTIFICATES, COMPANIES, EMAIL, FOREIGN_CERTIFICATE, PASSWORD, fakeBackend } from './backend';

const LATEST = CERTIFICATES[0]; // December 2024: newest issued, first in the list

async function signIn(page: Page, options: BackendOptions = {}): Promise<void> {
  await fakeBackend(page, options);
  await page.goto('/login');
  await page.getByLabel('Correo electrónico').fill(EMAIL);
  await page.getByLabel('Contraseña').fill(PASSWORD);
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await expect(page).toHaveURL(/\/empresas$/);
}

async function openCertificate(page: Page, options: BackendOptions = {}, certificateId = LATEST.id): Promise<void> {
  await signIn(page, options);
  await page.goto(`/empresas/${COMPANIES[0].id}/certificados/${certificateId}`);
}

async function expectNoSidewaysScroll(page: Page): Promise<void> {
  const { scrollWidth, innerWidth } = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  expect(scrollWidth).toBeLessThanOrEqual(innerWidth);
}

const sheet = (page: Page) => page.locator('article.sheet');

test('a certificate row on the company page opens its detail, and the detail shows the real fields', async ({ page }) => {
  await signIn(page);
  await page.goto(`/empresas/${COMPANIES[0].id}`);
  await page.getByRole('link', { name: 'Diciembre 2024' }).click();
  await expect(page).toHaveURL(new RegExp(`/empresas/${COMPANIES[0].id}/certificados/${LATEST.id}$`));

  await expect(page.getByRole('heading', { level: 1 })).toContainText('Certificado de trazabilidad ESG');
  await expect(page.getByRole('heading', { level: 1 })).toContainText(COMPANIES[0].name);
  await expect(sheet(page)).toContainText(`RUC ${COMPANIES[0].ruc}`);
  await expect(sheet(page)).toContainText('12,480.50 kg');
  await expect(sheet(page)).toContainText('87.5 %');
  await expect(sheet(page)).toContainText('01/12/2024 – 31/12/2024');
  await expect(sheet(page)).toContainText('05/01/2025'); // issuedAt 2025-01-05T15:00Z, in Lima
  // The whole UUID, not an 8-character prefix that can repeat.
  await expect(sheet(page).locator('code')).toHaveText(LATEST.id);
  // No status stamp and nothing implying third-party verification.
  await expect(sheet(page)).not.toContainText(/verific|vigente|vencido/i);

  await page.getByRole('link', { name: `Volver a ${COMPANIES[0].name}` }).click();
  await expect(page).toHaveURL(new RegExp(`/empresas/${COMPANIES[0].id}$`));
});

test('"Copiar" puts the full code on the clipboard and says so once', async ({ page, context }) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await openCertificate(page);

  await page.getByRole('button', { name: 'Copiar' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Código copiado.' })).toBeVisible();
  expect(await page.evaluate(() => navigator.clipboard.readText())).toBe(LATEST.id);
});

test('PDF and CSV download under the backend name, and the buttons come back', async ({ page }) => {
  await openCertificate(page);

  const [pdf] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Descargar PDF' }).click()]);
  expect(pdf.suggestedFilename()).toBe(`certificado-${LATEST.id}.pdf`);
  const [csv] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Descargar CSV' }).click()]);
  expect(csv.suggestedFilename()).toBe(`certificado-${LATEST.id}.csv`);

  await expect(page.getByRole('button', { name: 'Descargar PDF' })).toBeEnabled();
});

test('a hostile Content-Disposition is saved under a sanitized name', async ({ page }) => {
  await openCertificate(page, { downloadDisposition: 'attachment; filename="../../etc/passwd.exe"' });

  const [pdf] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Descargar PDF' }).click()]);
  expect(pdf.suggestedFilename()).toBe('passwd.pdf');
});

test('a failed download explains itself and never leaves the button stuck on "Descargando"', async ({ page }) => {
  await openCertificate(page, { downloadFailure: 404 });
  await page.getByRole('button', { name: 'Descargar CSV' }).click();
  await expect(page.getByRole('alert')).toContainText('No se pudo descargar el CSV: el certificado ya no existe');
  await expect(page.getByRole('button', { name: 'Descargar CSV' })).toBeEnabled();
});

test('a dropped connection during a download says so', async ({ page }) => {
  await openCertificate(page, { downloadFailure: 'network' });
  await page.getByRole('button', { name: 'Descargar PDF' }).click();
  await expect(page.getByRole('alert')).toContainText('No se pudo conectar con el servicio de reportes para descargar el PDF');
  await expect(page.getByRole('button', { name: 'Descargar PDF' })).toBeEnabled();
});

test('an unknown certificate (404 RPT-003) shows the typed message and the way back to the company', async ({ page }) => {
  await openCertificate(page, {}, '0192f3a8-dead-7000-8000-000000000000');

  await expect(page.getByRole('alert')).toContainText('Este certificado no existe o no pertenece a esta empresa.');
  await page.getByRole('alert').getByRole('link', { name: 'Volver a la empresa' }).click();
  await expect(page).toHaveURL(new RegExp(`/empresas/${COMPANIES[0].id}$`));
});

test('a 401 during a download ends the session and goes to the login, with no token left behind', async ({ page }) => {
  await openCertificate(page, { downloadFailure: 401 });
  await page.getByRole('button', { name: 'Descargar PDF' }).click();
  await expect(page).toHaveURL(/\/login\?motivo=sesion$/);
  expect(await page.evaluate(() => JSON.stringify(sessionStorage) + JSON.stringify(localStorage))).not.toMatch(/eyJ/);
});

test('a 200 that is not the file (an HTML fallback page) is refused, not saved as a PDF', async ({ page }) => {
  await openCertificate(page, { downloadAsHtml: true });
  let downloaded = false;
  page.on('download', () => (downloaded = true));
  await page.getByRole('button', { name: 'Descargar PDF' }).click();
  await expect(page.getByRole('alert')).toContainText('no devolvió un PDF válido');
  await expect(page.getByRole('button', { name: 'Descargar PDF' })).toBeEnabled();
  expect(downloaded).toBe(false);
});

test("another company's certificate, asked for under this company, is RPT-003 and shows nothing of it", async ({ page }) => {
  await openCertificate(page, {}, FOREIGN_CERTIFICATE.id);

  await expect(page.getByRole('alert')).toContainText('Este certificado no existe o no pertenece a esta empresa.');
  await expect(page.locator('body')).not.toContainText(COMPANIES[1].name);
});

for (const width of [360, 768, 1280]) {
  test(`certificate detail at ${width} px: no sideways scrolling, the full code wraps inside the sheet, AXE clean`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await openCertificate(page);
    await expect(sheet(page).locator('code')).toHaveText(LATEST.id);

    await expectNoSidewaysScroll(page);
    const inside = await page.evaluate(() => {
      const box = (document.querySelector('article.sheet') as HTMLElement).getBoundingClientRect();
      const code = (document.querySelector('article.sheet code') as HTMLElement).getBoundingClientRect();
      return code.right <= box.right && code.left >= box.left;
    });
    expect(inside).toBe(true);

    // AXE judges the printed ticket, not a half-revealed one.
    await page.evaluate(() => Promise.all(document.querySelector('article.sheet')!.getAnimations().map((a) => a.finished)));
    const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    const blocking = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(blocking.map((v) => v.id)).toEqual([]);
  });
}

for (const width of [320, 360, 400, 768, 1280]) {
  test(`certificate at ${width} px: the code breaks only after a hyphen, and the three ticket lines are laid out alike`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await openCertificate(page);
    await expect(sheet(page).locator('code')).toHaveText(LATEST.id);
    await page.evaluate(() => Promise.all(document.querySelector('article.sheet')!.getAnimations().map((a) => a.finished)));

    // Where does each rendered line of the code start? Only right after a "-" (or at the start).
    const breaks = await sheet(page).locator('code').evaluate((code) => {
      const text = code.firstChild as Text;
      const tops: number[] = [];
      for (let i = 0; i < text.length; i++) {
        const range = document.createRange();
        range.setStart(text, i);
        range.setEnd(text, i + 1);
        tops.push(Math.round(range.getBoundingClientRect().top));
      }
      const starts: number[] = [];
      tops.forEach((top, i) => {
        if (i > 0 && top > tops[i - 1]) starts.push(i);
      });
      return starts.map((i) => text.data[i - 1]);
    });
    expect(breaks.every((ch) => ch === '-'), `line breaks after: ${JSON.stringify(breaks)}`).toBe(true);

    // Compliance, period and issue date: all on one line with their label, or all stacked.
    const rows = await sheet(page).locator('.leader').evaluateAll((els) =>
      els.map((el) => {
        const dt = el.querySelector('dt')!.getBoundingClientRect();
        const dd = el.querySelector('dd')!.getBoundingClientRect();
        return dd.top >= dt.bottom - 1 ? 'stacked' : 'inline';
      }),
    );
    expect(rows).toHaveLength(3);
    expect(new Set(rows).size, JSON.stringify(rows)).toBe(1);
    expect(rows[0]).toBe(width <= 400 ? 'stacked' : 'inline');
  });
}
