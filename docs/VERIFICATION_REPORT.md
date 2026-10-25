# ShopEase verification — 25 October 2026

**Result: verification completed; full migration acceptance fails.** All six services build, pass their existing Java tests, register UP in Eureka, and serve the main Gateway journeys with port 8087 stopped. Three additional regression suites fail. The legacy backend source and existing customer data have been preserved.

This report supersedes earlier statements that the migration had no known remaining failures. The acceptance runner now includes the newly discovered regressions and exits **1** until they are corrected.

## Clean builds and automated tests

Every module was rebuilt using `backend/mvnw.cmd -f <module>/pom.xml clean install`, with tests enabled. The newly built services were restarted before HTTP verification.

| Module | Build | Tests | Failures | Errors | Skipped |
|---|---|---:|---:|---:|---:|
| Auth | PASS | 23 | 0 | 0 | 0 |
| Product | PASS | 37 | 0 | 0 | 0 |
| Order | PASS | 58 | 0 | 0 | 0 |
| Payment | PASS | 5 | 0 | 0 | 0 |
| Notification | PASS | 2 | 0 | 0 | 0 |
| Recommendation | PASS | 53 | 0 | 0 | 0 |
| Shared service security | PASS | 4 | 0 | 0 | 0 |
| Eureka | PASS | 0 | 0 | 0 | 0 |
| API Gateway | PASS | 0 | 0 | 0 | 0 |
| **Java total** | **PASS** | **182** | **0** | **0** | **0** |

Eureka and Gateway currently have no Java test cases; their builds and live HTTP checks passed. Frontend: **21 tests passed**, zero failed; production build passed.

## Live services, discovery and routing

Auth 8081, Product 8082, Order 8083, Payment 8084, Notification 8085 and Recommendation 8086 all returned HTTP 200 with actuator status `UP`, and each was registered `UP` in Eureka. Each rejected internal requests lacking the service key with HTTP 401. Gateway rejected an unrouted internal endpoint with HTTP 404. Frontend returned HTTP 200 on 5173.

Port **8087 had no listener** during all fresh HTTP tests. Gateway configuration contains no `8087`, `COMPATIBILITY_URI` or monolith fallback. Tested business responses carry the expected owner marker:

| Requests | Native Gateway owner |
|---|---|
| Registration, OTP, login, profiles, staff lifecycle and user mutations | Auth |
| Catalog, categories, search, reviews, inventory and price approvals | Product |
| Orders, checkout, tracking, cancellation, addresses, wishlist, returns, groups | Order |
| Payment reads/history/receipts and coupons | Payment |
| Payment creation/status commands | Order coordinator, calling the Payment owner |
| In-app notifications/read state | Notification |
| Recommendations, assistant, gift finder, admin reports/user-list aggregation | Recommendation |

The payment command split is intentional: Order coordinates updates while Payment persists the financial ledger. Admin reads aggregate data through owner APIs. Internal service APIs are not Gateway routes.

A static comparison found 144 original `/api/` method/path mappings and one missing native mapping: `GET /api/health`. A matching mapping alone does not prove endpoint behaviour; the HTTP suites below establish the tested behaviours.

## Gateway integration results

The original six service suites plus group-order and wishlist-alert suites passed: **8 suites, 142 successful checks, zero failures**. Coverage includes:

- Registration, OTP verification, login, profile reads/updates and customer ownership.
- Password reset and immediate revocation of old tokens in Auth and downstream Product.
- Admin/customer/staff authorization, staff application, approval, permissions and suspension; revoked staff token rejected downstream.
- Existing catalog/products/images, typo-tolerant search, categories, reviews and summaries, product Q&A, admin catalog mutations, inventory reserve/release retry and price approvals.
- Quote and idempotent CARD checkout, one stock deduction, payment/receipt/history, tracking, repeated cancellation, refund and one stock restoration.
- Address creation/read/removal and wishlist add/read/removal.
- Two-account group creation/joining, shared cart, exact-cent split, confirmations, lock, combined checkout, member payment report, host receipt confirmation, and zero remaining contribution amount.
- COD payment creation, admin payment confirmation, packing/shipping/out-for-delivery/delivery, paid return approval/refund, receipts and payment history.
- Notification ownership, unread/read-all, duplicate-event replay protection, actual cross-service wishlist price-drop and restock alerts.
- Frequently bought together, personalized recommendations, taste profile, shopping assistant, gift finder, dashboards and sales/product/customer reports.

With three added regression suites, the expanded runner completed **11 suites: 8 passed, 3 failed**. It logged 160 successful intermediate checks and exited **1**. Successful intermediate requests inside a failed suite do not count as that feature passing.

### Failed: account-deletion-parity

Test: `tools/tests/account_deletion_gateway.cjs`.

