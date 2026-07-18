import { expect, test } from "@playwright/test";

/**
 * Golden path E2E (Phase A): browse the catalog, open the S3 wizard, and provision — the core
 * loop a user runs. Mock-mode, so it validates the UI wiring end to end without a backend.
 */
test("dashboard loads and shows the user", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByText(/Welcome back/i)).toBeVisible();
});

test("catalog lists templates and the S3 wizard opens", async ({ page }) => {
  await page.goto("/catalog");
  await expect(page.getByText("S3 Bucket")).toBeVisible();
  await page.getByText("S3 Bucket").first().click();
  // wizard Step 1
  await expect(page.getByText(/Basics/i)).toBeVisible();
});

test("provision an S3 bucket through the wizard", async ({ page }) => {
  await page.goto("/catalog/s3-bucket");
  await expect(page.getByText(/Provision: S3 Bucket/i)).toBeVisible();
  // resource name
  const name = page.getByPlaceholder(/pastry-plus/i).or(page.locator("input").first());
  await name.fill("e2e-bucket");
  // advance through the wizard
  await page.getByRole("button", { name: /Next/i }).click();
  await expect(page.getByText(/Configuration/i)).toBeVisible();
});
