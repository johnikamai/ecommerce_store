"""Read-only cutover audit; cross-schema joins are offline checks, never service queries."""
import os,pathlib,shutil,subprocess,json
root=pathlib.Path(__file__).resolve().parents[2]
env=os.environ.copy();password=env.get('DB_PASSWORD',env.get('DB_PASS'))
if password is None:
 for line in (root/'.local/application.properties').read_text(encoding='utf-8-sig').splitlines():
  if line.startswith('spring.datasource.password='):password=line.split('=',1)[1]
env['MYSQL_PWD']=password or ''
mysql=shutil.which('mysql') or r'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
checks={
 'order/customer':'shopease_order.orders o LEFT JOIN shopease_auth.customers c ON c.id=o.customer_id WHERE c.id IS NULL',
 'order item/order':'shopease_order.order_items i LEFT JOIN shopease_order.orders o ON o.id=i.order_id WHERE o.id IS NULL',
 'order item/product':'shopease_order.order_items i LEFT JOIN shopease_product.products p ON p.id=i.product_id WHERE p.id IS NULL',
 'review/customer':'shopease_product.reviews r LEFT JOIN shopease_auth.customers c ON c.id=r.customer_id WHERE c.id IS NULL',
 'review/product':'shopease_product.reviews r LEFT JOIN shopease_product.products p ON p.id=r.product_id WHERE p.id IS NULL',
 'payment/order':'shopease_payment.payments p LEFT JOIN shopease_order.orders o ON o.id=p.order_id WHERE o.id IS NULL',
 'notification/customer':'shopease_notification.notifications n LEFT JOIN shopease_auth.customers c ON c.id=n.customer_id WHERE c.id IS NULL',
 'group member/group':'shopease_order.group_order_members m LEFT JOIN shopease_order.group_orders g ON g.id=m.group_order_id WHERE g.id IS NULL',
 'group member/customer':'shopease_order.group_order_members m LEFT JOIN shopease_auth.customers c ON c.id=m.customer_id WHERE c.id IS NULL',
 'contribution/member':'shopease_payment.group_payments p LEFT JOIN shopease_order.group_order_members m ON m.id=p.member_id WHERE m.id IS NULL',
 'tracking/order':'shopease_order.order_status_events e LEFT JOIN shopease_order.orders o ON o.id=e.order_id WHERE o.id IS NULL',
}
failed=False
for label,join in checks.items():
 result=subprocess.run([mysql,'-h',env.get('DB_HOST','127.0.0.1'),'-P',env.get('DB_PORT','3306'),'-u',env.get('DB_USERNAME','root'),*(['--ssl-mode='+env['DB_SSL_MODE']] if env.get('DB_SSL_MODE') else []),*(['--ssl-ca='+env['DB_SSL_CA']] if env.get('DB_SSL_CA') else []),'--batch','--skip-column-names','-e','SELECT COUNT(*) FROM '+join],env=env,capture_output=True,text=True)
 if result.returncode:raise RuntimeError('Audit query failed: '+label)
 count=int(result.stdout.strip());print(f'{label}: {count} orphan references');failed|=count>0
if failed:raise SystemExit(1)
print('All service references verified without modifying any data.')
