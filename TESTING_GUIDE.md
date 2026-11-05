# Testing Guide

The backend uses JUnit 5, Mockito, AssertJ, Spring Boot Test, MockMvc, and Spring Security Test. The current suite includes focused service/security/validation tests and PostgreSQL-backed migration/repository checks. It does not yet automate every external payment or browser workflow.

## Run Tests

From `backend/`:

```powershell
.\mvnw.cmd test
```

Run one class:

```powershell
.\mvnw.cmd -Dtest=PaymentServiceTest test
```

Cart and checkout service tests use Mockito and can be run without PostgreSQL:

```powershell
.\mvnw.cmd '-Dtest=CartServiceTest,CheckoutServiceTest' test
```

The full Spring context and MockMvc tests use the configured local PostgreSQL connection. Service unit tests use mocks and do not need PostgreSQL. Avoid running integration tests against a shared or production database.

From `frontend/`:

```powershell
npm run build
npm run lint
```

## Arrange, Act, Assert

Each unit test separates three ideas:

1. **Arrange:** construct inputs and fixture objects; stub collaborators with Mockito.
2. **Act:** call exactly the public service/controller method under test.
3. **Assert:** check the return value or exception and verify important collaborator interactions.

Example from product search: arrange a `PageImpl<Product>` and stub the repository; act by calling `searchProducts`; assert the page fields and captured `Pageable`, including requested sorting and the stable ID tie-breaker.

## Common Annotations

- `@Test`: marks a JUnit test method.
- `@ExtendWith(MockitoExtension.class)`: initializes Mockito mocks for a JUnit 5 test.
- `@Mock`: creates a collaborator mock whose behavior is controlled by the test.
- `@InjectMocks`: constructs the service and injects its mocks.
- `@SpringBootTest`: starts the real Spring application context for integration-level checks.
- `@AutoConfigureMockMvc`: provides MockMvc without opening a network port.
- `@MockitoBean`: replaces a Spring bean in the application context with a Mockito mock.
- `@WithMockUser` is available for tests, while current category tests attach identity directly to requests with `SecurityMockMvcRequestPostProcessors.user(...)` so the stateless security chain sees it.

## Mockito and AssertJ

- `when(mock.method(...)).thenReturn(value)` configures an expected collaborator response.
- `thenAnswer(...)` is useful when a repository `save()` should return the same object passed to it, possibly with an assigned ID.
- `verify(mock).method(...)` asserts that a collaboration occurred.
- `verify(mock, never()).method(...)` asserts that a rejected operation stopped before persistence.
- `any(Type.class)` matches an argument of a known type. For overloaded Spring Data methods, use a typed matcher such as `ArgumentMatchers.<Specification<Product>>any()` to avoid overload ambiguity.
- `ArgumentCaptor` captures arguments such as `Pageable` or a saved entity for precise assertions.
- AssertJ reads naturally: `assertThat(value).isEqualTo(expected)` and `assertThatThrownBy(...).isInstanceOf(...)`.

## Current Test Coverage

| Test class                                     | Behavior covered                                                                                                                                      |
| ---------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| `product.ProductServiceTest`                   | Product lookup, missing product, creation, duplicate SKU, missing category, search page metadata/sort, invalid sort rejected before repository access |
| `product.ProductImageUrlTest`                  | Image URL persists through product creation and update; replacement and deletion clean up managed files                                               |
| `product.ProductImageStorageServiceTest`       | Image signature/type validation, randomized filenames, size limit, managed deletion and traversal protection                                         |
| `payment.RazorpayPaymentServiceTest`           | Failed Razorpay payment retries reuse provider orders; signed failure webhooks persist state and duplicate event IDs are ignored atomically          |
| `payment.RefundServiceTest`                    | Settled partial refunds, failed refund recovery, and duplicate refund webhook idempotency                                                            |
| `order.OrderReservationExpirationServiceTest` | Expired pending orders release reserved stock and fail pending payments; captured payments keep their reservation                                    |
| `order.OrderReservationExpirationSchedulerTest` | Scheduler invokes each candidate through a separate per-order transactional service call                                                               |
| `product.ProductRepositoryIntegrationTest`     | PostgreSQL schema validation, catalog filtering/paging, and account-scoped recent-view ordering, deduplication, and five-item cap                    |
| `category.CategoryServiceTest`                 | Unique category creation, duplicate creation rejection, duplicate name during update                                                                  |
| `category.CategoryControllerAuthorizationTest` | Anonymous catalog read; customer denied category creation; admin allowed through MockMvc and method security                                          |
| `cart.CartServiceTest`                          | Cart creation, adding and merging items, stock limits, cross-customer item ownership, and clearing the current cart                                  |
| `checkout.CheckoutServiceTest`                  | Checkout line and aggregate totals, coupon discount application, empty cart rejection, and insufficient stock rejection                              |
| `payment.PaymentServiceTest`                   | Simulated failure, simulated success and stock deduction, stock exhaustion after simulated approval persists failure                                  |
| `order.OrderServiceTest`                       | Reject foreign order lookup; restore stock on cancellation; allow valid and reject invalid admin shipping transitions                                 |
| `address.AddressServiceTest`                   | First address defaults; deleting promotes a replacement; changing default clears the previous selection                                               |
| `user.UserServiceTest`                         | Password is encoded, registration assigns CUSTOMER, duplicate email rejected before encoding/persistence                                              |
| `auth.AuthServiceTest` and `security.JwtServiceTest` | Email normalization, invalid credential rejection, JWT subject and signature validation                                                           |
| `security.CorsConfigurationIntegrationTest`      | Local alternate Vite origins are allowed and unconfigured cross-origin requests are rejected                                                       |
| `user.PasswordResetServiceTest`                | One-time reset URL generation, hashed token persistence, password encoding, token consumption, expiry rejection                                       |
| `wishlist.WishlistServiceTest`                 | Duplicate add returns existing entry; new item is saved for the caller                                                                                |
| `review.ProductReviewServiceTest`              | Verified-purchase review creation, non-purchaser rejection, average rating and review count                                                           |
| `coupon.CouponServiceTest`                     | Percentage cap, fixed discount clamping, expired coupon rejection                                                                                     |
| `CartoraApplicationTests`                      | Full Spring application context starts against the configured PostgreSQL database                                                                     |

## How to Debug a Test

1. Run the focused Maven command for the test class.
2. Read the first failing assertion or exception, not only the final `BUILD FAILURE` line.
3. Check whether the failure is in Arrange (incorrect stub/fixture), Act (unexpected branch), or Assert (wrong behavior expectation).
4. In VS Code, set a breakpoint in the test and the called service method, then run that JUnit test with the Java debugger.
5. Keep tests deterministic: mock `SimulatedPaymentProcessor` to force success or failure rather than relying on its random runtime outcome.

## Manual Browser Smoke Check

The current local smoke check covers customer registration and login, saved-address selection during checkout, simulated payment and order confirmation, and page loading for the admin dashboard, catalog, category, order, customer, payment, and coupon screens. It also checks that an unauthenticated visit to an admin route redirects to login. Razorpay checkout callbacks still need merchant sandbox credentials and are not covered by this manual run.

## Coverage Still Needed

Dedicated Testcontainers coverage for isolated clean-database migration tests, concurrent checkout/payment race tests, Razorpay HTTP reconciliation tests, JWT-expiry tests, and browser workflow automation remain. Keep Arrange/Act/Assert explicit while expanding coverage.
