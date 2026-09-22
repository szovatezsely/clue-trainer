/**
 * When the app itself last changed, shown in the header next to the date the
 * clues were scraped. The two move independently: a new feature does not make
 * the guides any fresher, and a re-scrape does not change the app.
 *
 * Bump this by hand with each release. It cannot come from git at build time,
 * because the Docker build only sees the `frontend/` directory.
 */
export const APP_UPDATED = '2026-09-22'
