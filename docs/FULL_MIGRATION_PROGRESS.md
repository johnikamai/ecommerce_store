# Full migration acceptance — 25 October 2026

The six business services are running, registered in Eureka, and reachable through Gateway. **Compatibility backend port 8087 is stopped and is not a Gateway fallback or launcher dependency.** Original `ecommerce_db` was preserved. Earlier phase notes below are historical, not the current startup instructions.

## Verified results

- Full `node run_integration_tests.js` run exited 0: 140 individual Gateway/API checks across Auth, Product, Order, Payment, Notification, Recommendation, and two-account group settlement. Additional cross-service wishlist price-drop and restock alert checks passed and are now included in the runner.
- Java: Auth 23, Product 37, Order 58, Payment 5, Notification 2, Recommendation 53, common security 4 — 182 tests passed. Recommendation was rebuilt/retested after reducing repeated order reads in admin reports; its HTTP suite passed again.
- Frontend: 21 tests passed; production build passed. Browser checks confirmed original product images, fuzzy search with corrected results, cart totals, delivery address input, and COD/UPI/Card demo options with 8087 stopped.
- Customer journey: register/OTP/login, profile ownership, password reset and immediate token revocation, product/search/reviews, wishlist/addresses, quote, idempotent checkout, payment/receipt, tracking, cancellation with one stock restoration and refund, complete fulfilment, returns and refund.
- Group journey: two separate accounts, create/join, shared items, exact-cent split, both confirmations, lock, combined CARD checkout, member reports payment, host confirms receipt, remaining amount zero. `CONFIRMED_RECEIVED` retains the original settlement contract.
- Admin/staff: customer forbidden access, admin catalog mutations, price approval conflict/retry protection, staff application/approval/suspension, dashboard/users/customer detail, sales/top-product/customer-activity reports, payments and refunds.
- Notification delivery: original inbox APIs and read counts, customer ownership, replay creates one notification/delivery, late watcher events ignored, actual Order wishlist event followed by Product price/stock event reaches Notification inbox. Email/SMS queues persist independently of business transactions.
- Offline reference audit: 11 checks, zero orphan references (order/customer, items/order/product, reviews/customer/product, payment/order, notification/customer, group members/group/customer, contributions/member, tracking/order).

Local evidence is retained in ignored `.local/full-six-service-acceptance.log`, `alerts-final-http.log`, `recommendation-final-http.log`, `final-reference-audit.log`, each module's build log, and browser screenshots. Tokens, passwords and personal test fixtures are never committed.

## Migration baseline

Every copied table had matching source/target counts and normalized checksums before native writes. IDs, password hashes, images and stored financial data were retained. Post-cutover synthetic API tests add new rows in target schemas; the importer deliberately preserves these changes rather than resetting targets.

| Owner | Existing data copied at cutover |
|---|---|
| Auth | 46 customers, 42 users, 22 OTP challenges; remaining owned staff/email-change tables empty |
| Product | 1,222 products, 15 categories, 49 reviews, 1 admin alert, 1 inventory row; existing empty price/restock tables; price approvals moved to Product |
| Order | 40 orders, 35 items, 19 tracking events, 2 addresses, 4 wishlist entries, 1 return, 13 groups, 23 members, 16 group cart items, 13 invitations, 74 activities, 8 bill splits |
| Payment | 21 payments, 1 coupon, 8 group contributions; immutable receipt/member snapshots initialized additively |
| Notification | 104 existing notifications; watcher projection initialized from owned Order wishlist data once |
| Recommendation | No original table to copy; owns persistent query history in its schema |

Source backups are under ignored `.local/backups/`. Six explicit additive SQL extension files and the repeatable Python importer are under `tools/migrations/`. Normal reruns skip imported tables; `--verify` is for exact equality at initial cutover, not after live target writes. Offline cross-schema auditing is isolated from runtime service code.

## Main changes

- `services/*`: actual controllers, business services, repositories, security, owned entities or HTTP DTOs, migrations and tests across all six services.
- `services/service-common`: internal-key enforcement, Eureka HTTP client, Auth introspection, dependency health and transactional outbox.
- `api-gateway/src/main/resources/application.yml`: explicit six-service routes; protected internal endpoints never exposed; no compatibility fallback.
- `frontend/src/pages/Cart.jsx`: stable idempotency key per unchanged checkout payload; original UI retained.
- `tools/tests/*`, `run_integration_tests.js`: real native service acceptance, group settlement and cross-service alert checks.
- `start-services.ps1`: builds common library first, launches all six services, verifies readiness, keeps secrets/logs ignored, never starts compatibility.

Checkout uses durable intents, idempotent inventory reservations, coupon consumption and Payment commands, with scheduled recovery. Group checkout suspends its enclosing transaction while coordinating checkout so MySQL repeatable-read snapshots do not hide newly committed recovery records. Order/Payment/refund state and stock retry behavior were verified through real HTTP. Order emits customer-order facts to Auth for loyalty/referral calculations; Auth never queries Order tables. Product emits durable stock/price events. Notification owns its watcher projection and never calls back into Product or Order. Recommendation reads owners through Eureka and stores its own query records.

## Startup and known configuration requirements

```powershell
# Already migrated local machine, from repository root:
.\start-services.ps1
# Rebuild artifacts when needed:
.\start-services.ps1 -Build
# All business journeys and offline reference audit:
node run_integration_tests.js
python tools/migrations/verify_service_references.py
```

Frontend: http://localhost:5173; Gateway: http://localhost:8080; Eureka: http://localhost:8761. New-machine migration and environment requirements are in `README.md`.

