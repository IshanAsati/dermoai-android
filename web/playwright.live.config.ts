import { defineConfig } from "@playwright/test";

/**
 * Smoke test against the REAL Appwrite project named in .env.local, using the
 * real API key and its real (limited) scopes. It creates a few ZZ-LIVE records
 * and deletes them again. Run deliberately:
 *   npm run test:live
 */
const PORT = 3101;

export default defineConfig({
  testDir: "./tests/live",
  outputDir: "./tests/e2e/.results/live",
  workers: 1,
  retries: 0,
  timeout: 120_000,
  expect: { timeout: 15_000 },
  reporter: [["list"]],
  use: { baseURL: `http://localhost:${PORT}`, viewport: { width: 1280, height: 800 } },
  webServer: {
    command: `npx next start -p ${PORT}`,
    url: `http://localhost:${PORT}/api/health`,
    reuseExistingServer: false,
    timeout: 60_000,
    env: { NODE_ENV: "production" },
  },
});
