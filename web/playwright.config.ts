import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end tests run the PRODUCTION build against a local fake Appwrite
 * (tests/fake-appwrite). They never touch the real backend: every Appwrite
 * variable is set explicitly below, and process env wins over .env.local.
 */
const WEB_PORT = 3100;
const FAKE_PORT = 4010;
export const FAKE_URL = `http://127.0.0.1:${FAKE_PORT}/v1`;

export default defineConfig({
  testDir: "./tests/e2e",
  outputDir: "./tests/e2e/.results",
  fullyParallel: false,
  workers: 1, // the fake's key scopes are global state
  retries: 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [["list"]],
  use: {
    baseURL: `http://localhost:${WEB_PORT}`,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [
    {
      name: "phone",
      use: { ...devices["iPhone 13"], browserName: "chromium", defaultBrowserType: "chromium" },
    },
    { name: "laptop", use: { viewport: { width: 1440, height: 900 } } },
  ],
  webServer: [
    {
      command: `node tests/fake-appwrite/server.mjs ${FAKE_PORT}`,
      port: FAKE_PORT,
      reuseExistingServer: false,
      timeout: 30_000,
    },
    {
      command: `npx next start -p ${WEB_PORT}`,
      url: `http://localhost:${WEB_PORT}/api/health`,
      reuseExistingServer: false,
      timeout: 60_000,
      env: {
        NODE_ENV: "production",
        APPWRITE_ENDPOINT: FAKE_URL,
        APPWRITE_PROJECT_ID: "testproject",
        APPWRITE_DATABASE_ID: "testdb",
        APPWRITE_API_KEY: "fake-key",
        APP_TIME_ZONE: "Asia/Kolkata",
      },
    },
  ],
});
