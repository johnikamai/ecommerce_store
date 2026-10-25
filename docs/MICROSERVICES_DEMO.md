# ShopEase review demo — verified transitional architecture

## Launch the existing application

Requirements: local MySQL on 3306 with the existing `ecommerce_db`, Java 21+ (Java 25 tested), Node 22.12+ and installed frontend dependencies.

From the repository root in PowerShell:

```powershell
./start-services.ps1 -Build
node ./run_integration_tests.js
```

Or run `start-services.bat` when the JARs are already built. Open http://localhost:5173/products (browse) or http://localhost:5173/ (sign in). Eureka: http://localhost:8761. All frontend API traffic uses http://localhost:8080/api.

The launcher uses hidden processes, records their process IDs and logs in ignored `.local/`, and waits for actual HTTP readiness. Existing listeners are not killed. A process occupying 8080 that is not the expected Gateway is reported as an error.

For another machine, supply database configuration through `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`; never put credentials in tracked files. On this machine the previous local database credential was retained only in ignored `.local/application.properties`. This file must not be shared or committed. A random persistent JWT signing key is generated there on first launch unless `JWT_SECRET` is supplied. Changing the signing key invalidates existing sessions.

## What is actually migrated

| Request through Gateway 8080 | Execution owner | Implementation |
| --- | --- | --- |
| GET `/api/products` | product-service 8082 | Real JPA ProductRepository reads |
| GET `/api/products/{numericId}` | product-service 8082 | Real stored product or HTTP 404 |
| GET `/api/categories` | product-service 8082 | Real categories and product counts |
| All other `/api/**` | monolith compatibility backend 8087 | Existing controllers/services/repositories/security |
| `/products/**`, `/product-photos/**`, `/images/**` | monolith compatibility backend 8087 | Existing static product assets |

Every routed response includes `X-ShopEase-Backend: product-service` or `monolith-compatibility`. Gateway uses Eureka discovery for product-service. The compatibility backend uses an explicit HTTP URI. `PRODUCT_SERVICE_URI`, `COMPATIBILITY_URI` and `EUREKA_URI` can override destinations.

This is **not a completed six-service migration**. Product reads are migrated business endpoints. Authentication/JWT, catalog aggregates, fuzzy search, reviews, product/category writes, wishlist, addresses, checkout/orders, payments, coupons, admin/staff, group orders/split bill, recommendations and notifications still execute the original monolith code through the Gateway. The five other service scaffolds are not required or started for this demo. Their health controllers are not evidence of business migration.

The existing cart is per-customer browser localStorage in `frontend/src/context/CartContext.jsx`; there was no cart REST controller. It remains unchanged. Checkout sends the real quote and order APIs through Gateway.

## Data preservation

The `compatibility` profiles use the existing `ecommerce_db`; no product/customer/order records are copied into empty service databases. Hibernate DDL is disabled (`none`), catalog startup seeding is disabled, and the existing review-seeder switch is disabled. The ProductSeeder bean remains available to the admin catalog controller, so the existing explicit reseed feature is preserved.

Product-service currently shares the legacy product/category tables as a documented transition. Its public controller performs reads only. Legacy checkout/admin remain the authoritative writers, so stock changes appear immediately in product-service. This does not claim independent database ownership. There are no new cross-service JPA entity relationships. Future separation needs explicit APIs/events for stock and catalog statistics before moving writes.

An actual cancellation test exposed older MySQL ENUM definitions missing values already used by the Java code. `backend/migrations/20261025_append_order_statuses.sql` was applied locally: it appends CANCELLED/REFUNDED payment states and PACKED/OUT_FOR_DELIVERY order states, preserving existing values, their ordering and all stored rows. It does not drop tables, recreate data, or seed anything. Review the existing enum definitions before applying this script to a different database.

The old Docker Compose configuration still describes the scaffold services and separate databases. It is not the verified launch path and must not be used to demonstrate this transitional runtime without further configuration.

## Verification evidence — 25 October 2026

