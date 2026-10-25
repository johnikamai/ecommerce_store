# ShopEase Microservices Architecture

## Overview
ShopEase has been converted to a real microservices architecture with independent Spring Boot services, Eureka service discovery, API Gateway, and per-service databases.

## Services
| Service | Port | Purpose | DB Schema |
|---|---|---|---|
| discovery-server (Eureka) | 8761 | Service registry | - |
| api-gateway | 8080 | Single entry point, routes all /api/* to services | - |
| auth-service | 8081 | Authentication, users/customers, roles | shopease_auth |
| product-service | 8082 | Products, categories, inventory | shopease_product |
| order-service | 8083 | Orders, cart, group orders, addresses, returns/restock | shopease_order |
| payment-service | 8084 | Payments, coupons | shopease_payment |
| notification-service | 8085 | Notifications, alerts, emails | shopease_notification |
| recommendation-service | 8086 | Recommendations, wishlist, AI features | shopease_recommendation |

## Communication
- REST via OpenFeign between services where needed
- All external requests go through API Gateway
- Service discovery via Eureka

## Running Locally
### Without Docker
1. Start MySQL and create schemas or let services create
2. Start discovery-server: `cd discovery-server && mvn spring-boot:run`
3. Start api-gateway and each service in order
4. Frontend: point to `http://localhost:8080/api` or update axios config

### With Docker
```bash
docker-compose up --build
```

## Frontend Integration
Update frontend API base URL to API Gateway (http://localhost:8080) to route through microservices.
All existing /api/* paths preserved.

## Notes
- Each service is independently runnable
- Data ownership per service
- Preserves existing features and flows
