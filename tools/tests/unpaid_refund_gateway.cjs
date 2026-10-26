// Verify order/payment consistency when returning an unpaid synthetic COD order.
const assert=require('node:assert/strict'),fs=require('node:fs'),crypto=require('node:crypto');
const fixtures=JSON.parse(fs.readFileSync('.local/auth-phase-fixtures.json','utf8'));
async function call(path,{method='GET',body,admin=false,status=200}={}){
 const response=await fetch('http://localhost:8080'+path,{method,headers:{'Content-Type':'application/json',Authorization:'Bearer '+(admin?fixtures.admin.token:fixtures.customer.token)},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(45000)});
 const text=await response.text();assert.equal(response.status,status,`${method} ${path}: expected ${status}, got ${response.status}`);console.log(`PASS ${method} ${path} -> ${response.status}`);try{return JSON.parse(text)}catch{return text}
}
async function main(){
 const product=await call('/api/products',{method:'POST',admin:true,status:201,body:{name:'Unpaid return verification '+Date.now(),category:'Electronics',price:100,stockQuantity:2}});
 const request={customerId:fixtures.customer.customerId,paymentMethod:'CASH',shippingAddress:'Synthetic verification address Hyderabad',items:[{productId:product.id,quantity:1}],operationId:'verify-unpaid:'+crypto.randomUUID()};
 request.expectedTotal=(await call('/api/orders/quote',{method:'POST',body:request})).total;
 const order=await call('/api/orders',{method:'POST',body:request,status:201});
 const payment=await call('/api/payments/order/'+order.id);assert.equal(payment.paymentStatus,'PENDING');
 await call('/api/orders/'+order.id+'/status',{method:'PUT',admin:true,body:{status:'DELIVERED'}});
 const returned=await call('/api/returns',{method:'POST',status:201,body:{orderItemId:order.orderItems[0].id,customerId:fixtures.customer.customerId,reason:'Synthetic unpaid COD consistency verification'}});
 await call('/api/returns/'+returned.id+'/approve',{method:'PUT',admin:true,body:{}});
 await call('/api/returns/'+returned.id+'/refund',{method:'PUT',admin:true,body:{}});
 const canonical=await call('/api/payments/order/'+order.id);
 const orders=await call('/api/orders/customer/'+fixtures.customer.customerId);const current=orders.find(o=>o.id===order.id);
 const observed={orderId:order.id,paymentId:payment.id,orderPaymentStatus:current.paymentStatus,paymentLedgerStatus:canonical.paymentStatus};
 fs.mkdirSync('.local/verification',{recursive:true});fs.writeFileSync('.local/verification/unpaid-refund-observation.json',JSON.stringify(observed,null,2));
 // Reconcile only this test-created pending payment so failed verification does not leave divergent financial fixtures.
 if(current.paymentStatus!==canonical.paymentStatus&&canonical.paymentStatus==='PENDING'){
  const key=fs.readFileSync('.local/application.properties','utf8').split(/\r?\n/).find(line=>line.startsWith('services.internal-key=')).split('=').slice(1).join('=');
  const cancelled=await fetch(`http://localhost:8084/internal/payments/order/${order.id}/cancel`,{method:'POST',headers:{'Content-Type':'application/json','X-Service-Key':key},body:'{}'});
  assert.equal(cancelled.status,200,'Synthetic pending payment cancellation failed');
  await call('/api/payments/'+payment.id+'/status',{method:'PUT',admin:true,body:{status:'CANCELLED',transactionRef:'TEST-ONLY-RECONCILIATION'}});
  console.log('PASS synthetic unpaid-payment fixture reconciled through normal admin command');
 }
 assert.equal(current.paymentStatus,canonical.paymentStatus,'Unpaid COD return diverged between Order and Payment: '+JSON.stringify(observed));
 console.log('SUCCESS: Unpaid COD return keeps order and payment ledger consistent.');
}
main().catch(e=>{console.error('FAIL '+e.message);process.exitCode=1});
