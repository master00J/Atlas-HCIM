#!/usr/bin/env node
'use strict';
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const {resolveGoal} = require('./goal-resolver');

const ROOT = path.resolve(__dirname, '..');
const HOST = '127.0.0.1';
const args = process.argv.slice(2);
const arg = (key, fallback) => { const i=args.indexOf(key); return i>=0 && args[i+1] ? args[i+1] : fallback; };
const PORT = Number(arg('--port','38877'));
const demo = args.includes('--demo');
const INPUT = path.resolve(arg('--input', demo ? path.join(ROOT, 'tests', 'demo-stream-state.json') : path.join(os.homedir(),'.runelite','hcim-guide','stream-state.json')));
const SETTINGS = path.join(ROOT, 'stream-settings.json');
const STATIC = path.join(ROOT,'assets');
let subscribers = new Set();
let settings = {manualGoal:'', hardcoreStatus:'unknown', deaths:0, questPercent:null,
  milestones: [false,false,false,false,false,false], milestoneLabels:['Barrows gloves','Piety','Song of the Elves','Fire cape','Bowfa','Inferno']};
try { settings = {...settings,...JSON.parse(fs.readFileSync(SETTINGS,'utf8'))}; } catch (_) {}
const clean = (s,max=300)=>typeof s==='string'?s.replace(/[\u0000-\u001f]/g,' ').slice(0,max):'';
const writeAtomic=(file,data)=>{let temp=file+'.tmp';fs.writeFileSync(temp,data,{encoding:'utf8',mode:0o600});fs.renameSync(temp,file);};
function readGuide(){
 try {
  let st=fs.statSync(INPUT);
  if (st.size > 200000 || st.size === 0) return null;
  let x=JSON.parse(fs.readFileSync(INPUT,'utf8'));
  if(x.schemaVersion!==1 || !Number.isFinite(x.stepsTotal)|| !Number.isFinite(x.stepsDone))return null;
  return {guideId:clean(x.guideId,90), guideTitle:clean(x.guideTitle,120), chapter:clean(x.chapter,120),
   section:clean(x.section,120), currentBank:clean(x.currentBank,100), currentStep:clean(x.currentStep,420),
   currentStepIndex:Number.isFinite(x.currentStepIndex)?x.currentStepIndex:0, stepsTotal:Math.max(0,Math.trunc(x.stepsTotal)),
   stepsDone:Math.max(0,Math.trunc(x.stepsDone)), stepsSkipped:Math.max(0,Math.trunc(x.stepsSkipped||0)),
   sectionIndex:Math.max(0,Math.trunc(x.sectionIndex||0)), sectionTotal:Math.max(0,Math.trunc(x.sectionTotal||0)),
   timestamp:typeof x.timestamp==='string'?x.timestamp:'', ageMs:Date.now()-st.mtimeMs,
   connected:(Date.now()-st.mtimeMs)<15000 && (Date.now()-st.mtimeMs)>-60000};
 } catch (_) {return null;}
}
function state(){
 const g=readGuide();
 const goalResult=resolveGoal(g&&g.connected?g:null);
 const manual=clean(settings.manualGoal,130);
 const currentGoal=manual||goalResult.goal;
 return {connected:!!(g&&g.connected), guide:g,
   automaticGoal:goalResult.goal,currentGoal,goalSource:manual?'manual':'automatic',goalRuleId:goalResult.ruleId,
   settings:{manualGoal:clean(settings.manualGoal,130),
  hardcoreStatus:['alive','lost','unknown'].includes(settings.hardcoreStatus)?settings.hardcoreStatus:'unknown',
  deaths:Number.isInteger(settings.deaths)?Math.max(0,settings.deaths):0,
  questPercent:settings.questPercent===null?null:Math.max(0,Math.min(100,Number(settings.questPercent)||0)),
  milestones:settings.milestones.slice(0,6).map(Boolean), milestoneLabels:settings.milestoneLabels.slice(0,6).map(x=>clean(x,35))},
  demo};
}
const reply=(res,status,obj)=>{res.writeHead(status,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});res.end(JSON.stringify(obj));};
function sendEvent(res,payload){try{res.write('event: progress\ndata: '+payload+'\n\n');}catch(_){subscribers.delete(res);}}
let previous='';
function broadcast(){const cur=JSON.stringify(state());if(cur!==previous){previous=cur;for(const s of subscribers)sendEvent(s,cur);}}
let healthCheck=setInterval(broadcast,1000);healthCheck.unref();
function staticFile(res, filename, type){const f=path.join(STATIC,filename);try{const st=fs.statSync(f);res.writeHead(200,{'Content-Type':type,'Content-Length':st.size,'Cache-Control':'public, max-age=3600','X-Content-Type-Options':'nosniff'});fs.createReadStream(f).pipe(res);}catch(_){res.writeHead(404);res.end('Not found');}}
const routes={'/':'control.html','/control':'control.html','/overlay':'overlay.html','/overlay-classic':'overlay-classic.html','/webcam-frame':'webcam-frame.html','/widget':'widget.html'};
const contentType=(file)=>file.endsWith('.css')?'text/css; charset=utf-8':file.endsWith('.js')?'text/javascript; charset=utf-8':file.endsWith('.png')?'image/png':'text/html; charset=utf-8';
const server=http.createServer((req,res)=>{
 const url=new URL(req.url,'http://'+HOST+':'+PORT);
 if(req.method==='GET' && routes[url.pathname])return staticFile(res,routes[url.pathname],contentType(routes[url.pathname]));
 if(req.method==='GET' && url.pathname.startsWith('/assets/')){
  const f=url.pathname.slice('/assets/'.length);if(!/^[a-z0-9_.-]+$/i.test(f))return reply(res,400,{error:'Invalid asset'});
  return staticFile(res,f,contentType(f));
 }
 if(req.method==='GET' && url.pathname==='/api/state')return reply(res,200,state());
 if(req.method==='GET' && url.pathname==='/health')return reply(res,200,{ok:true,bridge:'HCIM Atlas',input:INPUT,hasFile:!!readGuide()});
 if(req.method==='GET' && url.pathname==='/events'){
  res.writeHead(200,{'Content-Type':'text/event-stream','Cache-Control':'no-cache, no-transform','Connection':'keep-alive','X-Accel-Buffering':'no'});
  subscribers.add(res);sendEvent(res,JSON.stringify(state()));req.on('close',()=>subscribers.delete(res));return;
 }
 if(req.method==='POST' && url.pathname==='/api/settings'){
  const origin=req.headers.origin;
  if(origin && origin!==`http://${HOST}:${PORT}` && origin!==`http://localhost:${PORT}`)return reply(res,403,{error:'Invalid origin'});
  if(!(req.headers['content-type']||'').startsWith('application/json'))return reply(res,415,{error:'JSON only'});
  let chunks='';req.on('data',b=>{chunks+=b;if(chunks.length>16384)req.destroy();});req.on('end',()=>{
   let body;try{body=JSON.parse(chunks)}catch(_){return reply(res,400,{error:'Invalid JSON'});}
   if(!body||typeof body!=='object'||Array.isArray(body))return reply(res,400,{error:'Invalid settings'});
   if('manualGoal'in body) settings.manualGoal=clean(body.manualGoal,130);
   if('hardcoreStatus'in body && ['alive','lost','unknown'].includes(body.hardcoreStatus))settings.hardcoreStatus=body.hardcoreStatus;
   if('deaths'in body && Number.isInteger(body.deaths))settings.deaths=Math.min(999,Math.max(0,body.deaths));
   if('questPercent'in body)settings.questPercent=body.questPercent===null || body.questPercent===''?null:Math.max(0,Math.min(100,Number(body.questPercent)||0));
   if(Array.isArray(body.milestones)&&body.milestones.length===6)settings.milestones=body.milestones.map(Boolean);
   try{writeAtomic(SETTINGS,JSON.stringify(settings,null,2));}catch(e){return reply(res,500,{error:'Cannot save settings: '+e.message});}
   broadcast();return reply(res,200,{ok:true,settings:state().settings});
  });return;
 }
 return reply(res,404,{error:'Not found'});
});
if(!Number.isInteger(PORT)||PORT<1024||PORT>65535){console.error('Ongeldige poort.');process.exit(1);}
server.listen(PORT,HOST,()=>{
 console.log('HCIM ATLAS LIVE STREAM');
 console.log('Bediening: http://'+HOST+':'+PORT+'/control');
 console.log('OBS / vMix: http://'+HOST+':'+PORT+'/overlay');
 console.log('Legacy:     http://'+HOST+':'+PORT+'/overlay-classic');
 console.log('Webcam:     http://'+HOST+':'+PORT+'/webcam-frame');
 console.log('Widget:     http://'+HOST+':'+PORT+'/widget');
 console.log('Bronbestand: '+INPUT);
 console.log('Status: '+(demo?'DEMONSTRATIE (geen RuneLite-data)':'Wacht op RuneLite-export'));
});
