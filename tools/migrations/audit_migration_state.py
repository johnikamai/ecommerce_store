"""Read-only migration audit. Save aggregate fingerprints, never customer rows."""
import argparse
import hashlib
import importlib.util
import json
import os
import pathlib
import re
import shutil
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    environment = os.environ.copy()
    password = environment.get('DB_PASSWORD', environment.get('DB_PASS'))
    if password is None:
        for line in (ROOT / '.local/application.properties').read_text(encoding='utf-8-sig').splitlines():
            if line.startswith('spring.datasource.password='):
                password = line.split('=', 1)[1]
    environment['MYSQL_PWD'] = password or ''
    mysql = shutil.which('mysql') or r'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'

    def sql(query):
        result = subprocess.run([
            mysql, '-h', environment.get('DB_HOST', '127.0.0.1'),
            '-P', environment.get('DB_PORT', '3306'),
            '-u', environment.get('DB_USERNAME', environment.get('DB_USER', 'root')),
            '--batch', '--skip-column-names', '-e', query,
        ], env=environment, capture_output=True, text=True, encoding='utf-8')
        if result.returncode:
            raise RuntimeError('Read-only audit query failed: ' + result.stderr.strip())
        return [line.split('\t') for line in result.stdout.splitlines()]

    spec = importlib.util.spec_from_file_location('migration', ROOT / 'tools/migrations/migrate_databases.py')
    migration = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(migration)
    source = {}
    for (table,) in sql("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='ecommerce_db' AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME"):
        if not re.fullmatch(r'\w+', table):
            raise ValueError('Unsupported table identifier')
        columns = sql(f"SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='ecommerce_db' AND TABLE_NAME='{table}' ORDER BY ORDINAL_POSITION")
        names = ','.join('`' + c[0] + '`' for c in columns)
        source[table] = sql(f'SELECT COUNT(*),COALESCE(SUM(CRC32(CAST(JSON_ARRAY({names}) AS CHAR))),0),COALESCE(BIT_XOR(CRC32(CAST(JSON_ARRAY({names}) AS CHAR))),0) FROM ecommerce_db.`{table}`')[0]

    tables = []
    for owner, owned in migration.OWNERS.items():
        for table in owned:
            schema = 'shopease_' + owner
            ledger = sql(f"SELECT source_count FROM {schema}._migration_ledger WHERE table_name='{table}'")
            count = int(sql(f'SELECT COUNT(*) FROM {schema}.`{table}`')[0][0])
            primary = sql(f"SELECT COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA='ecommerce_db' AND TABLE_NAME='{table}' AND CONSTRAINT_NAME='PRIMARY'")
            missing = None
            if primary:
                on = ' AND '.join(f's.`{c[0]}`=t.`{c[0]}`' for c in primary)
                missing = int(sql(f'SELECT COUNT(*) FROM ecommerce_db.`{table}` s LEFT JOIN {schema}.`{table}` t ON {on} WHERE t.`{primary[0][0]}` IS NULL')[0][0])
            tables.append(dict(owner=owner, table=table, sourceCount=int(source[table][0]), migrationCount=int(ledger[0][0]) if ledger else None, currentCount=count, missingOriginalIds=missing))

    mismatches = int(sql("SELECT COUNT(*) FROM shopease_order.orders o JOIN shopease_payment.payments p ON p.order_id=o.id WHERE o.checkout_key IS NOT NULL AND NOT(o.payment_status <=> p.payment_status)")[0][0])
    pending = {owner: int(sql(f'SELECT COUNT(*) FROM shopease_{owner}.service_outbox WHERE delivered=0')[0][0]) for owner in ['product', 'order', 'payment']}
    result = dict(sourceFingerprint=hashlib.sha256(json.dumps(source, sort_keys=True).encode()).hexdigest(), sourceTables=source, migrations=tables, nativePaymentMismatches=mismatches, pendingOutbox=pending, recommendationHistoryCount=int(sql('SELECT COUNT(*) FROM shopease_recommendation.query_history')[0][0]))
    path = pathlib.Path(args.output)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(result, indent=2), encoding='utf-8')
    missing = sum(t['missingOriginalIds'] or 0 for t in tables)
    print(f'Inspected {len(source)} source tables and {len(tables)} migrated tables; aggregate-only report saved.')
    print(f'Missing original IDs: {missing}; native order/payment mismatches: {mismatches}')
    print('Pending outbox events: ' + json.dumps(pending))
    if missing or mismatches or any(t['migrationCount'] is None for t in tables):
        raise SystemExit(1)


if __name__ == '__main__':
    main()
