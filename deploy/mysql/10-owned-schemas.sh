#!/bin/bash
# Runs only during official MySQL initialization of a NEW, EMPTY data directory.
# Never alters existing database credentials or restores customer data.
set -euo pipefail
export MYSQL_PWD="${MYSQL_ROOT_PASSWORD:?Root password required}"
for owner in AUTH PRODUCT ORDER PAYMENT NOTIFICATION RECOMMENDATION; do
  name="${owner,,}"
  variable="${owner}_DB_PASSWORD"
  password="${!variable:?Owned database password required}"
  # Render-generated base64 passwords avoid SQL-mode-dependent escaping.
  [[ "$password" =~ ^[A-Za-z0-9+/=]{32,}$ ]] || { echo "Expected generated base64 password for $owner" >&2; exit 2; }
  mysql --protocol=socket --user=root <<SQL
CREATE DATABASE IF NOT EXISTS shopease_${name} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'shopease_${name}'@'%' IDENTIFIED BY '${password}';
GRANT SELECT, INSERT, UPDATE, DELETE ON shopease_${name}.* TO 'shopease_${name}'@'%';
SQL
done
unset MYSQL_PWD password
