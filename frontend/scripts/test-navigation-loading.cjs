const fs=require('fs'),path=require('path'),assert=require('node:assert/strict'),vm=require('node:vm'),Vue=require('vue'),{parse,compileTemplate}=require('@vue/compiler-sfc');
const root=path.resolve(__dirname,'..');let now=0,serial=0;const timers=new Map();const timeout=(fn,delay)=>{const id=++serial;timers.set(id,{fn,at:now+delay});return id},clear=id=>timers.delete(id);
function tick(ms){now+=ms;for(const [id,t] of [...timers])if(t.at<=now){timers.delete(id);t.fn()}}
const code=fs.readFileSync(path.join(root,'src/utils/navigationLoading.js'),'utf8').replace(/import[^\n]+\n/g,'').replace(/export /g,'');
const api=new Function('reactive','setTimeout','clearTimeout',code+';return {state:navigationLoading,startNavigation,finishNavigation,failNavigation}')(Vue.reactive,timeout,clear);
const a={fullPath:'/portal/a'},b={fullPath:'/portal/b'};
api.startNavigation(a);tick(100);assert(!api.state.visible);api.finishNavigation(a);tick(300);assert(!api.state.visible);
api.startNavigation(a);tick(160);assert(api.state.visible);api.startNavigation(b);api.finishNavigation(a,true);tick(300);assert(api.state.visible);api.finishNavigation(b);assert(api.state.done);tick(200);assert(!api.state.visible);
api.startNavigation(a);tick(170);api.failNavigation(a);assert(api.state.error);assert.equal(api.state.retryPath,a.fullPath);assert(!api.state.visible);api.startNavigation(b);assert(!api.state.error);api.finishNavigation(b,true);tick(200);assert(!api.state.visible);api.failNavigation(undefined);
let removed=false,ready=false;const nodes={'iris-boot':{classList:{add(){ready=true}},setAttribute(){},remove(){removed=true}},'iris-boot-message':{},'iris-boot-retry':{hidden:true}};
const scripts=[...fs.readFileSync(path.join(root,'index.html'),'utf8').matchAll(/<script>([\s\S]*?)<\/script>/g)];const boot=scripts.find(x=>x[1].includes('__irisBoot'))[1];
const context={document:{getElementById:id=>nodes[id]},window:{setTimeout:timeout},setTimeout:timeout,clearTimeout:clear};vm.runInNewContext(boot,context);tick(20000);assert(!nodes['iris-boot-retry'].hidden);context.window.__irisBoot.ready();assert(ready);tick(230);assert(removed);
const {descriptor}=parse(fs.readFileSync(path.join(root,'src/components/NavigationLoading.vue'),'utf8'));assert.deepEqual(compileTemplate({source:descriptor.template.content,filename:'NavigationLoading.vue',id:'qa'}).errors,[]);
console.log('PASS: fast navigation, overlapping/cancelled routes, errors/retry, boot timeout and cleanup');
