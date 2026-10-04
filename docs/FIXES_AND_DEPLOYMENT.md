# ShopEase fixes and deployment

The project source has been updated. The existing Vercel/Render deployment is not updated by downloading this ZIP. Deploy the backend and frontend together because checkout now requires an authoritative quote and its expected total.

## Changes

- Reset, registration and resend endpoints never return or log OTP codes. Email failure returns HTTP 503.
- OTPs are stored as BCrypt hashes in the database, survive restarts, expire after five minutes, allow five verification attempts and enforce a 60-second resend cooldown. Codes are consumed atomically.
- JWT signing no longer uses a hardcoded secret. Password changes invalidate existing tokens. Disabled accounts cannot authenticate with a previously issued token.
- Customer email changes require the current password and a code delivered to the new address. Both login and customer profile email are updated together.
- `/api/orders/quote` calculates current product prices, coupon/bundle/loyalty discounts, delivery and configured tax without changing stock or coupon usage. Checkout recomputes and compares `expectedTotal` before reserving stock. The UI does not submit a stale quote.
- Empty orders, duplicate product lines, invalid quantities, missing/short addresses and unknown payment methods are rejected by the backend.
- Product, customer, order, payment and return records have optimistic concurrency checks. Conflicting requests return HTTP 409 and must be retried after a refresh. A real database regression test verifies that only one buyer can reserve the last unit.
- Rewards are awarded on delivery. Cancellation/refund reverses awarded order points, removes a referral bonus when no qualifying delivered purchase remains, and recalculates the tier using delivered, unrefunded goods spend. Cancelling a packed order also releases its reserved stock.
- Carts are stored separately for each customer. Logout switches cart identity, and session changes synchronize across tabs. Anonymous visitors no longer fall back to demo customer 1.
- Guests can browse products, detail pages and comparisons. Wishlist, restock and cart actions request sign-in.
- Expired client tokens are rejected by route guards. Authentication failures clear the session and return the shopper to sign-in; backend JSON errors are rendered as readable messages.
- Referral-code generation slices the cleaned name safely.
- Manual payment records always take their amount from the order.
- Customer notification email/SMS is dispatched after transaction commit, avoiding confirmations for rolled-back orders.
- Removed the customer-facing build debugging message and the database password default. New UI labels have English, Hindi and Spanish translations.

## Backend environment

Keep existing database settings and configure:

| Variable | Purpose |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Existing MySQL database connection. The source no longer supplies a password. |
| `JWT_SECRET` | A stable, random secret of at least 32 bytes. Generate one with `node -e "console.log(require('crypto').randomBytes(48).toString('base64'))"`. Store the output in your backend host's environment settings. |
| `BREVO_API_KEY` | Transactional email provider key. |
| `BREVO_SENDER_EMAIL` | Verified sender address. |
| `BREVO_SENDER_NAME` | Optional; defaults to ShopEase. |
| `DEMO_PAYMENTS_ENABLED` | Defaults to `false`. Only set `true` for an explicitly simulated demonstration. |
| `RECONCILE_REWARDS` | Defaults to `false`. Set `true` for one startup against a legacy database to rebuild rewards from delivered, unrefunded orders and valid referrals; then remove/unset it. |

If `JWT_SECRET` is absent, startup uses a random ephemeral signing key. This is secure but logs users out on each restart and cannot share sessions across instances. Set a stable secret for deployment. Old tokens must be replaced by signing in again after this update.

The existing `ddl-auto=update` creates OTP/email-change tables and adds concurrency/reward fields. For existing orders, the new rewards flag defaults to true because the old checkout awarded points immediately. The opt-in reconciliation rebuilds existing totals under the new delivery-based rules and is idempotent; it replaces old point balances using this project's order/referral rules. It should be enabled only for the first migration startup and disabled afterward. Do not run old and new backend versions simultaneously during the update.

## Payments

Cash on delivery works by default and remains pending until an admin records collection. UPI/card options appear only when `DEMO_PAYMENTS_ENABLED=true`, with an explicit on-screen demo label. They do not transfer money. A real gateway and verified payment/refund callbacks still require merchant configuration and integration; the project does not claim simulated transactions are real.

## Frontend deployment

Use the `frontend` directory as the Vercel project root, `npm run build` as the build command and `dist` as the output directory. `vercel.json` preserves SPA routes. `VITE_API_BASE_URL` is optional; blank uses the existing production API fallback. If overriding, include the `/api` suffix. The backend CORS configuration already allows the supplied Vercel storefront origin.

## Validation

- Frontend production build passes.
- All 702 referenced translation keys resolve in all three locales.
- Two frontend session regression tests pass.
- All 88 backend tests pass, including full application startup on an isolated H2 database, OTP handling, mail-failure reset safety, password-token revocation, disabled-account access, checkout quotes, reward reconciliation and concurrent last-unit reservation.
- Frontend lint reports warnings, with no errors. The production build reports a large-bundle warning.

On Windows:

```powershell
cd backend
.\mvnw.cmd test
cd ..\frontend
npm ci
npm run build
npm run check:i18n
npm test
npm run lint
```

The automated database tests use H2, not the live MySQL database. Live email delivery and signed-in production flows still need verification after deployment. In environments that prohibit Mockito's dynamic JVM attachment, launch tests with a supported Byte Buddy/Mockito startup agent; this was used for the local test run.

This source ZIP excludes Git history, installed dependencies, generated build output and local credential files. Install frontend dependencies with `npm ci` and build the backend with Maven.
