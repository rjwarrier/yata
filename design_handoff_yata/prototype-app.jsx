// YATA prototype — app shell: nav stack, theming, actions, confetti, drawer, sheets

function PrototypeApp() {
  const [data, setData] = React.useState(makeInitialData);
  const [tab, setTab] = React.useState(0);        // 0 Today 1 Projects 2 People 3 Tags 4 Upcoming
  const [stack, setStack] = React.useState([]);
  const [drawer, setDrawer] = React.useState(false);
  const [sheetStack, setSheetStack] = React.useState([]); // allow nested sheets
  const [theme, setThemeState] = React.useState(() => localStorage.getItem('yata-theme') || 'light');
  const [celebrate, setCelebrate] = React.useState(0);

  // Apply theme synchronously each render so all children read the right palette
  applyTheme(theme);
  const setTheme = (t) => { localStorage.setItem('yata-theme', t); setThemeState(t); };

  // ── Actions ──
  const actions = React.useMemo(() => ({
    toggleDone: (id) => setData(s => {
      let becameDone = false;
      const tasks = s.tasks.map(t => {
        if (t.id !== id) return t;
        becameDone = !t.done;
        return { ...t, done: !t.done };
      });
      if (becameDone) setCelebrate(c => c + 1);
      return { ...s, tasks };
    }),
    toggleSubtask: (taskId, subId) => setData(s => ({ ...s, tasks: s.tasks.map(t => t.id===taskId
      ? { ...t, subtasks: t.subtasks.map(st => st.id===subId ? { ...st, done:!st.done } : st) } : t) })),
    toggleFlag: (id) => setData(s => ({ ...s, tasks: s.tasks.map(t => t.id===id ? { ...t, flag:!t.flag } : t) })),
    cyclePriority: (id) => setData(s => { const order=['none','low','med','high'];
      return { ...s, tasks: s.tasks.map(t => t.id===id ? { ...t, priority: order[(order.indexOf(t.priority||'none')+1)%4] } : t) }; }),
    setRecurrence: (id, rec) => setData(s => ({ ...s, tasks: s.tasks.map(t => t.id===id ? { ...t, recurrence:rec } : t) })),
    setTaskList: (id, listId) => setData(s => ({ ...s, tasks: s.tasks.map(t => t.id===id ? { ...t, listId } : t) })),
    toggleAssignee: (taskId, pid) => setData(s => ({ ...s, tasks: s.tasks.map(t => { if(t.id!==taskId) return t;
      const a=t.assigneeIds||[]; return { ...t, assigneeIds: a.includes(pid)?a.filter(x=>x!==pid):[...a,pid] }; }) })),
    toggleTaskTag: (taskId, tid) => setData(s => ({ ...s, tasks: s.tasks.map(t => { if(t.id!==taskId) return t;
      const a=t.tagIds||[]; return { ...t, tagIds: a.includes(tid)?a.filter(x=>x!==tid):[...a,tid] }; }) })),
    addTask: ({ title, listId, priority, assigneeIds, tagIds, recurrence }) => setData(s => {
      const nt = { id:'n'+Date.now(), title, section:'Afternoon', listId:listId||'work', due:'today',
        priority:priority||'none', done:false, assigneeIds:assigneeIds||['me'], tagIds:tagIds||[], recurrence:recurrence||null };
      return { ...s, tasks:[...s.tasks, nt] };
    }),
    addPerson: ({ name, color, initials }) => setData(s => ({ ...s, people:[...s.people, { id:'p'+Date.now(), name, color, initials }] })),
    updatePerson: (id, patch) => setData(s => ({ ...s, people: s.people.map(p => p.id===id ? { ...p, ...patch } : p) })),
    deletePerson: (id) => setData(s => ({ ...s, people: s.people.filter(p=>p.id!==id),
      tasks: s.tasks.map(t => ({ ...t, assigneeIds:(t.assigneeIds||[]).filter(x=>x!==id) })) })),
    addTag: ({ name, color }) => setData(s => ({ ...s, tags:[...s.tags, { id:'tag'+Date.now(), name, color }] })),
    addProject: ({ name, color }) => setData(s => { const lid='l'+Date.now(), pid='pr'+Date.now();
      return { ...s, projects:[...s.projects, { id:pid, name, color, icon:'layers', listIds:[lid] }],
        lists:[...s.lists, { id:lid, name:'General', color, icon:'folder', projectId:pid }] }; }),
  }), []);

  // ── Nav ──
  const nav = React.useMemo(() => ({
    goTab: (i) => { setStack([]); setTab(i); },
    push: (name, params) => setStack(p => [...p, { name, params, id: Date.now()+Math.random() }]),
    pop: () => setStack(p => p.slice(0,-1)),
    openDrawer: () => setDrawer(true),
    closeDrawer: () => setDrawer(false),
    openSheet: (name, params) => setSheetStack(p => [...p, { name, params, id: Date.now()+Math.random() }]),
    closeSheet: () => setSheetStack(p => p.slice(0,-1)),
  }), []);

  React.useEffect(() => {
    const onKey = (e) => { if (e.key==='Escape') {
      if (sheetStack.length) nav.closeSheet(); else if (drawer) nav.closeDrawer(); else if (stack.length) nav.pop();
    }};
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [sheetStack, drawer, stack, nav]);

  const showFab = (tab===0 || tab===1) && stack.length===0;
  const fabParams = tab===1 ? { } : {};

  return (
    <div style={{ width:'100vw', height:'100vh', display:'flex', alignItems:'center', justifyContent:'center',
      background: theme==='dark' ? '#0c0806' : '#241612',
      backgroundImage: theme==='dark' ? 'radial-gradient(circle at 30% 15%, #241a17 0%, #0c0806 60%)' : 'radial-gradient(circle at 30% 15%, #3a2820 0%, #1a1110 65%)' }}>
      <PrototypeFrame theme={theme}>
        <TabSwitcher tab={tab} state={data} nav={nav} actions={actions} />

        <div style={{ position:'absolute', bottom:0, left:0, right:0, zIndex:5,
          opacity: stack.length?0:1, transform: stack.length?'translateY(20px)':'translateY(0)',
          pointerEvents: stack.length?'none':'auto', transition:`opacity ${DUR.fade}ms, transform ${DUR.fade}ms` }}>
          <PTBottomNav active={tab} onChange={(i)=>nav.goTab(i)} />
        </div>

        {showFab && (
          <div style={{ position:'absolute', bottom:88, right:20, zIndex:6,
            transition:`transform ${DUR.fade}ms ${EASE.spring}, opacity ${DUR.fade}ms`,
            opacity: sheetStack.length?0:1, transform: sheetStack.length?'scale(0.3)':'scale(1)' }}>
            <Press onClick={()=> tab===1 ? nav.openSheet('projectEditor') : nav.openSheet('newTask')}>
              <FAB label={tab===1?'New project':'New task'} icon="add" extended size="md" color="primary" />
            </Press>
          </div>
        )}

        {stack.map((s,i) => (
          <PushedScreen key={s.id} screen={s} state={data} nav={nav} actions={actions}
            theme={theme} setTheme={setTheme} depth={i} total={stack.length} />
        ))}

        <DrawerOverlay open={drawer} onClose={nav.closeDrawer}>
          <PTDrawer state={data} nav={nav} />
        </DrawerOverlay>

        {sheetStack.map((sh,i) => (
          <SheetOverlay key={sh.id} open onClose={nav.closeSheet} z={60+i}>
            <SheetRouter sheet={sh} state={data} nav={nav} actions={actions} />
          </SheetOverlay>
        ))}

        <Confetti trigger={celebrate} />
      </PrototypeFrame>
      <Hints />
    </div>
  );
}

function SheetRouter({ sheet, state, nav, actions }) {
  const map = {
    newTask: PTNewTaskSheet, recurrence: PTRecurrenceSheet, assignee: PTAssigneeSheet,
    tagPicker: PTTagPickerSheet, listPicker: PTListPickerSheet, personEditor: PTPersonEditorSheet,
    projectEditor: PTProjectEditorSheet, tagEditor: PTTagEditorSheet,
  };
  const S = map[sheet.name];
  return S ? <S state={state} nav={nav} actions={actions} params={sheet.params} /> : null;
}

function TabSwitcher({ tab, state, nav, actions }) {
  const screens = [PTToday, PTProjects, PTPeople, PTTags, PTUpcoming];
  return (
    <div style={{ position:'absolute', inset:0 }}>
      {screens.map((S,i) => (
        <div key={i} style={{ position:'absolute', inset:0,
          opacity: i===tab?1:0, transform: i===tab?'scale(1)':'scale(0.98)',
          transition:`opacity ${DUR.fade}ms ${EASE.standard}, transform ${DUR.fade}ms ${EASE.standard}`,
          pointerEvents: i===tab?'auto':'none' }}>
          <S state={state} nav={nav} actions={actions} />
        </div>
      ))}
    </div>
  );
}

function PushedScreen({ screen, state, nav, actions, theme, setTheme, depth, total }) {
  const [entered, setEntered] = React.useState(false);
  React.useEffect(()=>{ requestAnimationFrame(()=>requestAnimationFrame(()=>setEntered(true))); }, []);
  const map = {
    listDetail: PTListDetail, taskDetail: PTTaskDetail, projectDetail: PTProjectDetail,
    personDetail: PTPersonDetail, tagDetail: PTTagDetail, search: PTSearch, settings: PTSettings,
  };
  const S = map[screen.name];
  if (!S) return null;
  const isTop = depth === total-1;
  return (
    <div style={{ position:'absolute', inset:0,
      transform: entered ? (isTop?'translateX(0)':'translateX(-28%)') : 'translateX(100%)',
      opacity: entered ? (isTop?1:0.5) : 0,
      transition:`transform ${DUR.nav}ms ${EASE.emphDecel}, opacity ${DUR.nav}ms ${EASE.standard}`,
      zIndex:10+depth, background:M3.surface, boxShadow: isTop?`-8px 0 24px rgba(${M3.elevTint},0.14)`:'none' }}>
      <S state={state} nav={nav} actions={actions} params={screen.params} theme={theme} setTheme={setTheme} />
    </div>
  );
}

function DrawerOverlay({ open, onClose, children }) {
  const [mounted, setMounted] = React.useState(open);
  const [entered, setEntered] = React.useState(false);
  React.useEffect(()=>{ if(open){ setMounted(true); requestAnimationFrame(()=>requestAnimationFrame(()=>setEntered(true))); }
    else { setEntered(false); const t=setTimeout(()=>setMounted(false),DUR.sheet); return ()=>clearTimeout(t); } }, [open]);
  if (!mounted) return null;
  return (
    <div style={{ position:'absolute', inset:0, zIndex:50 }}>
      <div onClick={onClose} style={{ position:'absolute', inset:0, background:M3.scrim, opacity:entered?1:0, transition:`opacity ${DUR.sheet}ms` }} />
      <div style={{ position:'absolute', top:0, left:0, bottom:0, transform:entered?'translateX(0)':'translateX(-100%)', transition:`transform ${DUR.sheet}ms ${EASE.emphDecel}` }}>
        {children}
      </div>
    </div>
  );
}

function SheetOverlay({ open, onClose, children, z = 60 }) {
  const [mounted, setMounted] = React.useState(open);
  const [entered, setEntered] = React.useState(false);
  React.useEffect(()=>{ if(open){ setMounted(true); requestAnimationFrame(()=>requestAnimationFrame(()=>setEntered(true))); }
    else { setEntered(false); const t=setTimeout(()=>setMounted(false),DUR.sheet); return ()=>clearTimeout(t); } }, [open]);
  if (!mounted) return null;
  return (
    <div style={{ position:'absolute', inset:0, zIndex:z }}>
      <div onClick={onClose} style={{ position:'absolute', inset:0, background:M3.scrim, opacity:entered?1:0, transition:`opacity ${DUR.sheet}ms` }} />
      <div style={{ position:'absolute', left:0, right:0, bottom:0, background:M3.surfaceContainerLowest,
        borderRadius:`${SHAPE.xl}px ${SHAPE.xl}px 0 0`, boxShadow:ELEV.l3,
        transform:entered?'translateY(0)':'translateY(100%)', transition:`transform ${DUR.sheet}ms ${EASE.emphDecel}` }}>
        {children}
      </div>
    </div>
  );
}

// ── Bottom nav (5 tabs) ──
function PTBottomNav({ active, onChange }) {
  const items = [
    { icon:'today', label:'Today' },
    { icon:'layers', label:'Projects' },
    { icon:'people', label:'People' },
    { icon:'tag', label:'Tags' },
    { icon:'upcoming', label:'Upcoming' },
  ];
  return (
    <div style={{ height:70, background:M3.surfaceContainer, display:'flex', padding:'8px 4px', gap:2, borderTop:`1px solid ${M3.outlineVariant}` }}>
      {items.map((it,i) => { const isA=i===active; return (
        <Press key={i} onClick={()=>onChange(i)} style={{ flex:1 }}>
          <div style={{ display:'flex', flexDirection:'column', alignItems:'center', gap:3 }}>
            <div style={{ height:30, padding:'0 14px', borderRadius:SHAPE.full, background:isA?M3.secondaryContainer:'transparent',
              display:'flex', alignItems:'center', justifyContent:'center', transition:`background ${DUR.fade}ms ${EASE.standard}` }}>
              <Icon name={it.icon} size={21} color={isA?M3.onSecondaryContainer:M3.onSurfaceVariant} filled={isA} />
            </div>
            <div style={{ ...TYPE.label, fontSize:10.5, fontWeight:isA?600:500, color:isA?M3.onSurface:M3.onSurfaceVariant }}>{it.label}</div>
          </div>
        </Press>
      ); })}
    </div>
  );
}

// ── Phone frame ──
function PrototypeFrame({ children, theme }) {
  const [scale, setScale] = React.useState(1);
  React.useEffect(()=>{ const calc=()=>{ const s=Math.min(1,(window.innerHeight-48)/864,(window.innerWidth-40)/430); setScale(Math.max(0.55,s)); };
    calc(); window.addEventListener('resize',calc); return ()=>window.removeEventListener('resize',calc); }, []);
  const surface = M3.surface;
  const ink = M3.onSurface;
  return (
    <div style={{ width:390, height:844, borderRadius:50, transform:`scale(${scale})`, transformOrigin:'center center',
      background:surface, border:`10px solid #0a0a0a`,
      boxShadow:'0 30px 80px rgba(0,0,0,0.5), 0 4px 12px rgba(0,0,0,0.3), inset 0 0 0 1px rgba(255,255,255,0.05)',
      display:'flex', flexDirection:'column', boxSizing:'border-box', overflow:'hidden', position:'relative' }}>
      <div style={{ height:38, display:'flex', alignItems:'center', justifyContent:'space-between', padding:'0 22px',
        position:'relative', flexShrink:0, background:surface, zIndex:100 }}>
        <span style={{ ...TYPE.label, fontSize:14, fontWeight:600, color:ink, letterSpacing:0.2 }}>9:41</span>
        <div style={{ position:'absolute', left:'50%', top:9, transform:'translateX(-50%)', width:110, height:30, borderRadius:22, background:'#0a0a0a' }} />
        <div style={{ display:'flex', alignItems:'center', gap:6 }}>
          <svg width="16" height="12" viewBox="0 0 16 12"><path d="M1 11h2V8H1zM5 11h2V6H5zM9 11h2V4H9zM13 11h2V2h-2z" fill={ink}/></svg>
          <svg width="16" height="12" viewBox="0 0 16 12"><path d="M8 11.2L.5 3.7a11 11 0 0115 0L8 11.2z" fill={ink}/></svg>
          <svg width="24" height="12" viewBox="0 0 24 12"><rect x="1" y="1.5" width="20" height="9" rx="2" fill="none" stroke={ink} strokeWidth="1.2"/><rect x="3" y="3" width="16" height="6" rx="1" fill={ink}/></svg>
        </div>
      </div>
      <div style={{ flex:1, position:'relative', overflow:'hidden', background:surface }}>{children}</div>
      <div style={{ height:28, display:'flex', alignItems:'center', justifyContent:'center', background:surface, flexShrink:0, zIndex:100 }}>
        <div style={{ width:130, height:4, borderRadius:2, background:ink }} />
      </div>
    </div>
  );
}

// ── Hints ──
function Hints() {
  const [show, setShow] = React.useState(true);
  React.useEffect(()=>{ const t=setTimeout(()=>setShow(false),7000); return ()=>clearTimeout(t); }, []);
  if (!show) return (
    <div onClick={()=>setShow(true)} style={{ position:'fixed', top:20, right:20, padding:'8px 14px',
      background:'rgba(255,255,255,0.1)', color:'#fff', borderRadius:999, fontFamily:'Inter, system-ui', fontSize:12, cursor:'pointer' }}>?</div>
  );
  return (
    <div style={{ position:'fixed', top:28, left:28, maxWidth:280, background:'rgba(255,255,255,0.06)', backdropFilter:'blur(20px)',
      border:'1px solid rgba(255,255,255,0.1)', borderRadius:16, padding:'14px 16px', color:'#F0DED8', fontFamily:'Inter, system-ui' }}>
      <div style={{ fontWeight:600, fontSize:13, marginBottom:8, fontFamily:'"Inter Tight", system-ui' }}>YATA · interactive prototype</div>
      <div style={{ fontSize:12, lineHeight:1.5, opacity:0.8 }}>
        Complete a task for confetti · add people & tags · open a task → Repeats for RRULE rules · toggle theme in Settings.<br/>
        <span style={{ opacity:0.6 }}>Esc = back</span>
      </div>
      <div onClick={()=>setShow(false)} style={{ marginTop:10, fontSize:11, opacity:0.6, cursor:'pointer' }}>Hide</div>
    </div>
  );
}

Object.assign(window, { PrototypeApp });
