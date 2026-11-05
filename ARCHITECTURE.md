# Architecture

Cartora is a single Spring Boot application and a separately built React SPA. It is not a microservice system.

## Backend

Package root: `com.ecom.cartora`.

```text
HTTP request
  -> Spring Security filter chain (JWT, route rules, CORS)
  -> Controller (HTTP mapping, request DTO validation)
  -> Service (business rules, caller ownership, transactions)
  -> Spring Data JPA repository
  -> PostgreSQL
```

Feature packages:

- `auth`: credential authentication and JWT response
- `security`: JWT filter/service, password authentication provider, authorization configuration and handlers
- `user`: registration, profile operations, roles, expiring hashed password reset tokens, and optional SMTP notifications
- `category`, `product`: catalog APIs, including JPA Specifications for product filtering
- `cart`, `checkout`, `order`, `payment`: purchase lifecycle, inventory reservations, simulated/Razorpay payments, webhooks, and refunds
- `address`, `wishlist`, `review`: customer-owned saved data and verified-purchase feedback
- `RecentlyViewedProductService`: account-scoped, five-item product history; guest browsing history remains local and merges at sign-in
- `coupon`: admin-managed discounts applied and persisted during checkout/order creation
- `product`: validated remote image URLs and admin-only local JPEG/PNG/WebP uploads; Compose persists uploaded files in a named volume
- `exception`: domain exceptions, response DTO, and `@RestControllerAdvice`

Controllers use request/response DTOs rather than returning JPA entities. Services resolve the current principal and apply ownership checks. Product and category write methods use method security; catalog reads are public. Most other endpoints require a valid JWT.

`PaymentService` delegates randomized outcomes to `SimulatedPaymentProcessor`; Razorpay order creation and provider reconciliation run through `RazorpayPaymentService`. HMAC signatures are checked in constant time, webhook event IDs are stored for idempotency, and admin refunds are tracked against provider status. Confirmation email events are delivered after the transaction commits.

## Frontend

`frontend/src` is a Vite React TypeScript application:

- `App.tsx`: routes, shared auth/cart state, customer screens, admin catalog controls
- `api.ts`: Axios instance, bearer-token interceptor, response models and endpoint functions
- `App.css`, `index.css`, `address.css`: responsive visual system and page styles

Development requests to `/api` are proxied by Vite to `http://localhost:8080`. `VITE_API_BASE_URL` can override the Axios base for another deployment. The backend CORS allow-list is configured with `app.cors.allowed-origins` (comma-separated origins).

The current client stores JWTs in local storage and clears a token on 401. Customer and admin routes are guarded in React and API endpoints enforce authentication and roles. This is a pragmatic SPA approach, not an HttpOnly-cookie design; XSS prevention and deployment security headers remain necessary.

## Transaction and Consistency Boundaries

Order creation locks the customer's cart row, locks product rows in sorted order, snapshots prices, validates any coupon against the current cart subtotal, reserves stock, and creates a pending order in one transaction. Simulated and Razorpay payments lock the order before updating payment, order, and inventory state. Existing pending orders from before reservation support still validate and lock product rows at payment time.

Flyway owns schema changes and Hibernate validates the migrated schema at startup. PostgreSQL-backed tests cover migration startup, product search/pagination, recently viewed ordering/limits, and core service behavior; broader concurrency and provider HTTP tests are still needed.

Address mutations lock the owning user row before changing defaults. Migration V8 cleans up legacy duplicate defaults and adds a partial unique index so each account can have at most one default address.

## Current Architecture Limits

The React app includes customer password recovery, account-persisted recently viewed products, and the admin customer, payment, sales, and image upload screens. Coupon use limits are not implemented. SMTP and Razorpay require environment configuration for external delivery and sandbox execution.

Pending orders hold stock for 30 minutes by default. A scheduled expiry scan releases the reservation and marks stale orders cancelled; the retention period and scan interval can be configured with `app.orders.reservation-minutes` and `app.orders.reservation-scan-ms`. Late gateway captures for a cancelled order are automatically refunded.
