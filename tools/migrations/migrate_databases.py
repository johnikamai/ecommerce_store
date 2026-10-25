#!/usr/bin/env python3
"""Copy service-owned tables once, preserving source data and IDs; verify every copy.
Credentials come from DB_PASSWORD/DB_PASS or ignored .local/application.properties.
No DROP, DELETE, REPLACE, TRUNCATE, or UPDATE statements are used.
"""
import argparse, datetime, os, pathlib, re, shutil, subprocess, sys
ROOT=pathlib.Path(__file__).resolve().parents[2]
OWNERS={
 'auth':['customers','users','otp_challenge','email_change','staff_profiles','staff_permissions','staff_tasks','staff_notifications','staff_activity_logs'],
 'product':['products','categories','price_history','reviews','restock_requests','admin_alerts','inventory','approval_requests'],
 'order':['orders','order_items','order_status_events','addresses','wishlist_items','return_requests','group_orders','group_order_members','group_cart_items','group_invitations','group_order_activities','group_bill_splits'],
 'payment':['payments','coupons','group_payments'],
 'notification':['notifications'],
 'recommendation':[]}
def main():
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument('--service',choices=OWNERS,required=True);p.add_argument('--verify',action='store_true');p.add_argument('--extensions-only',action='store_true')
 p.add_argument('--source',default='ecommerce_db');p.add_argument('--no-backup',action='store_true')
 a=p.parse_args()
 if not re.fullmatch(r'[a-zA-Z0-9_]+',a.source): raise ValueError('Unsafe schema name')
 mysql=shutil.which('mysql') or r'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
 environment=os.environ.copy()
 password=environment.get('DB_PASSWORD',environment.get('DB_PASS'))
 if password is None:
  config=ROOT/'.local/application.properties'
  if config.exists():
   for line in config.read_text(encoding='utf-8-sig').splitlines():
    if line.startswith('spring.datasource.password='):password=line.split('=',1)[1]
 if password is not None:environment['MYSQL_PWD']=password
 args=['-h',environment.get('DB_HOST','127.0.0.1'),'-P',environment.get('DB_PORT','3306'),'-u',environment.get('DB_USERNAME',environment.get('DB_USER','root'))]
 def sql(statement):
  result=subprocess.run([mysql,*args,'--batch','--skip-column-names','-e',statement],env=environment,capture_output=True,text=True)
  if result.returncode:raise RuntimeError(result.stderr.strip())
  return [line.split('\t') for line in result.stdout.splitlines() if line]
 schema='shopease_'+a.service
 def origin(table):return 'shopease_auth' if a.service=='product' and table=='approval_requests' else a.source
 existing={row[0] for row in sql(f"SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='{a.source}'")}
 if a.extensions_only:
  extension=ROOT/('tools/migrations/'+('001_auth_extensions.sql' if a.service=='auth' else '002_product_extensions.sql'))
  sql(extension.read_text(encoding='utf-8-sig').replace('__SOURCE__',a.source));print('Service schema extensions applied; existing rows preserved.');return
 newly_copied=set()
 tables=OWNERS[a.service]
 missing=set(tables)-existing
 if missing:raise RuntimeError('Source tables missing; correct ownership manifest first: '+', '.join(sorted(missing)))
 if not a.verify:
  if not a.no_backup:
   backup=ROOT/'.local/backups'/('ecommerce_db-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S')+'.sql')
   backup.parent.mkdir(parents=True,exist_ok=True)
   dump=pathlib.Path(mysql).with_name('mysqldump.exe' if os.name=='nt' else 'mysqldump')
   result=subprocess.run([str(dump),*args,'--single-transaction','--no-tablespaces','--set-gtid-purged=OFF','--result-file='+str(backup),a.source],env=environment,capture_output=True,text=True)
   if result.returncode:raise RuntimeError('Backup failed: '+result.stderr.strip())
   print('Source backup saved under ignored .local/backups/.')
  sql(f'CREATE DATABASE IF NOT EXISTS `{schema}`')
  sql(f'CREATE TABLE IF NOT EXISTS `{schema}`._migration_ledger (table_name VARCHAR(128) PRIMARY KEY, source_count BIGINT NOT NULL, copied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)')
  for table in tables:
   if sql(f"SELECT table_name FROM `{schema}`._migration_ledger WHERE table_name='{table}'"):
    print(f'SKIP {schema}.{table}: already migrated; service changes preserved');continue
   sql(f'CREATE TABLE IF NOT EXISTS `{schema}`.`{table}` LIKE `{origin(table)}`.`{table}`')
   if int(sql(f'SELECT COUNT(*) FROM `{schema}`.`{table}`')[0][0]):raise RuntimeError(f'{schema}.{table} has untracked data; refusing to overwrite or mix it')
   columns=[row[0] for row in sql(f"SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='{origin(table)}' AND TABLE_NAME='{table}' ORDER BY COLUMN_NAME")]
   names=','.join('`'+name+'`' for name in columns)
   sql(f"START TRANSACTION WITH CONSISTENT SNAPSHOT; INSERT INTO `{schema}`.`{table}` ({names}) SELECT {names} FROM `{origin(table)}`.`{table}`; INSERT INTO `{schema}`._migration_ledger(table_name,source_count) SELECT '{table}',COUNT(*) FROM `{schema}`.`{table}`; COMMIT;")
   newly_copied.add(table)
   print('COPIED '+schema+'.'+table)
  if a.service=='auth':sql((ROOT/'tools/migrations/001_auth_extensions.sql').read_text(encoding='utf-8-sig'))
  if a.service=='product':
   extra=(ROOT/'tools/migrations/002_product_extensions.sql').read_text(encoding='utf-8-sig').replace('__SOURCE__',a.source)
   sql(extra)
 # Exact source equality is required at cutover. Re-running the importer after
 # service writes skips those tables; use ledger counts for historical evidence.
 failures=[]
 for table in tables:
  source_count=int(sql(f'SELECT COUNT(*) FROM `{origin(table)}`.`{table}`')[0][0]);target_count=int(sql(f'SELECT COUNT(*) FROM `{schema}`.`{table}`')[0][0])
  columns=[row[0] for row in sql(f"SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='{origin(table)}' AND TABLE_NAME='{table}' ORDER BY COLUMN_NAME")]
  types={row[0]:row[1] for row in sql(f"SELECT COLUMN_NAME,DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='{origin(table)}' AND TABLE_NAME='{table}'")}
  numeric={'tinyint','smallint','mediumint','int','bigint','bit','decimal','float','double'}
  names=','.join(f'CAST(`{name}` AS DECIMAL(65,12))' if types[name] in numeric else f'CAST(`{name}` AS CHAR)' for name in columns)
  expression=f'COALESCE(SUM(CRC32(CAST(JSON_ARRAY({names}) AS CHAR))),0),COALESCE(BIT_XOR(CRC32(CAST(JSON_ARRAY({names}) AS CHAR))),0)'
  source_checksum=sql(f'SELECT {expression} FROM `{origin(table)}`.`{table}`')[0];target_checksum=sql(f'SELECT {expression} FROM `{schema}`.`{table}`')[0]
  equal=source_count==target_count and source_checksum==target_checksum
  print(f'{"MATCH" if equal else "CHANGED"} {table}: source={source_count} target={target_count} checksum_equal={source_checksum==target_checksum}')
  if not equal and (a.verify or table in newly_copied):failures.append(table)
 if a.service=='auth':
  orphans=sql(f'SELECT COUNT(*) FROM `{schema}`.users u LEFT JOIN `{schema}`.customers c ON c.id=u.customer_id WHERE u.customer_id IS NOT NULL AND c.id IS NULL')[0][0]
  staff_orphans=sql(f'SELECT COUNT(*) FROM `{schema}`.staff_profiles s LEFT JOIN `{schema}`.users u ON u.id=s.user_id WHERE u.id IS NULL')[0][0]
  if int(orphans) or int(staff_orphans):raise RuntimeError('Auth referential consistency failed')
  checks={
   'customer/referrer': f'SELECT COUNT(*) FROM `{schema}`.customers c LEFT JOIN `{schema}`.customers r ON r.id=c.referred_by_id WHERE c.referred_by_id IS NOT NULL AND r.id IS NULL',
   'staff permissions/profile': f'SELECT COUNT(*) FROM `{schema}`.staff_permissions p LEFT JOIN `{schema}`.staff_profiles s ON s.id=p.staff_profile_id WHERE s.id IS NULL',
   'task/assignee': f'SELECT COUNT(*) FROM `{schema}`.staff_tasks t LEFT JOIN `{schema}`.users u ON u.id=t.assigned_to_user_id WHERE t.assigned_to_user_id IS NOT NULL AND u.id IS NULL',
   'staff notification/user': f'SELECT COUNT(*) FROM `{schema}`.staff_notifications n LEFT JOIN `{schema}`.users u ON u.id=n.user_id WHERE u.id IS NULL',
   'approval/requester': f'SELECT COUNT(*) FROM `{schema}`.approval_requests a LEFT JOIN `{schema}`.users u ON u.id=a.requested_by_user_id WHERE a.requested_by_user_id IS NOT NULL AND u.id IS NULL'}
  for reference,query in checks.items():
   if int(sql(query)[0][0]):raise RuntimeError('Orphaned '+reference+' reference')
  print('All auth-owned references verified: zero orphans.')
 if failures:raise RuntimeError('Source/target differ (expected after service writes): '+', '.join(failures))
 print('Migration complete; previously migrated tables preserved; original database unchanged.' if not a.verify else 'Exact source equality verified.')
if __name__=='__main__':
 try:main()
 except Exception as error:print('ERROR:',error,file=sys.stderr);sys.exit(1)
