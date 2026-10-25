"""Add only wishlist preference columns. Never drops/recreates tables or edits prices.
Run after a verified backup; --apply is explicit. Production use needs approval.
"""
import argparse
import os
import pathlib
import shutil
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[2]
COLUMNS = {
    ('shopease_order', 'wishlist_items'): {
        'target_price': 'DECIMAL(12,2) NULL', 'initial_price': 'DECIMAL(12,2) NULL',
        'notify_on_drop': 'BIT NOT NULL DEFAULT 1', 'notify_on_restock': 'BIT NOT NULL DEFAULT 1',
        'alert_version': 'BIGINT NOT NULL DEFAULT 0', 'row_version': 'BIGINT NOT NULL DEFAULT 0',
    },
    ('shopease_notification', 'product_watchers'): {
        'target_price': 'DECIMAL(38,2) NULL',
        'notify_on_drop': 'BIT NOT NULL DEFAULT 1', 'notify_on_restock': 'BIT NOT NULL DEFAULT 1',
    },
}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    env = os.environ.copy()
    password = env.get('DB_PASSWORD')
    if password is None:
        for line in (ROOT / '.local/application.properties').read_text(encoding='utf-8-sig').splitlines():
            if line.startswith('spring.datasource.password='): password = line.split('=', 1)[1]
    env['MYSQL_PWD'] = password or ''
    client = shutil.which('mysql') or r'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
    command = [client, '-h',env.get('DB_HOST','127.0.0.1'), '-P',env.get('DB_PORT','3306'),
               '-u',env.get('DB_USERNAME','root'), '--batch','--skip-column-names']
    if env.get('DB_SSL_MODE'): command += ['--ssl-mode='+env['DB_SSL_MODE']]
    if env.get('DB_SSL_CA'): command += ['--ssl-ca='+env['DB_SSL_CA']]
    def sql(query):
        result = subprocess.run(command+['-e',query], env=env, capture_output=True, text=True)
        if result.returncode: raise RuntimeError(result.stderr)
        return result.stdout.strip()
    for (schema, table), columns in COLUMNS.items():
        for column, definition in columns.items():
            exists = sql(f"SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='{schema}' AND table_name='{table}' AND column_name='{column}'")
            if exists == '0':
                statement = f'ALTER TABLE `{schema}`.`{table}` ADD COLUMN `{column}` {definition}'
                print(('APPLY ' if args.apply else 'PLAN ') + statement)
                if args.apply: sql(statement)
    print('Additive wishlist migration checked; existing rows and schemas retained.')

if __name__ == '__main__': main()