A disposable CUSTOMER account and address were created. `DELETE /api/admin/users/66` returned 204 in the initial reproduction, but the deleted account's customer profile still returned 200 rather than the expected 404. The expanded run reproduced this for disposable customer 75, with one saved address also still present. Original behaviour removed the profile and dependent non-financial data when deletion was allowed.

Cause: `services/auth-service/src/main/java/com/ecommerce/auth/controller/AdminController.java:83` deletes only the User. Cleanup of the Customer and other owners' dependent data is not implemented. Cross-service deletion must preserve financial history, prevent partial destructive cleanup and be retryable before this feature can be considered migrated completely.

### Failed: unpaid-refund-consistency

Test: `tools/tests/unpaid_refund_gateway.cjs`.

A synthetic CASH order remained PENDING, was delivered, then returned/approved/refunded. All operations returned successful HTTP responses. Order reported payment status **REFUNDED**, while the Payment ledger remained **PENDING**. The expanded reproduction used synthetic order 60/payment 44.

Cause: `services/order-service/src/main/java/com/ecommerce/order/service/ReturnService.java:135` sets the Order's payment status to REFUNDED unconditionally, but line 139 only updates Payment when the previous status was PAID. Uncollected COD must have a consistent, explicitly defined cancellation/refund outcome across both owners. The regression reconciles only its synthetic payment through native service commands after saving the failing observation; original financial records were not edited.

### Failed: gateway-health-contract

Test: `tools/tests/gateway_routes.cjs`.

`GET http://localhost:8080/api/health` returned **404**, expected 200. Gateway routes it to Auth, but Auth's `HealthController.java:10` maps only `/health`. Actuator health works for all services; this failure concerns the original public API contract.

## Database preservation and ownership

- All six configurations target their respective `shopease_auth`, `shopease_product`, `shopease_order`, `shopease_payment`, `shopease_notification` and `shopease_recommendation` schemas. The optional old Product compatibility profile still exists, but was not activated by the verified launcher.
- Read-only audits inspected **33 original tables and 33 migrated tables**. All migration ledger entries were present and **zero original IDs were missing** from their owned targets.
- Aggregate fingerprints of the complete original `ecommerce_db` before and after this verification matched: **source data unchanged during verification**.
- All **11 relationship audits passed**, with zero orphan references across orders, items, products, customers, reviews, payments, notifications, group members/contributions and tracking.
- Before the extra refund regression there were zero native order/payment mismatches. The regression captured the discrepancy described above, then reconciled its synthetic fixture. Final audit again showed **zero native mismatches** and **zero undelivered Product/Order/Payment outbox events**.
- Recommendation query history is persisted in its owned schema. Notification owns its inbox, watcher projection and email/SMS delivery queues.
- Runtime references to external customers/products/orders are IDs and HTTP DTOs; cross-schema joins in the audit scripts are offline verification only. The original migration copied IDs and recorded source counts; current target counts legitimately include synthetic post-cutover records. This audit does not claim exact current source/target equality after native writes.

The legacy `backend/` source remains intact. No legacy backend files were removed. No real customer account was deleted by these tests; deletion regressions used accounts created specifically by verification. Synthetic financial history is retained for audit.

## Remaining incomplete or unverified features

**Incomplete:** admin account/profile cleanup, unpaid COD return consistency, and the `/api/health` compatibility contract, as demonstrated above. Full migration acceptance therefore remains incomplete despite the passing core journeys.

**Configuration-dependent/unverified:** real Brevo email delivery, real Twilio SMS delivery, and Docker deployment. In-app notification delivery and durable queue behaviour were tested; external provider delivery was not. CARD/UPI remain explicit demo flows and do not transfer money. Group Host Pays settlement works; individual gateway charging was not part of the existing enabled feature set. No production deployment or sustained-load/failure-injection certification is implied by this local verification.

## Reproduce

```powershell
# From the repository root; existing MySQL and ignored local configuration required.
.\start-services.ps1 -Build
node run_integration_tests.js
# Expected current exit: 1; all three regression failures are listed at the end.

python tools/migrations/verify_service_references.py
python tools/migrations/audit_migration_state.py --output .local/verification/database-audit.json
```

The verification used clean Maven builds with tests enabled; the launcher's `-Build` option skips tests, so it is not itself proof that automated tests pass. Run `backend/mvnw.cmd -f <module>/pom.xml clean install` to repeat a module's full build/test step. Build `services/service-common` first.

Evidence stays in ignored `.local/verification/`: build logs and JSON results, `gateway-integration.log`, `expanded-integration.log`, individual regression logs, aggregate-only database snapshots and runtime discovery/health results. No credentials, tokens or customer rows are included in this tracked report.
