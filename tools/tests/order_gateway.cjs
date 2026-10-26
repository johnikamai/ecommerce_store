const assert=require('node:assert/strict'),fs=require('node:fs'),crypto=require('node:crypto');
const f=JSON.parse(fs.readFileSync('.local/auth-phase-fixtures.json','utf8'));
async function call(path,{method='GET',body,admin=false,status=200,owner='order-service',key}={}){
 const r=await fetch('http://localhost:8080'+path,{method,headers:{Authorization:'Bearer '+(admin?f.admin.token:f.customer.token),...(body?{'Content-Type':'application/json'}:{}),...(key?{'Idempotency-Key':key}:{})},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(45000)});
 const raw=await r.text();assert.equal(r.status,status,`${method} ${path}: ${r.status} ${raw.slice(0,400)}`);assert.equal(r.headers.get('x-shopease-backend'),owner);console.log(`PASS ${method} ${path} -> ${r.status} [${owner}]`);try{return JSON.parse(raw)}catch{return raw}
}
async function main(){
 const product=await call('/api/products',{method:'POST',admin:true,owner:'product-service',status:201,body:{name:'Order migration verification '+Date.now(),description:'Synthetic native checkout fixture',category:'Electronics',price:100,stockQuantity:10}});
 const request={customerId:f.customer.customerId,paymentMethod:'CARD',shippingAddress:'Migration test address, Hyderabad 500001',items:[{productId:product.id,quantity:2}]};
 const quote=await call('/api/orders/quote',{method:'POST',body:request});
 console.log('Quote fields: '+Object.keys(quote).join(','));
 request.expectedTotal=quote.total;
 const key='verify:'+crypto.randomUUID();
 const order=await call('/api/orders',{method:'POST',status:201,body:request,key});assert(order.id);assert.equal(order.paymentStatus,'PAID');
 const duplicate=await call('/api/orders',{method:'POST',status:201,body:request,key});assert.equal(duplicate.id,order.id);
 assert.equal((await call('/api/products/'+product.id,{owner:'product-service'})).stockQuantity,8);
 const payment=await call('/api/payments/order/'+order.id,{owner:'payment-service'});assert.equal(payment.paymentStatus,'PAID');
 await call('/api/payments/'+payment.id+'/receipt',{owner:'payment-service'});
 await call('/api/orders/customer/'+f.customer.customerId);
 await call('/api/orders/'+order.id+'/tracking');
 const cancelled=await call('/api/orders/'+order.id+'/cancel',{method:'POST',body:{}});assert.equal(cancelled.status,'CANCELLED');
 await call('/api/orders/'+order.id+'/cancel',{method:'POST',body:{}});
 assert.equal((await call('/api/products/'+product.id,{owner:'product-service'})).stockQuantity,10);
 assert.equal((await call('/api/payments/order/'+order.id,{owner:'payment-service'})).paymentStatus,'REFUNDED');
 const address=await call('/api/addresses',{method:'POST',status:201,body:{customerId:f.customer.customerId,label:'Migration verification',name:'Synthetic Customer',addressLine:'Test address',city:'Hyderabad',state:'Telangana',pincode:'500001',phone:'9000000000'}});
 await call('/api/addresses/customer/'+f.customer.customerId);await call('/api/addresses/'+address.id,{method:'DELETE',status:204});
 await call('/api/wishlist',{method:'POST',status:201,body:{customerId:f.customer.customerId,productId:product.id}});
 const wishes=await call('/api/wishlist/customer/'+f.customer.customerId);assert(wishes.some(w=>w.product.id===product.id));
 await call(`/api/wishlist/customer/${f.customer.customerId}/product/${product.id}`,{method:'DELETE',status:204});
 console.log('SUCCESS: Native quote, checkout retry, payment, receipt, history, tracking, cancellation/refund, stock restoration, address and wishlist verified.');
}
main().catch(e=>{console.error('FAIL '+e.message);process.exitCode=1});

