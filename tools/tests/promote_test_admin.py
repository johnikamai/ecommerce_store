"""Promote only a newly created phase1 test fixture in the isolated auth schema."""
import json,os,pathlib,re,subprocess
root=pathlib.Path(__file__).resolve().parents[2]
fixture=json.loads((root/'.local/auth-phase-fixtures.json').read_text())['admin']
username=fixture['username']
assert re.fullmatch(r'phase1_admin_[0-9]+_[a-f0-9]{4}',username),'Refusing non-fixture account'
environment=os.environ.copy()
password=environment.get('DB_PASSWORD',environment.get('DB_PASS'))
if password is None:
 for line in (root/'.local/application.properties').read_text(encoding='utf-8-sig').splitlines():
  if line.startswith('spring.datasource.password='):password=line.split('=',1)[1]
environment['MYSQL_PWD']=password or ''
mysql=r'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
query=f"UPDATE shopease_auth.users SET role='ADMIN' WHERE username='{username}' AND email='{username}@example.test' AND role='CUSTOMER' AND enabled=1; SELECT ROW_COUNT();"
result=subprocess.run([mysql,'-u',environment.get('DB_USERNAME','root'),'--batch','--skip-column-names','-e',query],env=environment,capture_output=True,text=True)
assert result.returncode==0 and result.stdout.strip()=='1','Fixture promotion failed'
print('Synthetic test admin fixture prepared; original database unchanged.')
