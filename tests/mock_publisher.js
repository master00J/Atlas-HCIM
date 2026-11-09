'use strict';
const fs=require('node:fs'),path=require('node:path');
const out=path.join(__dirname,'demo-stream-state.json');let done=143;
const write=()=>{fs.writeFileSync(out,JSON.stringify({schemaVersion:1,guideId:'hcim-atlas-demo',guideTitle:'HCIM Atlas · DEMO',chapter:'Starting out',section:'Bank 3',currentBank:'Bank 3',currentStep:done%2?'Talk to Father Aereck':'Collect 4x logs in Lumbridge Castle',stepsTotal:3225,stepsDone:done,stepsSkipped:0,currentStepIndex:4,sectionIndex:3,sectionTotal:262,timestamp:new Date().toISOString()}));};
write();console.log('DEMO RuneLite publisher started; no real RuneLite data used');setInterval(()=>{done++;write();},2500);
