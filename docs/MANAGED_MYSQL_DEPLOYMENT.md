# Managed MySQL preparation — review only

Use the default **`render.yaml`** (also supplied as **`render.managed.yaml`**) for Eureka, Gateway and the six services with an **existing, separately approved managed MySQL endpoint**. This Blueprint does not provision a database. The alternative `render.self-hosted.yaml` runs MySQL on a Render persistent disk; that is self-managed MySQL and is not a managed database offering. Do not apply both Blueprints to the same application.

No cloud account, database, deployment, production credential or production migration was changed. The local Windows MySQL server remains at port 3306. A consistent local backup of the original and six owned schemas was saved, with a SHA256 manifest, under ignored `.local/pre-feature-repairs-backup/` before the repairs. That local export contains private customer data: encrypt and copy it off-host before a cutover; never add it to Git.

## Existing ownership and dependencies

Each service's `src/main/resources/application.yml` uses its own `AUTH_DB_URL`, `PRODUCT_DB_URL`, `ORDER_DB_URL`, `PAYMENT_DB_URL`, `NOTIFICATION_DB_URL` or `RECOMMENDATION_DB_URL` for local development. Their localhost defaults are overridden in the Docker images by `deploy/config/application-render.yml`. Production builds use Java 21 and Connector/J from Spring Boot dependency management. Hibernate runs **validate**, never create/update/drop, in the production profile.

| Schema | Owner and principal data |
|---|---|
| `ecommerce_db` | Preserved legacy source; not a production service datasource |
| `shopease_auth` | Users, customers, staff profiles/permissions/tasks/notifications/audit, authentication and customer order ledger |
| `shopease_product` | Products, categories, reviews, price history, stock/restock subscriptions, inventory reservations and Product outbox |
| `shopease_order` | Orders/items/addresses/wishlists, group carts/members/invitations/splits/activity, tracking, returns and Order operations/outbox |
| `shopease_payment` | Payment ledger, group contribution records, coupons, command receipts/outbox |
| `shopease_notification` | In-app notifications, event inbox, watcher projection and delivery queues |
| `shopease_recommendation` | Persistent recommendation/query history and recommendation ownership metadata |

Authoritative original table mapping is `tools/migrations/migrate_databases.py` (`OWNERS`). Original ID-copy/migration history is in each `_migration_ledger`; subsequent native writes mean current counts can exceed legacy source counts. `tools/deployment/mysql-snapshot.py` inventories **all current tables**, columns, engines, collations and physical FK definitions, including newer service tables. It saves only counts/checksum aggregates, never customer rows. The migration audit verifies original IDs; `tools/migrations/verify_service_references.py` checks eleven logical cross-service relationships. These offline scripts need an auditor/admin account with read access to all selected schemas. Runtime users must only access their owning schema.

Existing preparation scripts are in `tools/migrations/`. The new `add_wishlist_preferences.py` is an idempotent, **additive** migration: six columns in `shopease_order.wishlist_items` and three in `shopease_notification.product_watchers`. It preserves existing tables/IDs/records, defaults existing opt-ins to their previous enabled behaviour, and does not invent target prices. It was applied only to the backed-up local development database. New destinations restored from the latest full backup already contain these columns; the script then makes no changes. Never run the original legacy-to-owner copy over a destination containing newer native writes.

Cross-service references remain IDs and service HTTP calls. There are no production joins against another owner's schema, and no service reads legacy `ecommerce_db`. The offline audit joins are intentional verification tools. Discovery uses Eureka private hostnames; business traffic enters the public Gateway only.

## Required provider capabilities

Select a genuine MySQL 8 compatible provider with six schemas on the same logical instance, InnoDB, the recorded collations/SQL mode, sufficient connections for six pools of five connections plus administrative headroom, backups/PITR and a tested restore procedure. Validate any MySQL-compatible proxy or serverless variant separately; it is not automatically compatible just because it accepts a JDBC URL. A provider that allows only one schema or forbids required DDL/grants needs a revised setup before deployment.

An external provider is **not automatically reachable over Render's private network**. Keep it in a nearby region, require TLS, and allowlist the actual Render outbound ranges from the service dashboard. Dedicated outbound addresses may cost extra. Do not enable `0.0.0.0/0` as a shortcut. If private-only provider networking is mandatory, establish an approved compatible connection first. [Render networking](https://render.com/docs/private-network), [outbound addresses](https://render.com/docs/outbound-ip-addresses).

`DB_SSL_MODE=VERIFY_IDENTITY` validates encryption, certificate trust and hostname. Use the provider hostname, not its numeric IP. If its CA is not trusted by the Java image, mount the CA in a Java truststore as a secret file and configure `JAVA_TOOL_OPTIONS` with the truststore path/password plus the existing JVM memory options. Supply the MySQL CLI's CA path through `DB_SSL_CA` for snapshots. Never disable certificate checks to get a deployment working. [Connector/J TLS](https://dev.mysql.com/doc/connector-j/en/connector-j-reference-using-ssl.html).

## Environment values

