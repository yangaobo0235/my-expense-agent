import { defineConfig, devices } from '@playwright/test';

process.env.all_proxy = '';
process.env.ALL_PROXY = '';
process.env.NO_PROXY = '127.0.0.1,localhost';
process.env.no_proxy = '127.0.0.1,localhost';

export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  expect: {
    timeout: 10_000,
  },
  fullyParallel: true,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://127.0.0.1:25106',
    trace: 'on-first-retry',
  },
  webServer: {
    command: 'node ./node_modules/vite/bin/vite.js --host 127.0.0.1 --port 25106',
    url: 'http://127.0.0.1:25106',
    reuseExistingServer: false,
    timeout: 60_000,
    env: {
      ...process.env,
      all_proxy: '',
      ALL_PROXY: '',
      NO_PROXY: '127.0.0.1,localhost',
      no_proxy: '127.0.0.1,localhost',
      VITE_AUTH_MODE: 'development',
      VITE_MOCK_API: '',
    },
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
