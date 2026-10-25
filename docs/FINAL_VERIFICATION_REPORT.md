# ShopEase final local verification — 26 October 2026

**Local application acceptance passes within the limits below. Production deployment is not approved or certified.** Work is on `feature/microservices-migration`, based on `186b377`. `main` remains at `228475944356dcb36241e902000d9187b357360d`.

## Fifteen smart shopping features

See [the complete feature-by-feature evidence table](SMART_FEATURE_AUDIT.md), which identifies each frontend, Gateway endpoint, owner, browser/API evidence and operating mode. All fifteen customer-facing workflows now work locally. The initial 8 working/5 partial/2 missing count was corrected to 7/6/2 when Q&A's unsupported warranty statement was discovered; the corrected baseline is retained separately.

The seven original gaps were repaired: real pixel-based catalogue matching, mounted recorded-price chart, public specification-based compatibility checker, quote-based budget tracker, matching bundle tiers, rendered data-based verdict, and backend-persisted wishlist targets/opt-ins. Product Q&A no longer invents warranty/return terms or labels prices tax-inclusive. Comparison verdicts no longer invent ratings/performance. Prices display paise so rounding cannot hide checkout differences.

These are mostly **rules/statistics/image-retrieval features**. No live LLM response was demonstrated. Image matching uses actual image pixels but does not recognize arbitrary objects semantically. Compatibility can correctly be unknown when specifications are missing. Wishlist external email/SMS is not included in the verified in-app alert result. Existing unscoped browser-only wishlist settings are retained locally; re-save desired preferences to attach them to the account.

## Staff request, approval and access

The existing staff implementation was retained. Verified through Gateway: public application, disabled login before approval, admin pending list and new persisted application notification, approval with explicit permissions, approved login/dashboard, rejection, role edits, immediately enforced permission revocation, suspension, in-app decision notifications and audit entries.

An additional test exposed a real migration authorization gap: any STAFF could edit products regardless of assigned permissions. Product creation now requires `PRODUCT_CREATE`; editing requires `PRODUCT_EDIT`; deletion remains admin-only. Cross-customer order reads/fulfilment/tracking also require the relevant Order permissions. Auth is consulted on each downstream request, so an existing token does not preserve removed permissions.

Browser verification inspected the public request/status page, approved a synthetic pending request in Admin, changed its permissions to TASK_VIEW only, signed into the staff dashboard, observed only permitted navigation, rejected direct entry to Approvals, and returned to login after suspension. The sign-in UI now explicitly says **Admin / Staff** and the staff page links directly to it. The synthetic browser staff account was suspended afterward. No real employee's permissions were changed.

## Group order and split bill

Reused the existing Order coordinator, Payment ledger, durable Notification delivery and React pages. Added an actual shared-cart product picker/quantity controls, an in-app invitation action and copyable invite link. Corrected the Custom Split button to include the required current amounts. Hydrated customer IDs before settlement messages so new audit entries name the member. Order status/payment changes now publish updates to non-host group members.

Gateway tests cover two ordinary CUSTOMER accounts, rejection before membership, code joining, host member removal/rejoin, shared cart, exact-cent Equal Split and Custom Split, each confirmation, freeze, one combined checkout, member report, host receipt, named audit history, zero remaining collection, and durable invitation/order-status notifications. A separate cart parity suite verifies coupon/bundle/shipping/tax totals through Order and Payment.

Fresh browser host workflow: created group 25, sent an invitation, added a disposable product to the shared cart, viewed the second member's cart line, changed the ₹285.00 total to custom shares **₹100.00/₹185.00**, confirmed, froze, placed explicit mock CARD order 82, and confirmed the reported member settlement. The peer's join/add/confirm/report actions were driven through Gateway; the host actions and displayed amounts were exercised through React. No money was transferred. Synthetic financial history is intentionally retained.

**Payment boundary:** Host Pays is supported: one ShopEase payment plus member debts settled directly with the host. Equal/Custom describe allocation, not independent real online charges. CARD/UPI are explicit local demos and disabled by default in production configuration. Individual online charging still requires a real provider integration; it is not silently simulated as complete.

## Migration regressions

| Previous failure | Repair and observed result |
|---|---|
| Account/profile cleanup | Admin deletion checks owned order/group/referral history, disables the login, calls protected idempotent non-financial owner cleanup, then removes login/profile atomically in Auth. Accounts with financial/group history are protected. On an owner failure, retain the disabled account and retry the same admin request; cross-owner work is not one distributed transaction. Notification retains deletion markers so delayed facts cannot recreate deleted-account data. Disposable-account Gateway regression passes; no original account was deleted. |
| Unpaid COD return | Order uses Payment's cancellation result when money was never collected. Paid returns still refund the Payment ledger. The unpaid test now ends with matching CANCELLED payment status in both owners, with no claim that uncollected money was refunded. |
| `/api/health` | Auth now maps both `/health` and `/api/health`; public Gateway request returns 200 from Auth. All actuator checks also pass. |

