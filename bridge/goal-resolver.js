'use strict';
// Goal derivation based on the current guide section. Never treats the goal as completed.
const rules = require('../guide/goal-rules.json');
const fallbackGoal = 'Guidefase vervolledigen';
function getBankNumber(section) {
  const m = String(section || '').match(/^Bank\s+(\d+)(?:[A-Z])?\b/i);
  return m ? Number(m[1]) : null;
}
function resolveGoal(guide) {
  if (!guide) return { goal:'Wacht op RuneLite...', ruleId:'offline' };
  const ch=String(guide.chapter||'').trim().toLowerCase();
  const sec=String(guide.section||guide.currentBank||'').trim().toLowerCase();
  const bank=getBankNumber(guide.section||guide.currentBank);
  for (const rule of rules.rules) {
    if (rule.chapterPrefix) {
      const prefix = rule.chapterPrefix.toLowerCase();
      if (!ch.startsWith(prefix)) continue;
      // 'Episode 1' must never match 'Episode 10', 'Episode 11', etc.
      if (/\d$/.test(prefix) && /\d/.test(ch.charAt(prefix.length))) continue;
    }
    if (rule.sectionPrefix && !sec.startsWith(rule.sectionPrefix.toLowerCase())) continue;
    if (rule.bankMin!==undefined && (bank===null || bank<rule.bankMin)) continue;
    if (rule.bankMax!==undefined && (bank===null || bank>rule.bankMax)) continue;
    return {goal:rule.goal,ruleId:rule.id};
  }
  // Fallback for sub-expeditions and future guide chapters: a stable higher-level title.
  if (sec && /^e\d{2}\s*[—–-]/.test(sec)) {
    const name=String(guide.section||guide.currentBank).replace(/^E\d{2}\s*[—–-]\s*/i,'');
    return {goal:name.slice(0,130),ruleId:'section-fallback'};
  }
  if (ch && !/^episode\s+\d+/.test(ch)) {
    return {goal:String(guide.chapter).replace(/^\d+[.]\s*/,'').slice(0,130),ruleId:'chapter-fallback'};
  }
  return {goal:guide.section && !/^bank\b/i.test(guide.section) ? guide.section.slice(0,130) : fallbackGoal, ruleId:'fallback'};
}
module.exports={resolveGoal,getBankNumber};
