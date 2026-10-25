# ShopEase microservices

The existing React store runs through Spring Cloud Gateway and six Eureka-discovered business services. The compatibility backend is not used or started.

## Run locally on Windows

Prerequisites: Java 21 or newer (verified with Java 25), Node 22.12+, Python, and the existing MySQL 8 server on port 3306. Maven is supplied by `backend/mvnw.cmd`.

Keep local database credentials in ignored `.local/application.properties` as `spring.datasource.password=...`, or set `DB_PASSWORD` and `DB_USERNAME` environment variables. Never commit credentials. The existing local file already contains the signing and internal service keys. When preparing a new checkout, set `SERVICE_INTERNAL_KEY` to a random secret of at least 32 bytes; the launcher generates and persists a signing key when absent.

From this repository in PowerShell:

```powershell
foreach ($service in 'auth','product','order','payment','notification','recommendation') {
    python tools/migrations/migrate_databases.py --service $service
    if ($LASTEXITCODE -ne 0) { throw "Migration failed: $service" }
}
.\start-services.ps1 -Build
node run_integration_tests.js
python tools/migrations/verify_service_references.py
```

On the already migrated machine, start with `./start-services.ps1`; no repeat migration is needed. Open [ShopEase](http://localhost:5173), [Gateway health](http://localhost:8080/actuator/health), and [Eureka](http://localhost:8761). The launcher leaves existing listeners running and verifies readiness; restart an affected service after rebuilding its jar. Runtime logs and test fixtures stay in ignored `.local/`.

## Ownership

| Service | Port | Owned schema and responsibilities |
|---|---:|---|
| Auth | 8081 | `shopease_auth`: identity, OTP, profiles, staff, permissions, loyalty/referral projections |
| Product | 8082 | `shopease_product`: catalog, search, categories, inventory, reviews, price history/approvals |
| Order | 8083 | `shopease_order`: checkout, orders/tracking, addresses, wishlist, returns, groups/splits |
| Payment | 8084 | `shopease_payment`: payment ledger, coupons, refunds, contributions, receipt snapshots |
| Notification | 8085 | `shopease_notification`: inbox, watcher projection, notifications, email/SMS queues |
| Recommendation | 8086 | `shopease_recommendation`: query history; personalized/co-purchase recommendations, assistant, gifts, admin read aggregation |

Gateway runs on 8080; Eureka on 8761. Browser APIs target Gateway. Internal HTTP calls use Eureka service names and a shared service key; internal endpoints are not Gateway routes. External customer/product/order references are scalar IDs and HTTP DTOs, never cross-database JPA entities. Durable checkout intents and idempotent inventory/payment operations protect retries; transactional outboxes deliver one-way events with replay protection.

## Validation and limitations

**Latest verification found three remaining regressions:** account/profile deletion cleanup, unpaid COD return consistency, and `GET /api/health`. The expanded acceptance runner exits 1. See the [current verification report](docs/VERIFICATION_REPORT.md) for exact passed and failed tests. Full migration acceptance is not complete.

All six Gateway suites plus group settlement and wishlist alert flows passed with **port 8087 stopped**. Java tests passed: Auth 23, Product 37, Order 58, Payment 5, Notification 2, Recommendation 53, shared security 4. Frontend tests: 21 passed; production build passed. See [migration evidence](docs/FULL_MIGRATION_PROGRESS.md).

Payments are intentionally demo flows: no real card credentials are collected or stored. Existing Host Pays group settlement is preserved; individual gateway charges remain outside the original feature set. Real email/SMS needs Brevo/Twilio configuration; absent providers are recorded as `DEMO_SKIPPED`, while in-app notifications work normally. External provider delivery was not tested. The legacy Docker Compose file is not the validated startup path; the commands above use the existing local MySQL data and verified Windows launcher.
