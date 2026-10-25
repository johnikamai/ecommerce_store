// Check legacy account-deletion parity using a newly created disposable account only.
const assert=require('node:assert/strict'),fs=require('node:fs'),crypto=require('node:crypto');
const fixtures=JSON.parse(fs.readFileSync('.local/auth-phase-fixtures.json','utf8'));
async function call(path,{method='GET',body,token,status=200}={}){
 const response=await fetch('http://localhost:8080'+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(45000)});
 assert.equal(response.status,status,`${method} ${path}: expected ${status}, got ${response.status}`);
 console.log(`PASS ${method} ${path} -> ${response.status}`);const text=await response.text();try{return JSON.parse(text)}catch{return text}
}
async function main(){
 const username='verification_delete_'+Date.now()+'_'+crypto.randomBytes(3).toString('hex');const password='Disposable!9aA'+crypto.randomBytes(12).toString('hex');
 const challenge=await call('/api/auth/register',{method:'POST',body:{username,password,email:username+'@example.test'}});assert(challenge.devOtp,'Local demo OTP is required for this test');
 const customer=await call('/api/auth/verify-otp',{method:'POST',body:{username,otp:challenge.devOtp}});
 const users=await call('/api/admin/users',{token:fixtures.admin.token});const user=users.find(u=>u.username===username);assert(user&&user.role==='CUSTOMER');
 await call('/api/addresses',{method:'POST',token:customer.token,status:201,body:{customerId:customer.customerId,label:'Disposable verification',name:'Synthetic verification',addressLine:'Test-only address',city:'Hyderabad',state:'Telangana',pincode:'500001'}});
 await call('/api/wishlist',{method:'POST',token:customer.token,status:201,body:{customerId:customer.customerId,productId:6}});
 await call('/api/admin/users/'+user.id,{method:'DELETE',token:fixtures.admin.token,status:204});
 await call('/api/customers/'+customer.customerId,{token:fixtures.admin.token,status:404});
 const addresses=await call('/api/addresses/customer/'+customer.customerId,{token:fixtures.admin.token});assert.equal(addresses.length,0);
 const wishlist=await call('/api/wishlist/customer/'+customer.customerId,{token:fixtures.admin.token});assert.equal(wishlist.length,0);
 await new Promise(resolve=>setTimeout(resolve,3000));
 const notifications=await call('/api/notifications/customer/'+customer.customerId,{token:fixtures.admin.token});assert.equal(notifications.notifications.length,0);
 console.log('SUCCESS: Admin deletion removes the disposable login and profile, preserving legacy contract.');
}
main().catch(e=>{console.error('FAIL '+e.message);process.exitCode=1});
