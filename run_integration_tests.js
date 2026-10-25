// Each phase uses real owners through Gateway; all requires the complete migration.
const {spawnSync}=require('node:child_process');
const suites={auth:'tools/tests/auth_gateway.cjs',product:'tools/tests/product_gateway.cjs'};
const requested=process.argv[2]||'all';
const phases=requested==='all'?Object.keys(suites):[requested];
for(const phase of phases){
 if(!suites[phase]){console.error(`No verified suite for phase ${phase}`);process.exit(1)}
 const run=spawnSync(process.execPath,[suites[phase]],{stdio:'inherit',env:process.env});
 if(run.status!==0)process.exit(run.status||1);
}
if(requested==='all'&&Object.keys(suites).length<6){
 console.error('Full microservices acceptance is not complete. Only the listed migrated phases were tested.');process.exit(1);
}
