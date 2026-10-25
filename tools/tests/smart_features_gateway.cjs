// Real Gateway requests; all mutations target a disposable product and test customer.
const assert=require('node:assert/strict'),fs=require('node:fs');
const f=JSON.parse(fs.readFileSync('.local/auth-phase-fixtures.json','utf8'));
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function call(path,{method='GET',body,token=f.customer.token,status=200}={}){
 const response=await fetch('http://localhost:8080'+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(30000)});
 const text=await response.text();assert.equal(response.status,status,`${method} ${path}: ${text.slice(0,200)}`);
 console.log(`PASS ${method} ${path} -> ${status}`);try{return JSON.parse(text)}catch{return text}
}
async function main(){
 let product=await call('/api/products',{method:'POST',token:f.admin.token,status:201,body:{name:'Smart repair verification '+Date.now(),category:'Electronics',specifications:'USB-C input',price:100,stockQuantity:0}});
 const path=`/api/wishlist/customer/${f.customer.customerId}/product/${product.id}`;
 try{
  const same=await call(`/api/compatibility?productA=${product.id}&productB=${product.id}`,{token:null});assert.equal(same.assessment,'SHARED_INTERFACE');
  const unknown=await call('/api/compatibility?productA=6&productB=7',{token:null});assert(unknown.assessment,'Same-category request must not throw');
  await call('/api/compatibility?productA=999999999&productB=6',{token:null,status:404});
  const qa=await call(`/api/products/${product.id}/ask`,{method:'POST',token:null,body:{question:'What warranty applies?'}});assert(!JSON.stringify(qa).includes('standard 1-year'));assert.equal(qa.source,'rules');
  await call('/api/wishlist',{method:'POST',status:201,body:{customerId:f.customer.customerId,productId:product.id}});
  await call(path+'/preferences',{method:'PUT',body:{targetPrice:75,notifyOnDrop:true,notifyOnRestock:false}});
  await call(path+'/preferences',{method:'PUT',body:{targetPrice:-1},status:400});
  await call(`/api/wishlist/customer/${f.admin.customerId}/product/${product.id}/preferences`,{method:'PUT',body:{targetPrice:1},status:403});
  const device=await call('/api/auth/login',{method:'POST',token:null,body:{username:f.customer.username,password:f.customer.password}});
  let saved=(await call('/api/wishlist/customer/'+f.customer.customerId,{token:device.token})).find(i=>i.product.id===product.id);assert.equal(Number(saved.targetPrice),75);assert.equal(saved.notifyOnRestock,false);
  await pause(3000);
  const messages=async()=> (await call('/api/notifications/customer/'+f.customer.customerId)).notifications.filter(n=>n.message.includes(product.name));
  product=await call('/api/products/'+product.id,{method:'PUT',token:f.admin.token,body:{...product,price:80}});
  await pause(5000);assert.equal((await messages()).length,0,'Above-target drop must not notify');
  product=await call('/api/products/'+product.id,{method:'PUT',token:f.admin.token,body:{...product,price:70}});
  let received=false;for(let i=0;i<15;i++){if((await messages()).some(n=>n.message.includes('price dropped'))){received=true;break}await pause(1000)}assert(received,'Target price event must notify');
  await call(path+'/preferences',{method:'PUT',body:{targetPrice:75,notifyOnDrop:false,notifyOnRestock:false}});await pause(3000);
  const before=(await messages()).length;
  product=await call('/api/products/'+product.id,{method:'PUT',token:f.admin.token,body:{...product,price:60,stockQuantity:5}});await pause(5000);
  assert.equal((await messages()).length,before,'Disabled price and restock preferences must suppress alerts');
  const history=await call(`/api/products/${product.id}/price-history`,{token:null});assert(history.some(h=>Number(h.newPrice)===60));
  console.log('SUCCESS: Public compatibility/Q&A, recorded price history, durable preferences across separate logins, target threshold, opt-outs and ownership verified.');
 }finally{
  await call(path,{method:'DELETE',status:204});
  await call('/api/products/'+product.id,{method:'DELETE',token:f.admin.token,status:204});
 }
}
main().catch(error=>{console.error('FAIL '+error.message);process.exitCode=1});
