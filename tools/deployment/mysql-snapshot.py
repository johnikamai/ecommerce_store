"""Read-only table/column/FK inventory and row fingerprints for backup/restore comparison.
Use admin/auditor credentials in environment; output contains aggregates, never rows.
Source/target comparisons require quiesced writes. CRC aggregates are not tamper proofs.
"""
import argparse
import json
import os
import pathlib
import re
import shutil
import subprocess

SCHEMAS = ['shopease_'+owner for owner in ['auth','product','order','payment','notification','recommendation']]
ROOT = pathlib.Path(__file__).resolve().parents[2]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True)
    parser.add_argument('--compare')
    parser.add_argument('--include-legacy', action='store_true')
    args = parser.parse_args()
    env = os.environ.copy()
    password=env.get('DB_PASSWORD')
    if password is None:
        for line in (ROOT/'.local/application.properties').read_text(encoding='utf-8-sig').splitlines():
            if line.startswith('spring.datasource.password='): password=line.split('=',1)[1]
    env['MYSQL_PWD']=password or ''
    command=[shutil.which('mysql') or r'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
             '-h',env.get('DB_HOST','127.0.0.1'),'-P',env.get('DB_PORT','3306'),'-u',env.get('DB_USERNAME','root'),
             '--batch','--skip-column-names','--default-character-set=utf8mb4']
    if env.get('DB_SSL_MODE'): command += ['--ssl-mode='+env['DB_SSL_MODE']]
    if env.get('DB_SSL_CA'): command += ['--ssl-ca='+env['DB_SSL_CA']]
    def sql(statement):
        result=subprocess.run(command+['-e',"SET SESSION time_zone='+00:00'; "+statement],env=env,capture_output=True,text=True,encoding='utf-8')
        if result.returncode: raise RuntimeError(result.stderr.strip())
        return [line.split('\t') for line in result.stdout.splitlines()]
    schemas=SCHEMAS+(['ecommerce_db'] if args.include_legacy else [])
    report={'server':sql('SELECT VERSION(),@@sql_mode,@@time_zone')[0], 'schemas':{}, 'foreignKeys':{}}
    report['tls']=sql("SHOW SESSION STATUS LIKE 'Ssl_cipher'")
    for schema in schemas:
        tables={}
        for name,engine,collation in sql(f"SELECT TABLE_NAME,ENGINE,TABLE_COLLATION FROM information_schema.TABLES WHERE TABLE_SCHEMA='{schema}' AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME"):
            if not re.fullmatch(r'\w+',name): raise ValueError('Unsafe table identifier')
            columns=sql(f"SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COALESCE(COLUMN_DEFAULT,'<NULL>'),EXTRA FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='{schema}' AND TABLE_NAME='{name}' ORDER BY ORDINAL_POSITION")
            names=','.join('`'+column[0]+'`' for column in columns)
            fingerprint=sql(f'SELECT COUNT(*),COALESCE(SUM(CRC32(CAST(JSON_ARRAY({names}) AS CHAR))),0),COALESCE(BIT_XOR(CRC32(CAST(JSON_ARRAY({names}) AS CHAR))),0) FROM `{schema}`.`{name}`')[0]
            tables[name]={'engine':engine,'collation':collation,'columns':columns,'fingerprint':fingerprint}
        if not tables: raise RuntimeError('No visible tables in '+schema+'; check schema grants and restore')
        report['schemas'][schema]=tables
        report['foreignKeys'][schema]=sql(f"SELECT TABLE_NAME,COLUMN_NAME,REFERENCED_TABLE_SCHEMA,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA='{schema}' AND REFERENCED_TABLE_NAME IS NOT NULL ORDER BY TABLE_NAME,CONSTRAINT_NAME,ORDINAL_POSITION")
    pathlib.Path(args.output).write_text(json.dumps(report,indent=2),encoding='utf-8')
    mismatches=[]
    if args.compare:
        before=json.loads(pathlib.Path(args.compare).read_text(encoding='utf-8-sig'))
        for schema in schemas:
            if before['schemas'].get(schema)!=report['schemas'][schema]: mismatches.append(schema+' tables/data')
            if before['foreignKeys'].get(schema)!=report['foreignKeys'][schema]: mismatches.append(schema+' foreign keys')
    print('Read-only snapshot: '+str(sum(len(t) for t in report['schemas'].values()))+' tables; '+str(len(schemas))+' schemas.')
    if mismatches: raise SystemExit('Snapshot differences: '+', '.join(mismatches))
    if args.compare: print('PASS all selected table counts, fingerprints, columns and FK definitions match.')

if __name__=='__main__': main()
