// Compare the production frontend bundle helpers with real Gateway quotes and payments.
const assert=require('node:assert/strict'),fs=require('node:fs'),crypto=require('node:crypto');
const {pathToFileURL}=require('node:url'),{resolve}=require('node:path');
const fixtures=JSON.parse(fs.readFileSync('.local/auth-phase-fixtures.json','utf8'));
let customer=fixtures.customer;const products=[];let order;
async function call(path,{method='GET',body,admin=false,anonymous=false,status=200,owner='order-service',key}={}){
 const response=await fetch('http://localhost:8080'+path,{method,headers:{'Content-Type':'application/json',...(!anonymous?{Authorization:'Bearer '+(admin?fixtures.admin.token:customer.token)}:{}),...(key?{'Idempotency-Key':key}:{})},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(45000)});
 const raw=await response.text();assert.equal(response.status,status,`${method} ${path}: ${response.status} ${raw.slice(0,200)}`);assert.equal(response.headers.get('x-shopease-backend'),owner);try{return JSON.parse(raw)}catch{return raw}
}
const cents=value=>Math.round(Number(value)*100);
async function main(){
 const {bundlePercentFor,nextBundleTier}=await import(pathToFileURL(resolve('frontend/src/utils/cartCalculations.js')).href);
 const username='cart_parity_'+Date.now()+'_'+crypto.randomBytes(3).toString('hex');const password='TestOnly!9aA'+crypto.randomBytes(12).toString('hex');
 const otp=await call('/api/auth/register',{method:'POST',anonymous:true,owner:'auth-service',body:{username,password,email:username+'@example.test'}});assert(otp.devOtp,'This local verification requires demo OTP mode');
 customer=await call('/api/auth/verify-otp',{method:'POST',anonymous:true,owner:'auth-service',body:{username,otp:otp.devOtp}});
 for(const price of [500,400,300,200,998.99,999])products.push(await call('/api/products',{method:'POST',admin:true,status:201,owner:'product-service',body:{name:'Cart parity verification '+Date.now()+' '+price,category:'Electronics',price,stockQuantity:10}}));
 const cases=[
  ['one distinct product',[[0,1]]],['two distinct products / 5%',[[0,1],[1,1]]],
  ['three distinct products / 10%',[[0,1],[1,1],[2,1]]],['four distinct products / 15%',[[0,1],[1,1],[2,1],[3,1]]],
  ['quantity does not increase bundle tier',[[0,2]]],['below free-shipping threshold',[[4,1]]],['at free-shipping threshold',[[5,1]]],
 ];
 let checkoutRequest,checkoutQuote;
 for(const [label,lines]of cases){
  const request={customerId:customer.customerId,paymentMethod:'CARD',shippingAddress:'Synthetic cart verification, Hyderabad 500001',items:lines.map(([index,quantity])=>({productId:products[index].id,quantity}))};
  const quote=await call('/api/orders/quote',{method:'POST',body:request});
  const subtotal=lines.reduce((sum,[index,quantity])=>sum+cents(products[index].price)*quantity,0);
  const discount=Math.round(subtotal*bundlePercentFor(lines.length)),shipping=subtotal>=99900?0:4900;
  const tax=Math.round((subtotal-discount)*0.18),total=subtotal-discount+shipping+tax;
  assert.equal(cents(quote.subtotal),subtotal);assert.equal(cents(quote.bundleDiscount),discount);assert.equal(cents(quote.shipping),shipping);assert.equal(cents(quote.tax),tax);assert.equal(cents(quote.total),total);assert.equal(cents(quote.couponDiscount),0);assert.equal(cents(quote.tierDiscount),0);
  assert.equal(nextBundleTier(lines.length),lines.length<2?2:lines.length<4?lines.length+1:0);
  console.log(`PASS frontend/Gateway total parity: ${label} -> ${(total/100).toFixed(2)}`);
  if(lines.length===4){checkoutRequest={...request,expectedTotal:quote.total};checkoutQuote=quote;}
 }
 const coupons=await call('/api/coupons',{admin:true,owner:'payment-service'});
 const coupon=coupons.find(c=>c.active&&!c.expired&&(c.maxUses==null||c.timesUsed<c.maxUses)&&Number(c.minimumOrderAmount||0)<=1400);
 if(coupon){
  const quote=await call('/api/orders/quote',{method:'POST',body:{...checkoutRequest,couponCode:coupon.code}});
  const couponOff=Math.min(140000,coupon.discountType==='PERCENT'?Math.round(140000*Number(coupon.discountValue)/100):cents(coupon.discountValue));
  const bundle=Math.round((140000-couponOff)*bundlePercentFor(4)),goods=140000-couponOff-bundle;
  assert.equal(cents(quote.couponDiscount),couponOff);assert.equal(cents(quote.bundleDiscount),bundle);assert.equal(cents(quote.total),goods+Math.round(goods*0.18));
  console.log('PASS Gateway coupon stacks before frontend bundle tier; authoritative total is consistent');
 }else console.log('SKIP coupon stacking: no existing redeemable coupon; no shared coupon was modified');
 const key='cart-parity:'+crypto.randomUUID();order=await call('/api/orders',{method:'POST',body:checkoutRequest,status:201,key});
 assert.equal(cents(order.totalAmount),cents(checkoutQuote.total));assert.equal(cents(order.bundleDiscountAmount),cents(checkoutQuote.bundleDiscount));
 const duplicate=await call('/api/orders',{method:'POST',body:checkoutRequest,status:201,key});assert.equal(duplicate.id,order.id);
 const payment=await call('/api/payments/order/'+order.id,{owner:'payment-service'});assert.equal(cents(payment.amount),cents(checkoutQuote.total));assert.equal(cents(payment.totalAmount),cents(checkoutQuote.total));
 console.log('PASS quoted bundle total equals persisted order and payment; retry creates one order');
 await call('/api/orders/'+order.id+'/cancel',{method:'POST',body:{}});order=null;
 assert.equal((await call('/api/payments/order/'+duplicate.id,{owner:'payment-service'})).paymentStatus,'REFUNDED');
 for(const product of products.slice(0,4))assert.equal((await call('/api/products/'+product.id,{owner:'product-service'})).stockQuantity,10);
 console.log('PASS synthetic checkout cancelled/refunded; stock restored');
 for(const product of products.slice(4))await call('/api/products/'+product.id,{method:'DELETE',admin:true,status:204,owner:'product-service'});
 console.log('SUCCESS: Cart helper extraction preserves Gateway quote/order/payment totals; synthetic financial history retained.');
}
main().catch(async error=>{console.error('FAIL '+error.message);if(order)try{await call('/api/orders/'+order.id+'/cancel',{method:'POST',body:{}})}catch{console.error('Synthetic order cleanup needs retry')}process.exitCode=1});
