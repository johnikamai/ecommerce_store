#!/bin/sh
set -eu
required() { eval "value=\${$1-}"; [ -n "$value" ] || { echo "Required environment variable missing: $1" >&2; exit 2; }; }
private_host() { case "$2" in localhost|localhost:*|127.*|0.0.0.0|::1|*://*|*/../*) echo "Invalid production host variable: $1" >&2; exit 2;; esac; }
case ",${SPRING_PROFILES_ACTIVE-}," in
 *,render,*)
  required SERVICE_HOST; required DISCOVERY_HOST
  private_host SERVICE_HOST "$SERVICE_HOST"; private_host DISCOVERY_HOST "$DISCOVERY_HOST"
  case "${PRODUCT_SERVICE_URI-lb://product-service}" in lb://product-service) ;; *) echo "Production Product route must use Eureka" >&2; exit 2;; esac
  case "${APP_MODULE-}" in
   services/*)
    required DB_HOST; required DB_NAME; required DB_USERNAME; required DB_PASSWORD; required SERVICE_INTERNAL_KEY
    private_host DB_HOST "$DB_HOST"
    [ "${#SERVICE_INTERNAL_KEY}" -ge 32 ] || { echo "Internal key must be at least 32 bytes" >&2; exit 2; }
    case "${DB_SSL_MODE-REQUIRED}" in REQUIRED|VERIFY_CA|VERIFY_IDENTITY) ;; *) echo "Database encryption required in render profile" >&2; exit 2;; esac
    ;;
  esac
  case "${APP_MODULE-}" in
   services/auth-service) required JWT_SECRET; [ "${#JWT_SECRET}" -ge 32 ] || exit 2;;
  esac
  case "${APP_MODULE-}" in
   services/auth-service|services/notification-service) required BREVO_API_KEY; required BREVO_SENDER_EMAIL; required BREVO_SENDER_NAME;;
  esac
  case "${APP_MODULE-}" in
   api-gateway|services/auth-service)
    required CORS_ALLOWED_ORIGINS
    case "$CORS_ALLOWED_ORIGINS" in *localhost*|*127.0.0.1*|*\**) echo "Use exact HTTPS frontend origins in production" >&2; exit 2;; esac
    old_ifs=$IFS; IFS=,
    for origin in $CORS_ALLOWED_ORIGINS; do case "$origin" in https://*) ;; *) echo "HTTPS CORS origin required" >&2; exit 2;; esac; done
    IFS=$old_ifs
    ;;
  esac
  ;;
esac
export SERVER_PORT="${PORT:-10000}"
exec java -jar /app/app.jar "$@"
