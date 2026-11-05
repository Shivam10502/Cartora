# Cartora storefront

This React 19 and TypeScript application provides Cartora's customer storefront and admin screens. It uses React Router for navigation, Axios for the Spring Boot API, and CSS stylesheets organized by page concern in `src/`.

## Local development

Install dependencies and start Vite from this directory:

```powershell
npm ci
npm run dev
```

Vite proxies `/api` and `/uploads` to `http://localhost:8080`. The backend CORS configuration allows localhost Vite ports 5173 through 5180 when Vite selects an alternate port. Set `VITE_API_BASE_URL` to call a different API host directly; add that exact frontend origin to the backend's `app.cors.allowed-origins` setting.

## Quality checks

```powershell
npm run build
npm run lint
```

The build runs TypeScript project checks before creating optimized assets in `dist/`. See the [root README](../README.md) for database setup, API details, payment configuration, and the full application workflow.
