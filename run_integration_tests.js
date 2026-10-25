// Each phase uses real owners through Gateway; all requires the complete migration.
const {spawnSync}=require('node:child_process');
const suites={auth:'tools/tests/auth_gateway.cjs',product:'tools/tests/product_gateway.cjs',order:'tools/tests/order_gateway.cjs',payment:'tools/tests/payment_gateway.cjs',notification:'tools/tests/notification_gateway.cjs',recommendation:'tools/tests/recommendation_gateway.cjs'};
const requested=process.argv[2]||'all';
const phases=requested==='all'?Object.keys(suites):[requested];
const failures=[];
function run(name,path){
 const result=spawnSync(process.execPath,[path],{stdio:'inherit',env:process.env});
 if(result.status!==0)failures.push(name);
}
for(const phase of phases){
 if(!suites[phase]){console.error(`No verified suite for phase ${phase}`);process.exit(1)}
 run(phase,suites[phase]);
 if(phase==='auth'&&!failures.includes('auth'))run('account-deletion-parity','tools/tests/account_deletion_gateway.cjs');
 if(phase==='order'){
  run('group-orders','tools/tests/group_gateway.cjs');
  run('cart-total-parity','tools/tests/cart_totals_gateway.cjs');
 }
 if(phase==='payment')run('unpaid-refund-consistency','tools/tests/unpaid_refund_gateway.cjs');
 if(phase==='notification'){
  run('wishlist-alerts','tools/tests/alerts_gateway.cjs');
 }
}
if(requested==='all')run('gateway-health-contract','tools/tests/gateway_routes.cjs');
if(requested==='all'&&Object.keys(suites).length<6){
 console.error('Full microservices acceptance is not complete. Only the listed migrated phases were tested.');process.exit(1);
}
if(failures.length){console.error('FAILED SUITES: '+failures.join(', '));process.exit(1)}
console.log('All requested acceptance suites passed.');
