const assert=require('node:assert/strict');
const fs=require('node:fs');
const crypto=require('node:crypto');
const {spawnSync}=require('node:child_process');
const base=process.env.API_URL||'http://localhost:8080';
async function request(path,{method='GET',body,token,status=200,owner='auth-service',direct}={}) {
 const r=await fetch((direct||base)+path,{method,headers:{...(body?{'Content-Type':'application/json'}:{}),...(token?{Authorization:`Bearer ${token}`}:{})},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(20000)});
 assert.equal(r.status,status,`${method} ${path}: expected ${status}, got ${r.status}`);
 if(!direct&&owner)assert.equal(r.headers.get('x-shopease-backend'),owner,`Wrong owner for ${path}`);
 console.log(`PASS ${method} ${path} -> ${r.status}${direct?' [direct downstream security]':` [${owner}]`}`);
 const text=await r.text();try{return JSON.parse(text)}catch{return text}
}
async function signup(prefix){
 const username=`${prefix}_${Date.now()}_${crypto.randomBytes(2).toString('hex')}`;
 const password=`TestOnly!9aA${crypto.randomBytes(10).toString('hex')}`;
 const otp=await request('/api/auth/register',{method:'POST',body:{username,email:username+'@example.test',password,role:'ADMIN'}});
 assert(otp.devOtp,'Automated test needs existing local demo OTP mode');
 const session=await request('/api/auth/verify-otp',{method:'POST',body:{username,otp:otp.devOtp}});
 assert.equal(session.role,'CUSTOMER');return {username,password,...session};
}
async function main(){
 const registry=await fetch('http://localhost:8761/eureka/apps',{headers:{Accept:'application/json'}});
 assert.equal(registry.status,200);const apps=(await registry.json()).applications.application;
 assert(apps.some(a=>a.name==='AUTH-SERVICE'&&a.instance.some(i=>i.status==='UP')));console.log('PASS Eureka: AUTH-SERVICE UP');
 await request('/api/customers/1',{status:401});
 const customer=await signup('phase1_customer');
 const login=await request('/api/auth/login',{method:'POST',body:{username:customer.username,password:customer.password}});
 customer.token=login.token;
 await request(`/api/customers/${customer.customerId}`,{token:customer.token});
 await request(`/api/customers/${customer.customerId}`,{method:'PUT',token:customer.token,body:{name:'Migration Test Customer',phone:'9999999999'}});
 await request(`/api/customers/${customer.customerId+100000}`,{token:customer.token,status:403});
 await request('/api/admin/users',{token:customer.token,status:403});
 await request('/api/staff/me',{token:customer.token,status:403});
 await request('/actuator/info',{direct:'http://localhost:8082',token:customer.token,owner:null});
 await request('/api/products',{method:'POST',body:{},token:customer.token,status:403,owner:'product-service'});
 await request('/api/products',{method:'POST',body:{},status:401,owner:'product-service'});
 const admin=await signup('phase1_admin');
 fs.writeFileSync('.local/auth-phase-fixtures.json',JSON.stringify({customer,admin}));
 const setup=spawnSync(process.env.PYTHON||'python',['tools/tests/promote_test_admin.py'],{encoding:'utf8'});
 assert.equal(setup.status,0,'Test-admin fixture setup failed');
 const adminLogin=await request('/api/auth/login',{method:'POST',body:{username:admin.username,password:admin.password}});
 assert.equal(adminLogin.role,'ADMIN');admin.token=adminLogin.token;
 const users=await request('/api/admin/users',{token:admin.token});assert(users.some(u=>u.username===customer.username));assert(users.every(u=>!('password'in u)));
 const staffEmail=`phase1_staff_${Date.now()}@example.test`;
 const application=await request('/api/staff/apply',{method:'POST',body:{fullName:'Migration Test Staff',email:staffEmail,phone:'9999999999',department:'INVENTORY',password:customer.password},status:201});
 await request('/api/auth/login',{method:'POST',body:{username:staffEmail,password:customer.password},status:403});
 await request(`/api/admin/staff/${application.id}/decision`,{method:'POST',body:{action:'APPROVE'},token:admin.token});
 const staff=await request('/api/auth/login',{method:'POST',body:{username:staffEmail,password:customer.password}});
 const me=await request('/api/staff/me',{token:staff.token});assert(me.authorities.includes('PERM_INVENTORY_UPDATE'));
 await request(`/api/admin/staff/${application.id}/decision`,{method:'POST',body:{action:'SUSPEND'},token:admin.token});
 await request('/api/staff/me',{token:staff.token,status:401});
 await request('/api/products',{method:'POST',body:{},token:staff.token,status:401,owner:'product-service'});
 const reset=await request('/api/auth/forgot-password',{method:'POST',body:{username:customer.username}});
 const newPassword=`Changed!9aA${crypto.randomBytes(10).toString('hex')}`;
 await request('/api/auth/reset-password',{method:'POST',body:{username:customer.username,otp:reset.devOtp,newPassword}});
 await request(`/api/customers/${customer.customerId}`,{token:customer.token,status:401});
 await request('/api/products',{method:'POST',body:{},token:customer.token,status:401,owner:'product-service'});
 await request('/api/auth/login',{method:'POST',body:{username:customer.username,password:customer.password},status:401});
 const refreshed=await request('/api/auth/login',{method:'POST',body:{username:customer.username,password:newPassword}});
 customer.password=newPassword;customer.token=refreshed.token;
 fs.writeFileSync('.local/auth-phase-fixtures.json',JSON.stringify({customer,admin}));
 console.log('SUCCESS: Auth Service owns signup/login/profiles/staff; downstream validates JWT, suspension and password-reset revocation. No passwords or tokens printed.');
}
main().catch(e=>{console.error('FAIL '+e.message);process.exitCode=1});
