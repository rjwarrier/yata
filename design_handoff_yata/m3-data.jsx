// YATA — data model + helpers (projects, lists, tags, people, recurrence, tasks)

function makeInitialData() {
  const A = ['accentA','accentB','accentC','accentD','accentE','accentF','accentG','accentH'];
  return {
    // ── People (local only — user creates & assigns) ──
    people: [
      { id:'me',    name:'Mira Castellanos', initials:'MC', color:'accentC', me:true },
      { id:'anders',name:'Anders Holm',      initials:'AH', color:'accentG' },
      { id:'priya', name:'Priya Nair',       initials:'PN', color:'accentF' },
      { id:'teo',   name:'Teo Alvarez',      initials:'TA', color:'accentE' },
      { id:'sam',   name:'Sam Okonkwo',      initials:'SO', color:'accentD' },
    ],
    // ── Projects group lists ──
    projects: [
      { id:'prod',   name:'Product Q2',     color:'accentC', icon:'layers', listIds:['work','design'] },
      { id:'home',   name:'Home & Life',    color:'accentE', icon:'home',   listIds:['personal','errands','family'] },
      { id:'growth', name:'Personal growth',color:'accentA', icon:'star',   listIds:['reading','side'] },
    ],
    lists: [
      { id:'work',    name:'Work',          color:'accentC', icon:'folder',  projectId:'prod' },
      { id:'design',  name:'Design',        color:'accentF', icon:'grid',    projectId:'prod' },
      { id:'personal',name:'Personal',      color:'accentE', icon:'home',    projectId:'home' },
      { id:'errands', name:'Errands',       color:'accentD', icon:'inbox',   projectId:'home' },
      { id:'family',  name:'Family',        color:'accentB', icon:'people',  projectId:'home' },
      { id:'reading', name:'Reading',       color:'accentA', icon:'star',    projectId:'growth' },
      { id:'side',    name:'Side projects', color:'accentG', icon:'sparkle', projectId:'growth' },
    ],
    tags: [
      { id:'design',      name:'design',      color:'accentF' },
      { id:'engineering', name:'engineering', color:'accentC' },
      { id:'meeting',     name:'meeting',     color:'accentD' },
      { id:'blocked',     name:'blocked',     color:'error'   },
      { id:'research',    name:'research',    color:'accentE' },
      { id:'quick-win',   name:'quick-win',   color:'accentA' },
      { id:'q2-goal',     name:'q2-goal',     color:'accentG' },
    ],
    tasks: [
      { id:'t1', title:'Review Q2 roadmap draft', section:'Morning', listId:'work',
        time:'9:00 AM', due:'today', priority:'high', flag:true, done:false,
        assigneeIds:['me','anders'], tagIds:['q2-goal','meeting'],
        recurrence:null },
      { id:'t2', title:'Gym — upper body', section:'Morning', listId:'personal',
        time:'10:30 AM', due:'today', priority:'none', done:true,
        assigneeIds:['me'], tagIds:[],
        recurrence:{ freq:'weekly', interval:1, byday:['MO','WE','FR'], ends:{type:'never'} } },
      { id:'t3', title:'Reply to Anders about the proposal', section:'Morning', listId:'work',
        due:'today', priority:'low', done:false, assigneeIds:['me'], tagIds:['engineering'], recurrence:null },
      { id:'t4', title:'Call with Priya re: onboarding flow', section:'Afternoon', listId:'design',
        time:'2:00 PM', due:'today', priority:'med', done:false,
        assigneeIds:['me','priya','teo'], tagIds:['design','q2-goal'],
        reminder:'15 min before',
        recurrence:{ freq:'weekly', interval:2, byday:['TH'], ends:{type:'never'} },
        notes:'Focus: how to cut steps 2–3. Bring the user research from last sprint, and the Figma link.',
        subtasks:[
          { id:'s1', title:'Prep talking points doc', done:true },
          { id:'s2', title:'Share latest mocks in advance', done:true },
          { id:'s3', title:'Draft success metrics', done:false },
          { id:'s4', title:'Send calendar invite to Teo', done:false },
        ] },
      { id:'t5', title:'Pick up dry cleaning', section:'Afternoon', listId:'errands',
        due:'today', priority:'none', done:false, assigneeIds:['me'], tagIds:[], recurrence:null },
      { id:'t6', title:'Water the plants', section:'Afternoon', listId:'personal',
        due:'today', priority:'none', done:false, assigneeIds:['me'], tagIds:[],
        recurrence:{ freq:'daily', interval:2, ends:{type:'never'} } },
      { id:'t7', title:'Ship v2 release notes', section:'Afternoon', listId:'work',
        time:'4:00 PM', due:'today', priority:'high', flag:true, done:false,
        assigneeIds:['me','sam'], tagIds:['engineering','q2-goal'], recurrence:null },
      { id:'t8', title:'Read: "Shape Up" ch. 4', section:'Afternoon', listId:'reading',
        due:'today', priority:'low', done:false, assigneeIds:['me'], tagIds:['research'], recurrence:null },
    ],
  };
}

