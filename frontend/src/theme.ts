import { Density, Mode, applyDensity, applyMode } from "@cloudscape-design/global-styles";

/**
 * Cloudscape visual theming (2.01): light/dark + comfortable/compact, persisted per user.
 * Cloudscape's "visual refresh" look — the current AWS console style — is the default theme.
 */
const MODE_KEY = "platform.ui.mode";
const DENSITY_KEY = "platform.ui.density";

export function initTheme(): void {
  applyMode(getMode() === "dark" ? Mode.Dark : Mode.Light);
  applyDensity(getDensity() === "compact" ? Density.Compact : Density.Comfortable);
}

export function getMode(): "light" | "dark" {
  const stored = localStorage.getItem(MODE_KEY);
  if (stored === "dark" || stored === "light") return stored;
  return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

export function toggleMode(): "light" | "dark" {
  const next = getMode() === "dark" ? "light" : "dark";
  localStorage.setItem(MODE_KEY, next);
  applyMode(next === "dark" ? Mode.Dark : Mode.Light);
  return next;
}

export function getDensity(): "comfortable" | "compact" {
  return localStorage.getItem(DENSITY_KEY) === "compact" ? "compact" : "comfortable";
}

export function toggleDensity(): "comfortable" | "compact" {
  const next = getDensity() === "compact" ? "comfortable" : "compact";
  localStorage.setItem(DENSITY_KEY, next);
  applyDensity(next === "compact" ? Density.Compact : Density.Comfortable);
  return next;
}
