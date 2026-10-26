const assert=require('node:assert/strict'),fs=require('node:fs'),crypto=require('node:crypto');
const f=JSON.parse(fs.readFileSync('.local/auth-phase-fixtures.json','utf8'));
async function call(path,{method='GET',body,token=f.admin.token,status=200}={}){
 const response=await fetch('http://localhost:8080'+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(30000)});
 const text=await response.text();assert.equal(response.status,status,`${method} ${path}: ${text.slice(0,200)}`);console.log(`PASS ${method} ${path} -> ${status}`);try{return JSON.parse(text)}catch{return text}
}
async function main(){
 const password='Synthetic!9aA'+crypto.randomBytes(12).toString('hex');
 const email='staff_lifecycle_'+Date.now()+'@example.test';
 const application=await call('/api/staff/apply',{method:'POST',token:null,status:201,body:{fullName:'Synthetic Staff Lifecycle',email,password,department:'PRODUCT_MANAGER'}});
 assert((await call('/api/admin/staff?status=PENDING')).some(p=>p.id===application.id));
 const adminMessages=await call('/api/staff/notifications');assert(JSON.stringify(adminMessages).includes(email.split('@')[0]),'Admin must receive application notification');
 await call('/api/auth/login',{method:'POST',token:null,status:403,body:{username:email,password}});
 await call(`/api/admin/staff/${application.id}/decision`,{method:'POST',body:{action:'APPROVE',permissions:['PRODUCT_VIEW','PRODUCT_CREATE']}});
 const staff=await call('/api/auth/login',{method:'POST',token:null,body:{username:email,password}});
 let me=await call('/api/staff/me',{token:staff.token});assert(me.permissions.includes('PRODUCT_CREATE'));
 await call('/api/staff/dashboard',{token:staff.token});
 const product=await call('/api/products',{method:'POST',token:staff.token,status:201,body:{name:'Staff permissions fixture '+Date.now(),category:'Electronics',price:100,stockQuantity:1}});
 await call('/api/products/'+product.id,{method:'PUT',token:staff.token,status:403,body:{...product,price:99}});
 await call('/api/admin/users',{token:staff.token,status:403});
 await call(`/api/admin/staff/${application.id}/role`,{method:'PUT',body:{permissions:['PRODUCT_VIEW']}});
 await call('/api/products',{method:'POST',token:staff.token,status:403,body:{name:'Denied'}});
 assert(JSON.stringify(await call('/api/staff/notifications',{token:staff.token})).includes('approved'));
 await call(`/api/admin/staff/${application.id}/decision`,{method:'POST',body:{action:'SUSPEND',reason:'Synthetic revocation check'}});
 await call('/api/staff/me',{token:staff.token,status:401});
 const rejectedEmail='staff_rejected_'+Date.now()+'@example.test';
 const rejected=await call('/api/staff/apply',{method:'POST',token:null,status:201,body:{fullName:'Synthetic Rejected Staff',email:rejectedEmail,password,department:'INVENTORY'}});
 await call(`/api/admin/staff/${rejected.id}/decision`,{method:'POST',body:{action:'REJECT',reason:'Synthetic rejection check'}});
 await call('/api/auth/login',{method:'POST',token:null,status:403,body:{username:rejectedEmail,password}});
 const logs=await call(`/api/admin/staff/logs/STAFF_PROFILE/${application.id}`);assert(logs.some(l=>l.action==='STAFF_APPROVE'));assert(logs.some(l=>l.action==='STAFF_SUSPEND'));
 await call('/api/products/'+product.id,{method:'DELETE',status:204});
 console.log('SUCCESS: Staff application/admin notification/pending review/approval/rejection/permissions/login/dashboard/revocation/audit verified.');
}
main().catch(error=>{console.error('FAIL '+error.message);process.exitCode=1});