// ── Lookups ──
const findPerson  = (s,id) => s.people.find(p=>p.id===id);
const findList    = (s,id) => s.lists.find(l=>l.id===id);
const findProject = (s,id) => s.projects.find(p=>p.id===id);
const findTag     = (s,id) => s.tags.find(t=>t.id===id);
const listColor   = (s,id) => { const l=findList(s,id); return l?M3[l.color]:M3.primary; };
const projectOfList = (s,listId) => { const l=findList(s,listId); return l?findProject(s,l.projectId):null; };

// ── Progress ──
function progressOf(tasks) {
  const total = tasks.length, done = tasks.filter(t=>t.done).length;
  return { total, done, pct: total ? done/total : 0 };
}
function tasksInProject(s, projectId) {
  const lids = (findProject(s,projectId)?.listIds)||[];
  return s.tasks.filter(t=>lids.includes(t.listId));
}
function tasksForPerson(s, personId) {
  return s.tasks.filter(t=>(t.assigneeIds||[]).includes(personId));
}
function tasksForTag(s, tagId) {
  return s.tasks.filter(t=>(t.tagIds||[]).includes(tagId));
}

// ── Priority meta ──
const PRIORITY = {
  none: { label:'None',   key:'none', dots:0 },
  low:  { label:'Low',    key:'low',  dots:1 },
  med:  { label:'Medium', key:'med',  dots:2 },
  high: { label:'High',   key:'high', dots:3 },
};
function priorityColor(p) { return p==='high' ? M3.error : p==='med' ? M3.accentD : p==='low' ? M3.accentE : M3.onSurfaceVariant; }

// ── RRULE-style recurrence summary ──
const DAY_ORDER = ['MO','TU','WE','TH','FR','SA','SU'];
const DAY_LABEL = { MO:'Mon',TU:'Tue',WE:'Wed',TH:'Thu',FR:'Fri',SA:'Sat',SU:'Sun' };
const DAY_FULL  = { MO:'Monday',TU:'Tuesday',WE:'Wednesday',TH:'Thursday',FR:'Friday',SA:'Saturday',SU:'Sunday' };

function recurrenceSummary(r) {
  if (!r) return 'Does not repeat';
  const n = r.interval || 1;
  const unit = { daily:'day', weekly:'week', monthly:'month', yearly:'year' }[r.freq] || 'day';
  let base;
  if (n === 1) base = { daily:'Every day', weekly:'Every week', monthly:'Every month', yearly:'Every year' }[r.freq];
  else base = `Every ${n} ${unit}s`;
  if (r.freq === 'weekly' && r.byday && r.byday.length) {
    // Special common phrasings
    const sorted = [...r.byday].sort((a,b)=>DAY_ORDER.indexOf(a)-DAY_ORDER.indexOf(b));
    if (sorted.length === 5 && !sorted.includes('SA') && !sorted.includes('SU')) base += ' on weekdays';
    else if (sorted.length === 2 && sorted.includes('SA') && sorted.includes('SU')) base += ' on weekends';
    else base += ' on ' + sorted.map(d=>DAY_LABEL[d]).join(', ');
  }
  if (r.freq === 'monthly' && r.bymonthday) base += ` on the ${ordinal(r.bymonthday)}`;
  if (r.ends) {
    if (r.ends.type === 'after') base += ` · ${r.ends.count}×`;
    else if (r.ends.type === 'on') base += ` · until ${r.ends.date}`;
  }
  return base;
}
function ordinal(n) {
  const s=['th','st','nd','rd'], v=n%100;
  return n + (s[(v-20)%10]||s[v]||s[0]);
}

// RRULE string (for handoff / realism)
function toRRULE(r) {
  if (!r) return '';
  const p = [`FREQ=${r.freq.toUpperCase()}`];
  if ((r.interval||1) > 1) p.push(`INTERVAL=${r.interval}`);
  if (r.byday && r.byday.length) p.push(`BYDAY=${r.byday.join(',')}`);
  if (r.bymonthday) p.push(`BYMONTHDAY=${r.bymonthday}`);
  if (r.ends && r.ends.type==='after') p.push(`COUNT=${r.ends.count}`);
  if (r.ends && r.ends.type==='on') p.push(`UNTIL=${(r.ends.date||'').replace(/-/g,'')}`);
  return 'RRULE:' + p.join(';');
}

Object.assign(window, {
  makeInitialData, findPerson, findList, findProject, findTag, listColor, projectOfList,
  progressOf, tasksInProject, tasksForPerson, tasksForTag,
  PRIORITY, priorityColor, recurrenceSummary, ordinal, toRRULE,
  DAY_ORDER, DAY_LABEL, DAY_FULL,
});