The old [VERIFICATION_REPORT.md](VERIFICATION_REPORT.md) is historical evidence of the failures, not the current acceptance result.

## Automated and runtime results

| Check | Result |
|---|---|
| Auth Java | 23 tests, zero failures/errors/skips; package successful |
| Product Java | 40 tests, zero failures/errors/skips; package successful |
| Order Java | 58 tests, zero failures/errors/skips; package successful |
| Payment Java | 5 tests, zero failures/errors/skips; package successful |
| Notification Java | 4 tests, zero failures/errors/skips; package successful |
| Recommendation Java | 53 tests, zero failures/errors/skips; package successful |
| Shared service security | 4 tests, zero failures/errors/skips |
| **Java total** | **187 tests** |
| Gateway / Eureka Java | Compile/test goals successful; zero Java test cases exist in these modules |
| Frontend | **35 tests passed**, zero failed; HTTPS-configured production build successful |
| Gateway acceptance | All **14 suites** pass; final combined run logged **230 PASS checks** and exited 0. Includes staff lifecycle, three regressions, groups, cart parity and real wishlist events. |
| Runtime | All six services actuator UP and Eureka UP; expected native owner headers; internal requests without key return 401; cleanup endpoints are not Gateway routes; frontend HTTP 200; legacy port 8087 stopped |
| Deployment files | Default managed and optional self-managed Blueprints pass official Render JSON schema and custom consistency/security checks; dynamic-port/exact-CORS Gateway profile probe passed locally |
| Docker / cloud | **Not executed**: Docker runtime unavailable; no managed database or cloud resource was created |

The initial test runs exposed and then corrected the old 12% expectation, Auth client loading, staff write permissions, asynchronous invitation assertion, missing shared-cart controls and missing custom-split payload. Final results refer to reruns after repair. The production frontend build retains a large-JavaScript-chunk warning; it is not a build failure. Local Brevo/Twilio workers did not transmit real messages.

Private execution evidence is in ignored `.local/feature-*-tests.log`, `.local/feature-gateway-final-acceptance.log`, targeted group/staff/account logs, `.local/feature-runtime.log`, and `.local/feature-browser/`. The combined run records individual PASS requests, including expected authorization failures and asynchronous polling; these are not additional Java test cases.

## Data preservation and managed MySQL preparation

A consistent backup of all seven local schemas was saved before additive changes under ignored `.local/pre-feature-repairs-backup/`, with a SHA256 manifest. Existing tables, IDs and original customer/product records were preserved. Only wishlist preference columns were added; production Hibernate remains validate-only. No production data or credentials were accessed or modified.

Read-only audit: **33 legacy tables and 33 originally migrated tables**, no original IDs missing from tables with primary keys. `staff_permissions` had zero original source rows and no scalar-ID check. Legacy database aggregate fingerprint remained unchanged. All **11 relationship checks** returned zero orphans; native Order/Payment mismatches and undelivered Product/Order/Payment outboxes were zero. Full inventory covers **89 current tables across seven schemas**; independent snapshots matched counts, fingerprints, columns and physical foreign keys. Snapshot checks were exercised locally, not against a cloud provider.

The default **`render.yaml`** now prepares Eureka, Gateway and six private business services with an external managed MySQL endpoint. `render.managed.yaml` is the equivalent explicit variant. `render.self-hosted.yaml` retains the alternative disk-backed MySQL design. Do not apply both environments together.

Follow [MANAGED_MYSQL_DEPLOYMENT.md](MANAGED_MYSQL_DEPLOYMENT.md) for exact order, schema/user settings, TLS, source/target snapshots, backup/restore validation, provider networking, rollback, costs and approval gates. **Vercel's exact public build variable is `VITE_API_BASE_URL=https://<gateway-host>/api`.** Brevo is already REST over HTTPS, not SMTP sockets; `BREVO_API_KEY`, `BREVO_SENDER_EMAIL` and `BREVO_SENDER_NAME` belong only in backend secret settings. No secrets are supplied in Git.

Production still needs an approved provider/plan, actual Docker image execution, isolated managed restore/TLS/grant tests, staging browser checks against public origins, verified external delivery, and explicit approval before deployment/cutover. Keep the legacy backend, local data and backups until acceptance/retention are complete. Do not overwrite newer financial writes with an older snapshot during rollback.