No known failure remains in the tested local acceptance journeys. Online payments remain the existing explicit demo; no real card information is stored. Brevo/Twilio deliveries need provider credentials; without them jobs are recorded `DEMO_SKIPPED`. External provider delivery and Docker deployment were not validated. Synthetic financial verification records are intentionally retained in isolated target schemas for audit; original source rows were not removed. Main was not merged.

---

## Historical phase notes



## Phase 1 — Auth Service (verified 25 October 2026)

Auth Service on 8081 owns `shopease_auth`: users, customers/profiles/referrals, OTP challenges, email-change challenges, staff profiles/permissions/tasks/notifications/audit and staff approval requests. Original business logic was ported; the monolith remains intact. Product references in approvals are HTTP DTOs, never auth JPA entities. Product price execution is completed and tested in the Product phase before that workflow is switched fully.

Gateway routes `/api/auth/**`, `/api/customers/**`, `/api/staff/**`, `/api/admin/staff/**`, and `/api/admin/users/**` to Eureka `auth-service` and marks responses with `X-ShopEase-Backend: auth-service`. Other business routes remain in migration; this is not full six-service acceptance.

### Evidence

- Auth Service: 21 tests passed, including original OTP/JWT/staff regressions plus actual HTTP security-context tests for registration, ownership, password reset and staff lifecycle.
- Shared service security: 4 tests passed for authority propagation, invalid tokens, fail-closed identity outages, and internal credential enforcement.
- Product Service: context/security startup test passed; full package built.
- Real Gateway requests: register/OTP verify/login/profile reads and edits 200; anonymous profile access 401; another customer's profile 403; customer admin/staff access 403; test-admin user list 200; staff application 201; pending login 403; approval 200; staff permissions present; suspension 200 and old staff token 401.
- Actual downstream Product Service: valid JWT authenticated `/actuator/info` 200; customer product write 403; anonymous write 401; suspended or password-reset-revoked token write 401. These downstream checks call 8082 directly during Phase 1 because product writes are switched through Gateway in Phase 2.
- Auth data copy before cutover: 46 customers, 42 users, 22 OTP challenges; seven remaining owned tables empty. Counts and normalized data checksums matched; existing IDs and password hashes were preserved; original source database unchanged. All user/customer and staff/user references had zero orphans. Additional checks cover all auth-owned references.
- Source backup retained in ignored `.local/backups/`. Database password, signing key, internal service key and synthetic fixture credentials remain in ignored `.local/` only.

### Commands

```powershell
python tools/migrations/migrate_databases.py --service auth
# --verify checks exact source equality before service writes; after cutover
# live target changes are expected and are never overwritten by rerunning copy.
.\backend\mvnw.cmd -f services/service-common/pom.xml install
.\backend\mvnw.cmd -f services/auth-service/pom.xml package
.\backend\mvnw.cmd -f services/product-service/pom.xml package
.\start-services.ps1
node run_integration_tests.js auth
```

The importer copies explicit named columns into empty or ledger-tracked targets, never drops/replaces source data, skips previously imported tables, and refuses untracked nonempty tables. Canonical checksums normalize numeric storage because the pre-existing scaffold represented booleans as BIT whereas the legacy schema uses TINYINT. It does not print passwords, hashes or personal rows.

Auth validates token signatures, expiry, credential fingerprints, current account enabled state and current staff permissions. Protected services query the key-protected internal identity endpoint through Eureka, so suspension and password reset take effect immediately. Identity outages return 503 before executing protected business logic. Internal service paths are not Gateway-routed.

### Remaining phase work

Full product catalog/search/CRUD/inventory/reviews, order/payment/notification/recommendation extraction, admin aggregate response enrichment, cross-service account deletion, price-approval execution, six-schema migration and final compatibility-off acceptance remain to be verified. `node run_integration_tests.js` intentionally fails full acceptance until all six real-service suites are present. The original hybrid smoke test is retained in `tools/tests/hybrid_smoke.cjs` for reference only.

## Phase 2 — Product Service (verified)

Full product ownership is now in `shopease_product`. Gateway routes product/category/catalog/search/review/restock/admin-catalog/admin-review/admin-stock-alert APIs and original product images to product-service. Product recommendation endpoints remain on their current owner until Phase 6.

- Exact migration equality verified: 1,222 products, 15 categories, 49 reviews, existing admin alert and legacy inventory row; original `ecommerce_db` unchanged. Existing IDs and images retained.
- 37 Product tests passed: search/review regressions, context startup, atomic multi-product rollback, stale-price rejection, two concurrent buyers for the last unit, idempotent reservation/commit/release, and price approval conflicts/retries. Auth's 21 tests, common security's 4 tests, and frontend's 21 tests passed again.
- Real Gateway suite passed: full catalog, typo-tolerant search, existing reviews/AI summaries, admin category/product CRUD, category rename propagation, customer review purchase gating, restock subscribe/read/remove, product Q&A, price history, moderation, stock/catalog status, original image HTTP 200, and price approvals (201 -> approval 200 -> repeat decision 409).
- Internal inventory HTTP tests verified reserve retry deducts stock once and release retry restores it once. Synthetic products/categories/subscriptions were removed; original catalog rows were not edited.
- Staff price approvals moved from Auth to Product, removing the Auth-to-Product dependency. Auth staff tasks/lifecycle remain Auth-owned. Approval logs/notifications use idempotent events delivered to Auth's inbox.
- Transactional outbox stores stock/price events until Notification Service is available. Sales and purchase eligibility are local read models seeded from actual source orders, not queries against another service schema. Order phase updates these through reservation commit/release.
- Readiness now checks Auth discovery before Product is announced ready. Temporary missing identity instances produce 503 before executing business code.

Commands: `node run_integration_tests.js auth` and `node run_integration_tests.js product`. Full acceptance still deliberately fails until all six suites exist. Order, Payment, Notification and Recommendation migration remain in progress.
