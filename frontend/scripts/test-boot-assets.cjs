const fs=require('fs'),path=require('path'),assert=require('node:assert/strict');
const code=fs.readFileSync(path.resolve(__dirname,'../src/utils/bootAssets.js'),'utf8').replace(/export /g,'');
function create(){const pending=[];const Image=class{set src(v){this.url=v;pending.push(this)}decode(){return this.decoded||Promise.resolve()}};const root={querySelectorAll:()=>[]};const api=new Function('Image','window','document',code+';return {trackBootRequest,preloadImage,waitForBootAssets}')(Image,{location:{href:'https://example.test/'}},{getElementById:()=>root});return {api,pending}}
const delay=ms=>new Promise(r=>setTimeout(r,ms));
(async()=>{
 const {api,pending}=create();const end=api.trackBootRequest('/portal/article/list');let complete=false;
 const updates=[];const result=api.waitForBootAssets({onProgress:p=>updates.push(p)}).then(x=>{complete=true;return x});await delay(350);assert(!complete);
 end(true,{list:[{cover:'/one.jpg'},{cover:'/one.jpg'},{cover:'/two.jpg'}]});assert.equal(pending.length,2);
 let decode;pending[0].decoded=new Promise(r=>decode=r);pending[0].onload();pending[1].onload();await delay(350);assert(!complete);decode();assert.equal((await result).failed,0);assert.equal(updates.at(-1).total,2);assert.equal(updates.at(-1).loaded,2);assert.equal(updates.at(-1).requestsPending,0);assert(updates.some(p=>p.requestsPending===1));
 assert.equal(api.trackBootRequest('/portal/config'),null);
 const broken=create();broken.api.preloadImage('/bad.jpg');broken.pending[0].onerror();assert.equal((await broken.api.waitForBootAssets()).failed,1);
 const slow=create();const done=slow.api.trackBootRequest('/portal/background');assert.equal((await slow.api.waitForBootAssets({timeout:150})).timedOut,true);done(false);
 console.log('PASS: data barrier, duplicate covers, image decode barrier, failure state, timeout and post-boot opt-out');
})().catch(e=>{console.error(e);process.exitCode=1});