| Scope | Required settings |
|---|---|
| Every business service | `DB_HOST` = provider hostname; `DB_PORT` = provider port; `DB_NAME` = own `shopease_*` schema; `DB_USERNAME` = own provider-issued user; `DB_PASSWORD` = own secret; `DB_SSL_MODE=VERIFY_IDENTITY` |
| Java services | `SPRING_PROFILES_ACTIVE=render`, `PORT=10000`, `SERVICE_HOST` from its Render private host, `DISCOVERY_HOST` from Eureka's private host, `DISCOVERY_PORT=10000` |
| Business service authentication | Same strong `SERVICE_INTERNAL_KEY` for authorized service-to-service calls; do not expose it in Vercel |
| Auth | `JWT_SECRET` of at least 32 bytes, `CORS_ALLOWED_ORIGINS` exact HTTPS origins, Brevo values below |
| Gateway | Same exact HTTPS CORS origins; public `/actuator/health`; private service discovery; initial maintenance mode enabled |
| Auth and Notification | `BREVO_API_KEY`, verified `BREVO_SENDER_EMAIL`, `BREVO_SENDER_NAME`; backend only |
| Notification | `NOTIFICATION_DELIVERY_ENABLED=false` until explicit delivery approval and controlled-recipient testing |
| Payment behaviour | `DEMO_PAYMENTS=false` for production; no real CARD/UPI payment provider has been certified |
| Vercel | Frontend root `frontend`; `VITE_API_BASE_URL=https://<actual-gateway-host>/api`; build `npm run build`, output `dist` |

The effective JDBC format is `jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?sslMode=${DB_SSL_MODE}&useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC`. Set secrets using cloud dashboard secret fields, never source, Docker build arguments or client variables. Provider usernames can differ from schema names. Keep six independent grants; administrative restore credentials are temporary and must not be service credentials. Render's `sync:false` entries request values during approved setup, without storing values in Git. [Blueprint environment configuration](https://render.com/docs/blueprint-spec).

Notification and Auth already use **Brevo REST over HTTPS**, posting to `https://api.brevo.com/v3/smtp/email` with an `api-key` header. Despite the URL segment `smtp`, this code does not use SMTP sockets or Spring Mail. SMTP settings alone will not configure it. Existing durable notification queues and in-app notifications remain; do not replay a backlog to real recipients without review. External Brevo/Twilio delivery was not exercised by local demo tests.

## Backup, restore and exact deployment order

1. Obtain approval for the provider/region/cost, database creation, network access and maintenance window. Provision no cloud resources during preparation.
2. Quiesce writers and workers for an agreed final snapshot. Export **all seven** schemas together with `tools/deployment/backup-mysql.ps1` using `DB_PASSWORD` in the administrator environment. Validate its SHA256 manifest, encrypt it and retain an off-host copy. Take provider-native backups after import too. The legacy-only export is insufficient after native service writes.
3. Run `python tools/deployment/mysql-snapshot.py --include-legacy --output <private-source-snapshot.json>`, `audit_migration_state.py` and `verify_service_references.py` while writes remain quiesced. Keep table/schema inventory and baseline fingerprints privately.
4. In a newly approved **empty** managed destination, create the six owned schemas and separate accounts with provider-supported admin commands. Restore only the named application schemas from the verified dump using a MySQL 8 client with `--ssl-mode=VERIFY_IDENTITY` and provider CA if required. Do not restore the system `mysql` schema or overwrite an existing destination. The backup intentionally includes the legacy source for recovery; no service should use it.
5. Review/apply only required additive migrations, using `add_wishlist_preferences.py` in plan mode first. Do not reset tables or reseed the catalogue. Run the same snapshot against the destination with `--compare <private-source-snapshot.json>` plus relationship audits. Schema grants must permit the auditor's offline checks. Resolve every difference before opening traffic.
6. After approval, deploy **Eureka** from `render.managed.yaml`, then **Auth**, then **Product and Payment**, then **Order and Notification**, then **Recommendation**, then **Gateway in maintenance mode**. Auth and Notification can exchange durable facts once both are healthy; no customer traffic is allowed while dependencies are starting. Verify all six registrations are UP and inspect each private service's actuator health from within the network. Private Render services have TCP platform checks; only the public Gateway has an HTTP platform health check.
7. Test provider TLS cipher/hostname verification, each per-schema grant, no legacy/localhost dependency, discovery after restart, all Gateway suites and controlled data checks on an approved isolated staging dataset. Docker image execution and real provider compatibility must still be verified in that environment; this workstation has no Docker runtime.
8. Deploy Vercel only after approval with the exact Gateway URL. Set the exact Vercel/custom origins in CORS and rebuild the frontend when the API URL changes. Test customer/staff/admin browser journeys, then explicitly approve lifting Gateway maintenance mode and enabling external delivery. Keep automatic backend deployment disabled until the rollout policy is agreed.
9. Retain the local server, original data, old backend and verified backups until acceptance and rollback retention expire. After new writes occur, rollback requires reconciliation; restoring an older snapshot over current financial records is unsafe. No destructive rollback script is supplied.

For a cross-host restore, set `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD`, `DB_SSL_MODE` and optional `DB_SSL_CA` in the **auditor session** before running snapshots/reference audits. Confirm the target host before executing any restore or additive migration. The scripts were exercised locally, not against a cloud server.

## Costs and limits

The managed Blueprint contains eight paid Render compute services at the prepared `1c-2g` tier. Using the previously checked $25/service/month price, compute is about **$200/month**, plus the managed provider, backups/PITR, network transfer, any dedicated outbound IPs, workspace charges and taxes. Provider cost remains unknown until a provider/plan is selected. The alternative self-managed Blueprint estimated $227.50/month including a 10GB disk; its MySQL operational responsibility is yours. Confirm live pricing before approval. See the existing `RENDER_VERCEL_DEPLOYMENT.md` for Render/Vercel/Brevo plan constraints and source links. No payment or deployment was made.
