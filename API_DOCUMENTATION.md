# API Documentation

Base URL: `http://localhost:8080`

All endpoints except registration, login, password-reset requests, and catalog `GET` routes require a JWT bearer token. Product and category write endpoints require the `ADMIN` role. Requests and responses use JSON unless noted. Exact error payloads are handled by the global exception handler and security handlers.

Successful creation endpoints for accounts, products, categories, orders, coupons, reviews, and addresses return `201 Created`. Updates return `200 OK`; deletes return `204 No Content` where a response body is not needed.

## Authentication and Account

| Method | Path                     | Access        | Purpose                                                            |
| ------ | ------------------------ | ------------- | ------------------------------------------------------------------ |
| `POST` | `/api/users/register`    | Public        | Register a customer; role is assigned as `CUSTOMER` by the backend |
| `POST` | `/api/auth/login`        | Public        | Authenticate and receive `{ "token": "..." }`                      |
| `POST` | `/api/auth/forgot-password` | Public      | Request a reset link; response does not reveal whether the email exists |
| `PUT`  | `/api/auth/reset-password`  | Public      | Consume a valid one-time token and set a new password               |
| `GET`  | `/api/users/me`          | Authenticated | Read the current profile                                           |
| `PUT`  | `/api/users/me`          | Authenticated | Update the current user's name                                     |
| `PUT`  | `/api/users/me/password` | Authenticated | Change password using current and new password                     |
| `GET`  | `/api/users/me/recently-viewed` | Authenticated | List up to five most recently viewed products for this account |
| `POST` | `/api/users/me/recently-viewed/{productId}` | Authenticated | Record a product view and retain the five newest products |

Reset tokens are stored as SHA-256 hashes, expire after 30 minutes, are consumed once, and are rate-limited per account. SMTP must be configured for reset emails and post-payment order confirmations. When email is not configured, reset requests still return the same generic response but no reset link can be delivered.

Example login:

```json
{
  "email": "customer@example.com",
  "password": "YourPassword1!"
}
```

## Catalog

| Method   | Path                   | Access | Purpose                                        |
| -------- | ---------------------- | ------ | ---------------------------------------------- |
| `GET`    | `/api/products`        | Public | Search/filter products with paging and sorting |
| `GET`    | `/api/products/{id}`   | Public | Read one product                               |
| `POST`   | `/api/products`        | Admin  | Create a product                               |
| `PUT`    | `/api/products/{id}`   | Admin  | Update a product                               |
| `DELETE` | `/api/products/{id}`   | Admin  | Delete a product                               |
| `POST`   | `/api/products/{id}/image` | Admin | Upload a JPEG, PNG, or WebP image (multipart field `file`, max 5 MB) |
| `GET`    | `/api/categories`      | Public | List categories                                |
| `GET`    | `/api/categories/{id}` | Public | Read one category                              |
| `POST`   | `/api/categories`      | Admin  | Create a category                              |
| `PUT`    | `/api/categories/{id}` | Admin  | Update a category                              |
| `DELETE` | `/api/categories/{id}` | Admin  | Delete a category                              |

Product create/update requests accept an optional HTTP(S) `imageUrl`, returned in product responses. Admin uploads replace the product image URL with a generated `/uploads/...` path. Compose persists uploaded files in the `cartora-product-images` volume.

Product create/update requests may include an optional `imageUrl` using HTTP or HTTPS. Admin uploads replace it with a generated `/uploads/...` path; Compose persists uploaded files in the `cartora-product-images` volume.

Product search query parameters are `search`, `categoryId`, `minPrice`, `maxPrice`, `page`, `size`, `sortBy`, and `direction`. Defaults are page `0`, size `10`, `createdAt`, and `desc`. Supported sort fields are `name`, `price`, and `createdAt`; size is limited to 1-100.

Example:

```http
GET /api/products?search=lamp&minPrice=500&maxPrice=5000&page=0&size=12&sortBy=price&direction=asc
```

## Cart and Checkout

