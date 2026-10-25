const assert=require('node:assert/strict'),fs=require('node:fs'),crypto=require('node:crypto');
const base=process.env.API_URL||'http://localhost:8080';
const fixtures=JSON.parse(fs.readFileSync('.local/auth-phase-fixtures.json','utf8'));
const settings=fs.readFileSync('.local/application.properties','utf8').split(/\r?\n/);
const internalKey=settings.find(line=>line.startsWith('services.internal-key=')).split('=').slice(1).join('=');
let productId,categoryId,restockId,reservationId;
async function call(path,{method='GET',body,token,status=200,owner='product-service',internal=false}={}){
 const r=await fetch((internal?'http://localhost:8082':base)+path,{method,headers:{...(body?{'Content-Type':'application/json'}:{}),...(token?{Authorization:`Bearer ${token}`}:{}) ,...(internal?{'X-Service-Key':internalKey}:{})},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(20000)});
 assert.equal(r.status,status,`${method} ${path}: expected ${status}, got ${r.status}`);
 if(!internal)assert.equal(r.headers.get('x-shopease-backend'),owner,`Owner mismatch ${path}`);
 console.log(`PASS ${method} ${path} -> ${r.status} [${internal?'product internal API':owner}]`);
 const text=await r.text();try{return JSON.parse(text)}catch{return text}
}
async function main(){
 const admin=fixtures.admin.token,customer=fixtures.customer.token;
 const all=await call('/api/products');assert(all.length>=1222);console.log(`PASS ${all.length} real products from product-owned schema`);
 const catalog=await call('/api/products/catalog');assert.equal(catalog.length,all.length);assert(catalog.every(p=>'averageRating'in p&&'unitsSold'in p));
 const search=await call('/api/products/search?q=wireles%20erbuds');assert(search.results.length>0&&search.results.some(p=>/earbuds/i.test(p.name)));
 await call('/api/categories');await call('/api/products/6/price-history');
 const reviews=await call('/api/reviews/product/6');assert(reviews.length>0&&reviews.some(r=>r.customer?.name));
 await call('/api/reviews/product/6/ai-summary');
 await call('/api/products',{method:'POST',body:{},status:401});
 await call('/api/products',{method:'POST',body:{},token:customer,status:403});
 const name=`Migration test ${Date.now()}`;
 const category=await call('/api/categories',{method:'POST',token:admin,body:{name},status:201});categoryId=category.id;
 const product=await call('/api/products',{method:'POST',token:admin,body:{name,description:'Synthetic migration verification product',category:name,price:100,stockQuantity:5,sustainabilityScore:80},status:201});productId=product.id;
 await call(`/api/categories/${categoryId}`,{method:'PUT',token:admin,body:{name:name+' updated'}});
 const renamed=await call(`/api/products/${productId}`);assert.equal(renamed.category,name+' updated');
 await call(`/api/products/${productId}/ask`,{method:'POST',token:customer,body:{question:'How much does this cost?'}});
 const reviewBody={product:{id:productId},customer:{id:fixtures.customer.customerId},rating:5,comment:'Test'};
 await call('/api/reviews',{method:'POST',token:customer,body:reviewBody,status:400});
 const restock=await call('/api/restock-requests',{method:'POST',token:customer,body:{customer:{id:fixtures.customer.customerId},product:{id:productId}},status:201});restockId=restock.id;
 await call(`/api/restock-requests/customer/${fixtures.customer.customerId}`,{token:customer});
 await call(`/api/restock-requests/${restockId}`,{method:'DELETE',token:customer,status:204});restockId=null;
 reservationId='phase2_'+crypto.randomUUID();const reserve={operationId:reservationId,customerId:fixtures.customer.customerId,items:[{productId,quantity:1,expectedPrice:100}]};
 await call('/internal/inventory/reservations',{method:'POST',body:reserve,internal:true});
 await call('/internal/inventory/reservations',{method:'POST',body:reserve,internal:true});
 assert.equal((await call(`/api/products/${productId}`)).stockQuantity,4);
 await call(`/internal/inventory/reservations/${reservationId}/release`,{method:'POST',body:{},internal:true});
 await call(`/internal/inventory/reservations/${reservationId}/release`,{method:'POST',body:{},internal:true});reservationId=null;
 assert.equal((await call(`/api/products/${productId}`)).stockQuantity,5);
 const approval=await call('/api/staff/approvals',{method:'POST',token:admin,body:{type:'PRICE_CHANGE',productId,requestedPrice:80,reason:'Synthetic test'},status:201});
 // Controller uses 201 for a real approval request.
 assert(approval.id);
 await call(`/api/admin/staff/approvals/${approval.id}/decision`,{method:'POST',token:admin,body:{approve:true}});
 await call(`/api/admin/staff/approvals/${approval.id}/decision`,{method:'POST',token:admin,body:{approve:true},status:409});
 assert.equal(Number((await call(`/api/products/${productId}`)).price),80);
 const history=await call(`/api/products/${productId}/price-history`);assert(history.some(p=>Number(p.newPrice)===80));
 await call('/api/admin/staff/summary',{token:admin});await call('/api/admin/reviews',{token:admin});await call('/api/admin/alerts',{token:admin});await call('/api/admin/catalog/status',{token:admin});
 const asset=fs.readdirSync('backend/src/main/resources/static/products').find(n=>n.endsWith('.webp'));
 const image=await fetch(base+'/products/'+encodeURIComponent(asset));assert.equal(image.status,200);assert(image.headers.get('content-type').startsWith('image/'));assert.equal(image.headers.get('x-shopease-backend'),'product-service');console.log('PASS original product image through Gateway -> 200');
 await call(`/api/products/${productId}`,{method:'DELETE',token:admin,status:204});productId=null;
 await call(`/api/categories/${categoryId}`,{method:'DELETE',token:admin,status:204});categoryId=null;
 console.log('SUCCESS: Full product APIs, fuzzy search, existing reviews/images, admin CRUD, owned inventory and price approvals verified; synthetic catalog records removed.');
}
main().catch(async error=>{
 console.error('FAIL '+error.message);
 try{
  if(reservationId)await call(`/internal/inventory/reservations/${reservationId}/release`,{method:'POST',body:{},internal:true});
  if(restockId)await call(`/api/restock-requests/${restockId}`,{method:'DELETE',token:fixtures.customer.token,status:204});
  if(productId)await call(`/api/products/${productId}`,{method:'DELETE',token:fixtures.admin.token,status:204});
  if(categoryId)await call(`/api/categories/${categoryId}`,{method:'DELETE',token:fixtures.admin.token,status:204});
 }catch(cleanup){console.error('Synthetic fixture cleanup needs retry: '+cleanup.message)}
 process.exitCode=1;
});
