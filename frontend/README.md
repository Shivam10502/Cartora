# Cartora storefront

This React 19 and TypeScript application provides Cartora's customer storefront and admin screens. It uses React Router for navigation, Axios for the Spring Boot API, and CSS stylesheets organized by page concern in `src/`.

## Local development

Install dependencies and start Vite from this directory:

```powershell
npm ci
npm run dev
```

Vite proxies `/api` and `/uploads` to `http://localhost:8080`. The backend CORS configuration allows localhost Vite ports 5173 through 5180 when Vite selects an alternate port. Set `VITE_API_BASE_URL` to call a different API host directly; add that exact frontend origin to the backend's `app.cors.allowed-origins` setting.

## Shopping experience

The storefront includes photo-led collections, URL-based search/category/price filters, price presets, sorting, pagination, a persistent grid/list preference, product quick views, and a bag drawer. Compare up to three pieces without signing in; comparison IDs are stored in this browser and product details are refreshed after reload. Wishlist and bag actions use the authenticated backend APIs. Product pages offer quantity controls and related products from the same category.

Dialogs support Escape, keyboard focus restoration, and background scroll locking. Mobile navigation, empty/error states, notifications, and reduced-motion preferences are supported. Images have a local fallback, and the included photography is served locally rather than relying on remote image requests at runtime.

The optional local catalog seed is documented in the root README. No sample inventory is inserted automatically during application startup.

## Quality checks

```powershell
npm run build
npm run lint
```

The build runs TypeScript project checks before creating optimized assets in `dist/`. See the [root README](../README.md) for database setup, API details, payment configuration, and the full application workflow.

## Browser checks

```powershell
npm run test:e2e
```

Playwright uses installed Google Chrome by default and starts or reuses Vite on port 5173. API fixtures isolate these tests from your database. Checks run at desktop and mobile widths and cover rapid filter changes, pagination, comparison persistence and limits, quick-view focus and quantities, cart updates, search, empty states, retries, and navigation. Set `PLAYWRIGHT_CHANNEL=msedge` in your environment to use installed Microsoft Edge instead.
