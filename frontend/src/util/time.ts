/** Single relative-time helper (2.01 enhancement) — local time with UTC tooltip everywhere. */
export function relativeTime(iso: string): string {
  const seconds = Math.round((Date.now() - new Date(iso).getTime()) / 1000);
  if (seconds < 60) return `${seconds}s ago`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
  return `${Math.floor(seconds / 86400)}d ago`;
}

export function localWithUtcTitle(iso: string): { text: string; title: string } {
  const date = new Date(iso);
  return { text: date.toLocaleString(), title: `${date.toISOString()} (UTC)` };
}
