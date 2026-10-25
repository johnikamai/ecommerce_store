const assert=require('node:assert/strict'),fs=require('node:fs');
async function main(){
 const config=fs.readFileSync('api-gateway/src/main/resources/application.yml','utf8');
 assert(!/8087|COMPATIBILITY_URI|monolith-compatibility/.test(config),'Gateway retains a legacy monolith route');
 console.log('PASS Gateway configuration contains no legacy fallback');
 const response=await fetch('http://localhost:8080/api/health',{signal:AbortSignal.timeout(10000)});
 assert.equal(response.status,200,'Existing GET /api/health contract must be served through Gateway');
 assert.equal(response.headers.get('x-shopease-backend'),'auth-service');
 console.log('PASS GET /api/health -> 200 [auth-service]');
}
main().catch(error=>{console.error('FAIL '+error.message);process.exitCode=1});