| Method   | Path                           | Access        | Purpose                                               |
| -------- | ------------------------------ | ------------- | ----------------------------------------------------- |
| `GET`    | `/api/cart`                    | Authenticated | Read the current user's cart                          |
| `POST`   | `/api/cart/items`              | Authenticated | Add `{ "productId": 1, "quantity": 2 }`               |
| `PUT`    | `/api/cart/items/{cartItemId}` | Authenticated | Set an item's quantity                                |
| `DELETE` | `/api/cart/items/{cartItemId}` | Authenticated | Remove an item owned by the caller                    |
| `DELETE` | `/api/cart`                    | Authenticated | Clear the current cart                                |
| `POST`   | `/api/checkout`                | Authenticated | Validate the cart and return a shipping/total preview |

Checkout request example:

```json
{
  "shippingAddress": {
    "recipientName": "Asha Rao",
    "houseNumberOrBuildingNumber": "12 Park Street",
    "localityOrLandmark": "Near the library",
    "city": "Pune",
    "state": "Maharashtra",
    "postalCode": "411001",
    "country": "India"
  }
}
```

Checkout previews the address, current stock, coupon discount, and total. Include optional `couponCode` at the request root. `POST /api/orders` requires the shipping address, accepts the same coupon code, and recalculates the discount server-side before saving an order address snapshot, reserving inventory, and clearing the cart.

## Orders and Payments

| Method | Path                           | Access        | Purpose                                                                                  |
| ------ | ------------------------------ | ------------- | ---------------------------------------------------------------------------------------- |
| `POST` | `/api/orders`                  | Authenticated | Create a pending order; body requires `shippingAddress`, optional `couponCode` |
| `GET`  | `/api/orders`                  | Authenticated | List the caller's orders                                                                 |
| `GET`  | `/api/orders/{orderId}`        | Owner         | Read order details                                                                       |
| `PUT`  | `/api/orders/{orderId}/cancel` | Owner         | Cancel a pending order and restore stock                                                 |
| `POST` | `/api/payments`                | Owner         | Attempt a simulated payment when the simulator is configured                              |

Admin order management:

| Method  | Path                                 | Access | Purpose                                                             |
| ------- | ------------------------------------ | ------ | ------------------------------------------------------------------- |
| `GET`   | `/api/admin/orders`                  | Admin  | List orders newest first                                            |
| `PATCH` | `/api/admin/orders/{orderId}/status` | Admin  | Advance confirmed orders to shipped and shipped orders to delivered |

Other status transitions are rejected. Payment confirmation remains in the simulated payment workflow; customer cancellation remains on the owner-scoped endpoint.

Payment request:

```json
{
  "orderId": 12,
  "paymentMethod": "UPI"
}
```

Supported simulated payment methods: `CARD`, `UPI`, `NET_BANKING`, `COD`. The simulator returns a randomized test success/failure outcome. New orders reserve stock before payment; cancellation releases it, and successful payment confirms the order. Pending reservations expire after 30 minutes by default; a late captured gateway payment for a cancelled order is refunded. Payment statuses include `PENDING`, `SUCCESS`, `FAILED`, `REFUND_PENDING`, `PARTIALLY_REFUNDED`, and `REFUNDED`.

When `PAYMENT_PROVIDER=razorpay`, the storefront calls `GET /api/payments/provider`, `POST /api/payments/gateway-order`, and `POST /api/payments/verify`. The server creates gateway orders and validates callback signatures and provider-side amount, currency, order, and capture state. `POST /api/payments/webhook` accepts signed, idempotent payment and refund events. Configure the key ID, key secret, and webhook secret in the backend environment.

Admin reporting endpoints are `GET /api/admin/customers`, `GET /api/admin/payments`, `GET /api/admin/payments/{paymentId}/refunds`, and `GET /api/admin/dashboard` with `GET /api/admin/dashboard/sales?days=14`. Full Razorpay refunds are submitted with `POST /api/admin/payments/{paymentId}/refunds`; an optional body `{ "amount": 10.00 }` requests a partial refund.