- Backend regression suite: all 154 tests passed (153 original tests plus the new coupon-access regression), zero failures/errors; Maven package succeeded.
- Frontend: 21 tests passed, production build passed. Lint completed with pre-existing warnings, including React effect and unused-variable warnings. No visual redesign or feature removal.
- Real HTTP smoke test through 8080: Eureka 200 with PRODUCT-SERVICE and API-GATEWAY UP; Gateway health 200; products/categories/detail 200; missing product 404; 1,222 existing products loaded; catalog/search 200.
- Auth: register 200, real demo OTP verify 200, login 200, JWT-backed requests accepted. Anonymous customer-order access 401; customer access to admin orders/product creation and another customer's orders 403.
- Wishlist: add 201, read 200, remove 204.
- Checkout: server quote 200, order creation 201, product stock decremented and observed via product-service; own order list/tracking/payment/notifications/recommendations 200; cancellation 200 and stock restored.
- Smoke-test orders 36, 37 and 39 and browser-test order 40 were cancelled through business APIs. Test customer and cancelled-order history remain for review; existing customer data was not deleted.
- Browser: original storefront rendered all 1,222 products and product images; customer sign-in, product detail, Add to Cart, server quote, CASH checkout (order 40), order/payment notifications and cancellation all passed. Screenshot evidence is retained in `.local/ui-checkout.png`.
- Email is unconfigured locally: existing demo OTP behavior is used. Real email delivery and real card/UPI processing are not verified; checkout validation used CASH. The application labels itself a demo.

Raw build/test/HTTP logs are kept in `.local/` and intentionally excluded from Git, since runtime logs may contain private application information.

## Changed implementation areas

- Gateway routing, explicit compatibility response markers, browser CORS and duplicate-header removal.
- Product-service CatalogController and compatibility database profile; explicit Lombok annotation processing for Java 25.
- Backend compatibility profile and non-mutating startup switches; database password default removed; Java 25-compatible Byte Buddy for tests.
- Specific public coupon suggestion access, preserving admin-only coupon management, with a security regression test.
- Additive legacy enum SQL fix; real HTTP smoke suite with status/data assertions and test-order cancellation.
- Windows PowerShell/batch launcher, local-secret/log exclusions and this documentation.

## Remaining migration work and limits

1. Complete auth-service using the original OTP, password-reset, account-disable, staff-permission and JWT fingerprint rules; validate the same tokens in downstream services before switching Gateway routes.
2. Move product writes and enriched catalog/search with explicit stock/statistics APIs; separate database ownership only after data migration is verified.
3. Extract order/payment workflows with customer/product IDs and explicit service calls, transactional stock reservation and idempotency.
4. Extract notifications/recommendations/admin/staff/group-order workflows, preserving ownership checks and API shapes.
5. Run an authenticated admin/staff and group-order UI suite, and verify configured email/online payment providers when available.
6. Update and verify Docker Compose for the selected architecture.

Concurrent workspace activity added auth-service scaffolding and created commit `ddf2320` while verification was running. That commit contains the demo implementation plus unrelated auth files. This verification covers the four launched backend modules only; the new auth/admin scaffolding was not built or tested by this work. No branch history was rewritten.


## Exact implementation files in commit ddf2320

- `.gitignore`
- `api-gateway/src/main/resources/application.yml`
- `backend/migrations/20261025_append_order_statuses.sql`
- `backend/pom.xml`
- `backend/src/main/java/com/ecommerce/ecommerce_system/config/ProductSeeder.java`
- `backend/src/main/java/com/ecommerce/ecommerce_system/config/ReviewSeeder.java`
- `backend/src/main/java/com/ecommerce/ecommerce_system/security/SecurityConfig.java`
- `backend/src/main/resources/application-compatibility.properties`
- `backend/src/main/resources/application.properties`
- `backend/src/test/java/com/ecommerce/ecommerce_system/security/CouponAccessTest.java`
- `run_integration_tests.js`
- `services/auth-service/src/main/java/com/ecommerce/auth/controller/AdminController.java`
- `services/auth-service/src/main/java/com/ecommerce/auth/controller/AdminStaffController.java`
- `services/auth-service/src/main/java/com/ecommerce/auth/model/ApprovalStatus.java`
- `services/auth-service/src/main/java/com/ecommerce/auth/model/StaffActivityLog.java`
- `services/auth-service/src/main/java/com/ecommerce/auth/model/TaskUpdateRequest.java`
- `services/auth-service/src/main/java/com/ecommerce/auth/repo/StaffActivityLogRepository.java`
- `services/auth-service/src/main/java/com/ecommerce/auth/repo/UserRepository.java`
- `services/auth-service/src/main/resources/application.yml`
- `services/product-service/pom.xml`
- `services/product-service/src/main/java/com/ecommerce/product/controller/CatalogController.java`
- `services/product-service/src/main/resources/application-compatibility.yml`
- `start-services.bat`
- `start-services.ps1`

This documentation and the endpoint inventory are recorded in a follow-up commit.
