'use strict';
const http=require('node:http');const fs=require('node:fs');const path=require('node:path');const cp=require('node:child_process');const assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..');const file=path.join(__dirname,'test-stream-state.json');const port=38879;
const settingsFile=path.join(root,'stream-settings.json');const prior=fs.existsSync(settingsFile)?fs.readFileSync(settingsFile):null;
const srv=cp.spawn(process.execPath,[path.join(root,'bridge','server.js'),'--port',String(port),'--input',file],{stdio:'pipe'});
function req(method,url,body){return new Promise((resolve,reject)=>{let r=http.request({hostname:'127.0.0.1',port,path:url,method,headers:body?{'Content-Type':'application/json'}:{}},res=>{let out='';res.on('data',c=>out+=c);res.on('end',()=>resolve({status:res.statusCode,data:out,headers:res.headers}));});r.on('error',reject);if(body)r.write(JSON.stringify(body));r.end();});}
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
(async()=>{
 try{
  for(let i=0;i<30;i++){try{const h=await req('GET','/health');if(h.status===200)break;}catch(e){}await sleep(100);}
  let a=JSON.parse((await req('GET','/api/state')).data);assert.equal(a.connected,false);
  let html=await req('GET','/overlay');assert.equal(html.status,200);assert(html.data.includes('Current')===false || html.data.includes('goalText'));
  let png=await req('GET','/assets/stream-frame.png');assert.equal(png.status,200);assert.equal(png.headers['content-type'],'image/png');
  let body={schemaVersion:1,guideId:'test',guideTitle:'HCIM Test',chapter:'Episode 1 - Banks 1 through 24:',section:'Bank 1',currentBank:'Bank 1',currentStep:'Pickpocket a man/woman',stepsTotal:3225,stepsDone:29,stepsSkipped:1,currentStepIndex:5,sectionIndex:1,sectionTotal:262,timestamp:new Date().toISOString()};fs.writeFileSync(file,JSON.stringify(body));
  a=JSON.parse((await req('GET','/api/state')).data);assert.equal(a.connected,true);assert.equal(a.guide.stepsDone,29);assert.equal(a.guide.currentStep,'Pickpocket a man/woman');assert.equal(a.currentGoal,'Vroege quests & accountbasis opbouwen');assert.equal(a.goalSource,'automatic');
  let setting=await req('POST','/api/settings',{manualGoal:'Fire cape halen',hardcoreStatus:'alive',deaths:0,questPercent:23,milestones:[true,false,false,false,false,false]});assert.equal(setting.status,200);
  a=JSON.parse((await req('GET','/api/state')).data);assert.equal(a.settings.manualGoal,'Fire cape halen');assert.equal(a.settings.milestones[0],true);assert.equal(a.currentGoal,'Fire cape halen');assert.equal(a.goalSource,'manual');
  let reject=await req('POST','/api/settings',{});assert.equal(reject.status,200);await req('POST','/api/settings',{manualGoal:''});a=JSON.parse((await req('GET','/api/state')).data);assert.equal(a.currentGoal,'Vroege quests & accountbasis opbouwen');
  let bad=await req('GET','/assets/../../server.js');assert.equal(bad.status,404);
  body.stepsDone=30;body.currentStep='Talk to Duke Horacio';fs.writeFileSync(file,JSON.stringify(body));
  a=JSON.parse((await req('GET','/api/state')).data);assert.equal(a.guide.stepsDone,30);assert.equal(a.guide.currentStep,'Talk to Duke Horacio');
  console.log('PASS: 12 checks — HTTP, PNG, RuneLite state, live goal, manual override, progress update, route isolation');
 }finally{srv.kill('SIGTERM');if(fs.existsSync(file))fs.unlinkSync(file);if(prior)fs.writeFileSync(settingsFile,prior);else if(fs.existsSync(settingsFile))fs.unlinkSync(settingsFile);}
})().catch(e=>{console.error(e);process.exitCode=1;});
