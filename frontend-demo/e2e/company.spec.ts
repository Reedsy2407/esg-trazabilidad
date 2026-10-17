import { Page, expect, test } from '@playwright/test';

import { BackendOptions, CERTIFICATES, COMPANIES, EMAIL, PASSWORD, fakeBackend } from './backend';

async function openCompany(page: Page, companyId: string, options: BackendOptions = {}): Promise<void> {
  await fakeBackend(page, options);
  await page.goto('/login');
  await page.getByLabel('Correo electrónico').fill(EMAIL);
  await page.getByLabel('Contraseña').fill(PASSWORD);
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await expect(page).toHaveURL(/\/empresas$/);
  await page.goto(`/empresas/${companyId}`);
}

async function expectNoSidewaysScroll(page: Page): Promise<void> {
  const { scrollWidth, innerWidth } = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  expect(scrollWidth).toBeLessThanOrEqual(innerWidth);
}

const summary = (page: Page) => page.getByRole('region', { name: 'Resumen del período' });
const certificates = (page: Page) => page.getByRole('region', { name: 'Certificados' });

test('company page at 1280 px: header, summary, 12-period chart and the certificates table', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  await openCompany(page, COMPANIES[0].id);

  await expect(page.getByRole('heading', { name: COMPANIES[0].name, level: 1 })).toBeVisible();
  await expect(page.getByText(COMPANIES[0].ruc)).toBeVisible();

  // Resumen del período: the latest certified period (the chart's last bar), three lines.
  await expect(summary(page).locator('dt')).toHaveText([
    'Último período certificado',
    'Kilos trazados',
    'Cumplimiento de jerarquía (declarado)',
  ]);
  await expect(summary(page).locator('dd').first()).toHaveText('Diciembre 2024');
  await expect(summary(page)).toContainText('12,480.50 kg');
  await expect(summary(page)).toContainText('87.5 %');

  // Chart: the 12 most recent of 14 periods, with the same numbers as a text table.
  await expect(page.locator('app-kilos-chart rect.bar')).toHaveCount(12);
  const chartTable = page.locator('app-kilos-chart table tbody tr');
  await expect(chartTable).toHaveCount(12);
  await expect(chartTable.first()).toContainText('Enero 2024');
  await expect(chartTable.last()).toContainText('Diciembre 2024');

  // Table: newest first, kilos and compliance right-aligned in mono, issue date in Lima.
  const rows = certificates(page).locator('tbody tr');
  await expect(rows).toHaveCount(CERTIFICATES.length);
  await expect(rows.first()).toContainText('Diciembre 2024');
  await expect(rows.first()).toContainText('12,480.50 kg');
  await expect(rows.first()).toContainText('87.5 %');
  await expect(rows.first()).toContainText('05/01/2025');
  await expect(certificates(page)).toContainText('Mostrando 14 de 14 certificados');

  await expectNoSidewaysScroll(page);
});

test('company page at 360 px: certificates become stacked cards, no sideways scrolling', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 800 });
  await openCompany(page, COMPANIES[0].id);

  await expect(page.locator('app-kilos-chart rect.bar')).toHaveCount(12);
  // Every axis number and period label stays inside the drawing (no clipped "15,000" or "Dic 24").
  const clipped = await page.evaluate(() => {
    const svg = document.querySelector('app-kilos-chart svg') as SVGSVGElement;
    const box = svg.getBoundingClientRect();
    return Array.from(svg.querySelectorAll('text'))
      .filter((t) => getComputedStyle(t).display !== 'none')
      .map((t) => ({ text: t.textContent?.trim(), r: t.getBoundingClientRect() }))
      .filter(({ r }) => r.left < box.left - 0.5 || r.right > box.right + 0.5)
      .map(({ text }) => text);
  });
  expect(clipped).toEqual([]);
  // ...and the visible period labels keep at least 4 px between them (touching reads as "Feb 24Abr 24").
  const overlaps = await page.evaluate(() => {
    const labels = Array.from(document.querySelectorAll('app-kilos-chart text.label'))
      .filter((t) => getComputedStyle(t).display !== 'none')
      .map((t) => t.getBoundingClientRect());
    return labels.slice(1).filter((r, i) => r.left - labels[i].right < 4).length;
  });
  expect(overlaps).toBe(0);
  await expect(certificates(page).locator('table')).toBeHidden();
  const cards = certificates(page).locator('a.card');
  await expect(cards).toHaveCount(CERTIFICATES.length);
  await expect(cards.first()).toContainText('Diciembre 2024');
  await expect(cards.first()).toContainText('12,480.50 kg');
  expect((await cards.first().boundingBox())?.height ?? 0).toBeGreaterThanOrEqual(44);

  await expectNoSidewaysScroll(page);

  // Each card opens its certificate.
  await cards.first().click();
  await expect(page).toHaveURL(new RegExp(`/certificados/${CERTIFICATES[0].id}$`));
});

test('a company without certificates says so in every block and never asks for a summary', async ({ page }) => {
  const summaryCalls: string[] = [];
  page.on('request', (r) => {
    if (r.url().includes('/certificate-summary')) summaryCalls.push(r.url());
  });
  await openCompany(page, COMPANIES[1].id);

  await expect(page.getByRole('heading', { name: COMPANIES[1].name, level: 1 })).toBeVisible();
  await expect(page.getByText('Aún no hay certificados emitidos, así que no hay períodos que graficar.')).toBeVisible();
  await expect(page.getByText('Esta empresa todavía no tiene certificados emitidos.')).toBeVisible();
  await expect(summary(page)).toContainText('Aún no hay certificados.');
  expect(summaryCalls).toEqual([]);

  await expectNoSidewaysScroll(page);
});

