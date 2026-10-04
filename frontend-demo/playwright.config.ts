import { defineConfig, devices } from '@playwright/test';

// Not 4200: on Windows hosts with Hyper-V/WSL2 that port can sit in a reserved range (EACCES).
const PORT = 5201;

/** Browser flows against `ng serve`; the backend is replaced per test with page.route (see e2e/backend.ts). */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env['CI'],
  retries: 0,
  // A cold `ng serve` pre-bundles dependencies on the first request and reloads the page once.
  expect: { timeout: 15_000 },
  workers: 2,
  reporter: 'list',
  use: {
    baseURL: `http://localhost:${PORT}`,
    locale: 'es-PE',
    timezoneId: 'America/Lima',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: `npx ng serve --port ${PORT}`,
    url: `http://localhost:${PORT}`,
    reuseExistingServer: !process.env['CI'],
    timeout: 120_000,
  },
});
