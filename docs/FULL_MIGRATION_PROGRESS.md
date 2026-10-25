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