test('a summary without SIGERSOL compliance shows it as missing, never as 0 %', async ({ page }) => {
  await openCompany(page, COMPANIES[0].id, { summaryComplianceNull: true });

  await expect(summary(page)).toContainText('Diciembre 2024');
  await expect(summary(page)).toContainText('Sin registro SIGERSOL');
  await expect(summary(page)).not.toContainText('0 %');
});

test('if only the certificates fail, the header stays, the blocks that need them say why, and the table offers a retry', async ({ page }) => {
  await openCompany(page, COMPANIES[0].id, { certificatesStatus: 500 });

  await expect(page.getByRole('heading', { name: COMPANIES[0].name, level: 1 })).toBeVisible();
  await expect(summary(page)).toContainText('El resumen no está disponible porque no se pudieron cargar los certificados.');
  await expect(certificates(page).getByRole('alert')).toContainText('No se pudieron cargar los certificados: el servicio de reportes tuvo un problema inesperado.');
  await expect(certificates(page).getByRole('button', { name: 'Volver a intentar' })).toBeVisible();
  await expect(page.getByText('La gráfica no está disponible porque no se pudieron cargar los certificados.')).toBeVisible();
});

test('lifecycle: edit the contact, deactivate with a reason, reactivate, then delete', async ({ page }) => {
  const writes: NonNullable<BackendOptions['writes']> = [];
  await openCompany(page, COMPANIES[0].id, { writes, noCertificates: true });
  const actions = page.getByRole('region', { name: 'Acciones de la empresa' });

  await actions.getByRole('button', { name: 'Editar contacto' }).click();
  await page.getByLabel('Persona de contacto').fill('Luis Paredes');
  await page.getByRole('button', { name: 'Guardar contacto' }).click();
  await expect(page.getByText('Contacto guardado.')).toBeVisible();
  await expect(page.getByRole('region', { name: 'SUNAT y contacto' })).toContainText(/Contacto\s*Luis Paredes/);

  await actions.getByRole('button', { name: 'Desactivar empresa' }).click();
  await page.getByLabel('Motivo').fill('Cerró el local');
  await page.getByLabel(/Entiendo que no se le podrán emitir certificados/).check();
  await page.locator('app-reason-confirm').getByRole('button', { name: /^Desactivar / }).click();
  await expect(page.getByText(/Empresa desactivada\./)).toBeVisible();
  await expect(page.getByText('Inactiva', { exact: true })).toBeVisible();

  await actions.getByRole('button', { name: 'Reactivar empresa' }).click();
  await page.getByLabel('Motivo').fill('Reabrió');
  await page.getByLabel(/Revisé que la empresa vuelve a estar activa/).check();
  await page.locator('app-reason-confirm').getByRole('button', { name: /^Reactivar / }).click();
  await expect(page.getByText('Empresa reactivada.')).toBeVisible();

  await actions.getByRole('button', { name: 'Eliminar empresa' }).click();
  await page.getByLabel('Motivo').fill('Duplicada');
  await page.getByLabel(/Entiendo que la eliminación no se puede deshacer/).check();
  await page.locator('app-reason-confirm').getByRole('button', { name: /^Eliminar / }).click();
  await expect(page).toHaveURL(/\/empresas$/);
  await expect(page.getByRole('link', { name: new RegExp(COMPANIES[0].name) })).toHaveCount(0);

  expect(writes.map((w) => `${w.method} ${w.path.split('/').slice(4).join('/')}`)).toEqual([
    `PATCH ${COMPANIES[0].id}`,
    `PATCH ${COMPANIES[0].id}/deactivate`,
    `PATCH ${COMPANIES[0].id}/activate`,
    `DELETE ${COMPANIES[0].id}`,
  ]);
  expect(writes[1].body).toEqual({ reason: 'Cerró el local' });
});

test('a company with certificates cannot be deleted: no button, the reason, and deactivating is still offered', async ({ page }) => {
  await openCompany(page, COMPANIES[0].id);
  const actions = page.getByRole('region', { name: 'Acciones de la empresa' });
  await expect(page.getByRole('region', { name: 'Certificados' })).toBeVisible();
  await expect(actions).toContainText('no se puede eliminar');
  await expect(actions.getByRole('button', { name: 'Eliminar empresa' })).toHaveCount(0);
  await expect(actions.getByRole('button', { name: 'Desactivar empresa' })).toBeVisible();
});

test('a certificate issued after the page loaded: the server still refuses the delete (RPT-017) and the company stays', async ({ page }) => {
  await openCompany(page, COMPANIES[0].id, { noCertificates: true, companyWriteFailure: { status: 409, code: 'RPT-017' } });
  await page.getByRole('button', { name: 'Eliminar empresa' }).click();
  await page.getByLabel('Motivo').fill('Intento');
  await page.getByLabel(/Entiendo que la eliminación no se puede deshacer/).check();
  await page.locator('app-reason-confirm').getByRole('button', { name: /^Eliminar / }).click();
  await expect(page.getByRole('alert')).toContainText('aunque estén anulados');
  await expect(page.getByRole('heading', { name: COMPANIES[0].name, level: 1 })).toBeVisible();
});

test('an unknown company (404 RPT-001) shows a clear message instead of an empty page', async ({ page }) => {
  await openCompany(page, '0192f3a8-dead-7000-8000-000000000000');

  await expect(page.getByRole('alert')).toContainText('Esta empresa no existe o ya no está registrada.');
  await expect(page.getByRole('link', { name: 'Ver todas las empresas' })).toBeVisible();
  await expect(page.getByRole('region', { name: 'Certificados' })).toHaveCount(0);
});
