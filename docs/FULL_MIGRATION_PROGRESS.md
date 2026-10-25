# Full migration progress

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