Refund intents and their idempotency keys are persisted before calling Razorpay. If the provider response is ambiguous, the API returns a refund in `REQUESTED` state; retry the same request with the same amount to reuse its key and payload. Do not initiate a different refund while a request remains unresolved.

## Addresses

| Method   | Path                          | Access        | Purpose                                                   |
| -------- | ----------------------------- | ------------- | --------------------------------------------------------- |
| `POST`   | `/api/addresses`              | Authenticated | Create an address (first address becomes default; mutations are serialized per account) |
| `GET`    | `/api/addresses`              | Authenticated | List the caller's addresses                               |
| `GET`    | `/api/addresses/{id}`         | Owner         | Read one address                                          |
| `PUT`    | `/api/addresses/{id}`         | Owner         | Update one address                                        |
| `DELETE` | `/api/addresses/{id}`         | Owner         | Delete an address; choose a replacement default if needed |
| `PATCH`  | `/api/addresses/{id}/default` | Owner         | Select the account's only default address                 |

Postal codes are validated as six digits by the current request DTO.

## Wishlist

| Method   | Path                              | Access        | Purpose                                                                |
| -------- | --------------------------------- | ------------- | ---------------------------------------------------------------------- |
| `GET`    | `/api/wishlist`                   | Authenticated | List the caller's saved products                                       |
| `POST`   | `/api/wishlist/items`             | Authenticated | Add `{ "productId": 1 }`; duplicate additions return the existing item |
| `DELETE` | `/api/wishlist/items/{productId}` | Owner         | Remove a product from the caller's wishlist                            |

A database unique constraint prevents duplicate `(user_id, product_id)` rows.

## Product Reviews and Ratings

| Method   | Path                                        | Access                           | Purpose                                |
| -------- | ------------------------------------------- | -------------------------------- | -------------------------------------- |
| `GET`    | `/api/products/{productId}/reviews`         | Public                           | List product reviews, newest first     |
| `GET`    | `/api/products/{productId}/reviews/summary` | Public                           | Return average rating and review count |
| `POST`   | `/api/products/{productId}/reviews`         | Authenticated verified purchaser | Create one review for the product      |
| `PUT`    | `/api/products/{productId}/reviews/me`      | Review owner                     | Update the caller's review             |
| `DELETE` | `/api/products/{productId}/reviews/me`      | Review owner                     | Delete the caller's review             |

Review request example:

```json
{
  "rating": 5,
  "title": "Wonderful",
  "comment": "Works beautifully on my desk."
}
```

Ratings must be 1-5. A review can be created only after the customer has a confirmed, shipped, or delivered order containing that product. Each customer may have one review per product; that review can be updated or removed by its owner.

## Coupons

| Method  | Path                                          | Access | Purpose                                    |
| ------- | --------------------------------------------- | ------ | ------------------------------------------ |
| `GET`   | `/api/admin/coupons`                          | Admin  | List coupon definitions                    |
| `POST`  | `/api/admin/coupons`                          | Admin  | Create a percentage or fixed-amount coupon |
| `PATCH` | `/api/admin/coupons/{id}/active?active=false` | Admin  | Activate or deactivate a coupon            |

Coupon request example:

```json
{
  "code": "SAVE10",
  "discountType": "PERCENTAGE",
  "discountValue": 10,
  "minimumOrderAmount": 1000,
  "maximumDiscountAmount": 500,
  "validFrom": null,
  "validUntil": null
}
```

`discountType` is `PERCENTAGE` or `FIXED_AMOUNT`. Codes are case-insensitive. The backend enforces active state, validity dates, minimum spend, percentage range, optional maximum discount, and a discount amount no larger than the cart subtotal. Coupon use limits/per-customer limits are not implemented.

## Current Boundaries

Product images can be supplied as validated HTTP(S) URLs or uploaded by an admin as JPEG, PNG, or WebP files up to 5 MB using `POST /api/products/{id}/image` with multipart field `file`. Uploaded files use generated names and are served through `/uploads/`. Signed-in customers can list and record the five most recently viewed products; guest history stays in browser storage and merges into the account after sign-in. Coupon use limits/per-customer limits are not implemented.
