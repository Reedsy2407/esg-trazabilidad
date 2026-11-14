import { Page, test } from '@playwright/test';

import { ASSOCIATIONS, BackendOptions, FIELD_RECYCLERS, WORKDAY_IDS } from './backend';
import { signedIn } from './session';

/**
 * Screenshots for the visual review of the field screens (module 2, stage 2), at 360, 768, 1280 and 1920 px and in the
 * states that matter: empty, loading, error, inactive recycler and suspended association. They are written only when
 * CAPTURAS is set, to the folder CAPTURAS names, so a normal run of the e2e suite does not produce files:
 *   CAPTURAS=../tasks/evidencia-m2-etapa2/web npx playwright test field-captures
 */
const OUT = process.env['CAPTURAS'];
test.skip(!OUT, 'solo con CAPTURAS=<carpeta>');

const [ACTIVE, INACTIVE, SUSPENDED] = FIELD_RECYCLERS;
const at = (association: string, recycler: string, tail = '') => `/asociaciones/${association}/recicladores/${recycler}${tail}`;

interface Shot {
  name: string;
  path: string;
  options?: BackendOptions;
  prepare?: (page: Page) => Promise<void>;
  /** Taken as soon as the page opens, without waiting for the data. */
  early?: boolean;
}

const lima = async (page: Page) => {
  const combo = page.getByRole('combobox', { name: 'Distrito', exact: true });
  await combo.fill('lima');
  await combo.press('Enter');
  await page.getByRole('group', { name: /Cronogramas de Lima/ }).waitFor();
};

const SHOTS: Shot[] = [
  { name: 'cronogramas-inicial', path: at(ASSOCIATIONS[0].id, ACTIVE.id, '/cronogramas') },
  {
    name: 'cronogramas-distrito',
    path: at(ASSOCIATIONS[0].id, ACTIVE.id, '/cronogramas'),
    prepare: async (page) => {
      await lima(page);
      await page.locator('.pick-list label', { hasText: 'Ana Flores' }).getByRole('checkbox').check();
    },
  },
  {
    name: 'cronogramas-confirmar-movimiento',
    path: at(ASSOCIATIONS[0].id, ACTIVE.id, '/cronogramas'),
    prepare: async (page) => {
      await lima(page);
      await page.locator('.pick-list label', { hasText: 'Beto' }).getByRole('checkbox').check();
      await page.getByRole('button', { name: 'Asignar 1 marcado' }).click();
    },
  },
  {
    name: 'cronogramas-resultado-distrito',
    path: at(ASSOCIATIONS[0].id, ACTIVE.id, '/cronogramas'),
    prepare: async (page) => {
      await lima(page);
      await page.getByRole('button', { name: 'Asignar todo Lima' }).click();
      await page.getByRole('button', { name: 'Sí, asignar el distrito' }).click();
      await page.getByRole('region', { name: 'Resultado en Lima' }).waitFor();
    },
  },
  {
    name: 'cronogramas-distrito-vacio',
    path: at(ASSOCIATIONS[0].id, ACTIVE.id, '/cronogramas'),
    prepare: async (page) => {
      const combo = page.getByRole('combobox', { name: 'Distrito', exact: true });
      await combo.fill('comas');
      await combo.press('Enter');
      await page.getByText('no tiene cronogramas activos').waitFor();
    },
  },
  { name: 'cronogramas-error', path: at(ASSOCIATIONS[0].id, ACTIVE.id, '/cronogramas'), options: { fieldStatus: 503 } },
  {
    name: 'cronogramas-reciclador-inactivo',
    path: at(ASSOCIATIONS[0].id, INACTIVE.id, '/cronogramas'),
    prepare: lima,
  },
  { name: 'cronogramas-asociacion-suspendida', path: at(ASSOCIATIONS[1].id, SUSPENDED.id, '/cronogramas') },
  { name: 'jornadas-lista', path: at(ASSOCIATIONS[0].id, ACTIVE.id, '/jornadas') },
  {
    name: 'jornadas-retencion-confirmar',
    path: at(ASSOCIATIONS[0].id, ACTIVE.id, '/jornadas'),
    prepare: async (page) => {
      await page.getByRole('button', { name: 'Ejecutar retención' }).click();
    },
  },
  {
    name: 'jornadas-retencion-resultado',
    path: at(ASSOCIATIONS[0].id, ACTIVE.id, '/jornadas'),
    prepare: async (page) => {
      await page.getByRole('button', { name: 'Ejecutar retención' }).click();
      await page.getByRole('button', { name: 'Sí, ejecutar la retención' }).click();
      await page.locator('#retention-result').waitFor();
    },
  },
  {
    name: 'jornadas-filtro-vacio',
    path: at(ASSOCIATIONS[0].id, ACTIVE.id, '/jornadas'),
    prepare: async (page) => {
      await page.getByLabel('Hasta').fill('2026-01-01');
      await page.getByText('Ninguna jornada empezó').waitFor();
    },
  },
  { name: 'jornadas-vacio', path: at(ASSOCIATIONS[0].id, INACTIVE.id, '/jornadas') },
  { name: 'jornadas-error', path: at(ASSOCIATIONS[0].id, ACTIVE.id, '/jornadas'), options: { fieldStatus: 503 } },
  { name: 'jornada-detalle', path: at(ASSOCIATIONS[0].id, ACTIVE.id, `/jornadas/${WORKDAY_IDS.closed}`) },
  {
    name: 'jornada-foto-ampliada',
    path: at(ASSOCIATIONS[0].id, ACTIVE.id, `/jornadas/${WORKDAY_IDS.closed}`),
    prepare: async (page) => {
      await page.locator('button.thumb:visible').first().click();
    },
  },
  { name: 'jornada-tras-retencion', path: at(ASSOCIATIONS[0].id, ACTIVE.id, `/jornadas/${WORKDAY_IDS.summarized}`) },
  { name: 'jornada-sin-ubicacion', path: at(ASSOCIATIONS[0].id, ACTIVE.id, `/jornadas/${WORKDAY_IDS.open}`) },
  {
    name: 'jornada-cargando',
    path: at(ASSOCIATIONS[0].id, ACTIVE.id, `/jornadas/${WORKDAY_IDS.closed}`),
    options: { workdayDelayMs: 4000 },
    early: true,
  },
  { name: 'jornada-no-existe', path: at(ASSOCIATIONS[0].id, ACTIVE.id, '/jornadas/0192f3a8-d000-7000-8000-0000000000ff') },
  { name: 'consola-reciclador', path: at(ASSOCIATIONS[0].id, ACTIVE.id) },
];

for (const width of [360, 768, 1280, 1920]) {
  for (const shot of SHOTS) {
    test(`${shot.name} a ${width} px`, async ({ page }) => {
      // The brief reduces every animation to an instant under reduced motion: the capture shows the settled screen.
      await page.emulateMedia({ reducedMotion: 'reduce' });
      await page.setViewportSize({ width, height: width < 640 ? 800 : 900 });
      await signedIn(page, shot.path, { ...shot.options });
      if (shot.early) {
        await page.getByText('Cargando la jornada…').waitFor();
      } else {
        await page.locator('h1').first().waitFor();
        await page.waitForLoadState('networkidle');
      }
      await shot.prepare?.(page);
      await page.screenshot({ path: `${OUT}/${shot.name}-${width}.png`, fullPage: true });
    });
  }
}
