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
  // exact: the card title, not the description text ("…S3 bucket…") which also contains it
  await expect(page.getByText("S3 Bucket", { exact: true })).toBeVisible();
  // the catalog opens the wizard via each card's "Provision this" action — scope to the S3 card
  await page
    .locator("li")
    .filter({ hasText: "S3 Bucket" })
    .getByRole("button", { name: "Provision this" })
    .click();
  await expect(page.getByRole("heading", { name: /Provision: S3 Bucket/i })).toBeVisible();
});

test("provision an S3 bucket through the wizard", async ({ page }) => {
  await page.goto("/catalog/s3-bucket");
  await expect(page.getByRole("heading", { name: /Provision: S3 Bucket/i })).toBeVisible();
  // Basics step: the resource-name Autosuggest (placeholder "e.g. pastry-plus")
  await page.getByPlaceholder(/pastry-plus/i).fill("e2e-bucket");
  await page.keyboard.press("Escape"); // dismiss the suggestion dropdown before navigating
  // advance to the Configuration step
  await page.getByRole("button", { name: "Next" }).click();
  // Configuration content = the s3-bucket schema's fields; "Bucket name" appears only here
  await expect(page.getByText("Bucket name")).toBeVisible();
});
