'use strict';
const fmt = new Intl.NumberFormat('nl-BE');
const $ = (id) => document.getElementById(id);
const put=(id,s)=>{const el=$(id);if(el)el.textContent=String(s??'');};
const pct=(d,t)=>t>0?Math.max(0,Math.min(100,d/t*100)):0;
const percent=(x)=>`${x.toFixed(1).replace('.',',')}%`;
function updateView(payload){
 const active=payload.connected && payload.guide;
 const g=active?payload.guide:null;
 const s=payload.settings;
 const p=g?pct(g.stepsDone,g.stepsTotal):0;
 put('goalText',payload.currentGoal || 'Wacht op RuneLite...');
 put('stepText',g?g.currentStep:'RuneLite-guide niet verbonden');
 put('chapterText',g?g.chapter:'');
 put('bankText',g?(g.currentBank || g.section || 'Nog geen sectie'):'--');
 put('sectionText',g?`Sectie ${g.sectionIndex} / ${g.sectionTotal}`:'--');
 put('stepCount',g?`${fmt.format(g.stepsDone)} / ${fmt.format(g.stepsTotal)} stappen`:'Wachten op gegevens');
 put('percentText',g?percent(p):'--');
 put('questPercentText',s.questPercent===null?'--':percent(s.questPercent));
 put('hcStatus',s.hardcoreStatus==='alive'?'RED HELM ALIVE':s.hardcoreStatus==='lost'?'HARDCORE VERLOREN':'HCIM STATUS ONBEKEND');
 put('deathCount',`${s.deaths} deaths`);
 put('milestoneText',g?`HCIM Atlas · ${g.section||g.currentBank||'In progress'}`:'RuneLite offline · controleer de koppeling');
 const pg=$('overallFill');if(pg)pg.style.width=((g?p:0)*(Number(pg.dataset.span||100)/100))+'%';
 const qg=$('questFill');if(qg)qg.style.width=((s.questPercent??0)*(Number(qg.dataset.span||100)/100))+'%';
 const big=Array.from(document.querySelectorAll('[data-milestone]'));
 big.forEach(el=>{const i=Number(el.dataset.milestone);el.classList.toggle('unlocked',!!s.milestones[i]);el.title=s.milestoneLabels[i]||'';el.setAttribute('aria-label',(s.milestoneLabels[i]||'Milestone')+(s.milestones[i]?' voltooid':' open'));});
 document.body.dataset.connection=active?'online':'offline';
 document.body.dataset.hardcore=s.hardcoreStatus;
 const live=$('liveIndicator');if(live)live.textContent=active?'● LIVE SYNC':'○ OFFLINE';
}
let lastEvent=0;
const stream = new EventSource('/events');
stream.addEventListener('progress',(e)=>{try{updateView(JSON.parse(e.data));lastEvent=Date.now();}catch(err){console.error(err);}});
stream.addEventListener('error',()=>console.warn('SSE verbinding onderbroken; automatisch opnieuw verbinden'));
// Browser Sources may be suspended between scene switches: fallback poll.
setInterval(()=>{if(Date.now()-lastEvent>5000)fetch('/api/state',{cache:'no-store'}).then(r=>r.json()).then(updateView).catch(()=>{document.body.dataset.connection='offline';});},4000);
fetch('/api/state',{cache:'no-store'}).then(r=>r.json()).then(updateView).catch(()=>{});
