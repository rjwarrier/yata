// YATA — task detail, list detail, upcoming, search, settings, drawer

// ─────────────────────────────────────────────────────────────────
// TASK DETAIL (enhanced)
// ─────────────────────────────────────────────────────────────────
function PTTaskDetail({ state, nav, actions, params }) {
  const task = state.tasks.find(t => t.id === params.taskId) || state.tasks[3];
  const list = findList(state, task.listId);
  const lc = list ? M3[list.color] : M3.primary;
  const subs = task.subtasks || [];
  const subDone = subs.filter(s => s.done).length;
  const people = (task.assigneeIds||[]).map(id=>findPerson(state,id)).filter(Boolean);
  const tags = (task.tagIds||[]).map(id=>findTag(state,id)).filter(Boolean);
  return (
    <div style={{ display:'flex', flexDirection:'column', height:'100%', background:M3.surface }}>
      <div style={{ display:'flex', padding:'4px', alignItems:'center', flexShrink:0 }}>
        <Press onClick={()=>nav.pop()}><IconBtn name="back" color={M3.onSurface} /></Press>
        <div style={{ flex:1 }} />
        <Press onClick={()=>actions.toggleFlag(task.id)}><IconBtn name="flag" color={task.flag?M3.error:M3.onSurfaceVariant} /></Press>
        <IconBtn name="archive" color={M3.onSurface} />
        <IconBtn name="more" color={M3.onSurface} />
      </div>
      <div style={{ flex:1, overflowY:'auto', padding:'4px 20px' }}>
        <div style={{ display:'flex', gap:14, alignItems:'flex-start' }}>
          <Press scale={0.85} onClick={()=>actions.toggleDone(task.id)} style={{ paddingTop:5 }}>
            <Check checked={task.done} color={lc} size={28} />
          </Press>
          <div style={{ flex:1 }}>
            <div style={{ ...TYPE.display, fontSize:25, color:M3.onSurface, lineHeight:1.15,
              textDecoration: task.done?'line-through':'none', opacity: task.done?0.6:1, transition:`opacity ${DUR.fade}ms` }}>
              {task.title}
            </div>
          </div>
        </div>

        {/* Meta rows */}
        <div style={{ marginTop:18, display:'flex', flexDirection:'column', gap:8 }}>
          <MetaRow icon="calendar" label="Due" value={task.time ? `Today · ${task.time}` : 'Today'} accent={M3.primary} />
          <MetaRow icon="bell" label="Reminder" value={task.reminder || 'None'} />
          <Press onClick={()=>nav.openSheet('recurrence',{ taskId:task.id })}>
            <MetaRow icon="repeat" label="Repeats" value={recurrenceSummary(task.recurrence)} accent={task.recurrence?M3.tertiary:null} chevron />
          </Press>
          <Press onClick={()=>nav.openSheet('listPicker',{ taskId:task.id })}>
            <MetaRow icon="folder" label="List" value={list?`${findProject(state,list.projectId)?.name} · ${list.name}`:'Inbox'} swatch={lc} chevron />
          </Press>
          <Press onClick={()=>actions.cyclePriority(task.id)}>
            <MetaRow icon="flag" label="Priority" value={PRIORITY[task.priority||'none'].label} accent={priorityColor(task.priority)} rightEl={<PriorityBars priority={task.priority} size={16} />} />
          </Press>
        </div>

        {/* Assignees */}
        <SectionMini title="Assigned to" action="Manage" onAction={()=>nav.openSheet('assignee',{ taskId:task.id })} />
        <div style={{ display:'flex', flexWrap:'wrap', gap:8 }}>
          {people.map(p => (
            <div key={p.id} style={{ display:'inline-flex', alignItems:'center', gap:8, height:36, padding:'0 12px 0 4px',
              background:M3.surfaceContainerLow, borderRadius:SHAPE.full }}>
              <PersonAvatar person={p} size={28} />
              <span style={{ ...TYPE.label, fontSize:13, fontWeight:600, color:M3.onSurface }}>{p.me?'You':p.name.split(' ')[0]}</span>
            </div>
          ))}
          <Press onClick={()=>nav.openSheet('assignee',{ taskId:task.id })}>
            <div style={{ height:36, width:36, borderRadius:'50%', border:`1.5px dashed ${M3.outlineVariant}`,
              display:'flex', alignItems:'center', justifyContent:'center' }}>
              <Icon name="personAdd" size={18} color={M3.primary} />
            </div>
          </Press>
        </div>

        {/* Tags */}
        <SectionMini title="Tags" action="Edit" onAction={()=>nav.openSheet('tagPicker',{ taskId:task.id })} />
        <div style={{ display:'flex', flexWrap:'wrap', gap:8 }}>
          {tags.map(t => <TagChip key={t.id} tag={t} onClick={()=>nav.push('tagDetail',{tagId:t.id})} />)}
          <Press onClick={()=>nav.openSheet('tagPicker',{ taskId:task.id })}>
            <div style={{ height:30, padding:'0 12px', borderRadius:SHAPE.full, border:`1.5px dashed ${M3.outlineVariant}`,
              display:'inline-flex', alignItems:'center', gap:5, ...TYPE.label, fontSize:13, fontWeight:600, color:M3.primary }}>
              <Icon name="add" size={14} color={M3.primary} /> tag
            </div>
          </Press>
        </div>

        {/* Subtasks */}
        {subs.length>0 && (
          <div style={{ marginTop:20 }}>
            <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center', marginBottom:8 }}>
              <div style={{ ...TYPE.label, fontSize:12, color:M3.onSurfaceVariant, fontWeight:700, textTransform:'uppercase', letterSpacing:'0.08em' }}>Subtasks · {subDone}/{subs.length}</div>
              <span style={{ ...TYPE.label, fontSize:13, color:M3.primary, fontWeight:600 }}>+ add</span>
            </div>
            <div style={{ height:4, borderRadius:2, background:M3.surfaceContainerHigh, overflow:'hidden', marginBottom:10 }}>
              <div style={{ width:`${(subDone/subs.length)*100}%`, height:'100%', background:lc, transition:`width ${DUR.nav}ms ${EASE.emphDecel}` }} />
            </div>
            {subs.map(s => (
              <Press key={s.id} onClick={()=>actions.toggleSubtask(task.id,s.id)}>
                <div style={{ display:'flex', alignItems:'center', gap:12, padding:'7px 0' }}>
                  <Check checked={s.done} color={lc} size={20} />
                  <div style={{ ...TYPE.body, fontSize:14, color:s.done?M3.onSurfaceVariant:M3.onSurface,
                    textDecoration:s.done?'line-through':'none', transition:`all ${DUR.fade}ms` }}>{s.title}</div>
                </div>
              </Press>
            ))}
          </div>
        )}

        {task.notes && (
          <div style={{ marginTop:16, background:M3.surfaceContainerLow, borderRadius:SHAPE.lg, padding:'12px 14px' }}>
            <div style={{ ...TYPE.label, fontSize:12, color:M3.onSurfaceVariant, fontWeight:700, marginBottom:4 }}>Notes</div>
            <div style={{ ...TYPE.body, fontSize:14, color:M3.onSurface, lineHeight:1.5 }}>{task.notes}</div>
          </div>
        )}
        <div style={{ height:40 }} />
      </div>
    </div>
  );
}

function MetaRow({ icon, label, value, accent, swatch, chevron, rightEl }) {
  return (
    <div style={{ display:'flex', alignItems:'center', gap:14, padding:'10px 12px', background:M3.surfaceContainerLow, borderRadius:SHAPE.md }}>
      <div style={{ width:32, height:32, borderRadius:SHAPE.xs, background:M3.surfaceContainerHigh, display:'flex', alignItems:'center', justifyContent:'center' }}>
        <Icon name={icon} size={18} color={accent || M3.onSurfaceVariant} />
      </div>
      <div style={{ flex:1, minWidth:0 }}>
        <div style={{ ...TYPE.label, fontSize:11, color:M3.onSurfaceVariant, textTransform:'uppercase', letterSpacing:'0.06em' }}>{label}</div>
        <div style={{ ...TYPE.body, fontSize:15, color:accent||M3.onSurface, fontWeight:500, display:'flex', alignItems:'center', gap:6,
          whiteSpace:'nowrap', overflow:'hidden', textOverflow:'ellipsis' }}>
          {swatch && <span style={{ width:10, height:10, borderRadius:'50%', background:swatch, flexShrink:0 }} />}
          {value}
        </div>
      </div>
      {rightEl}
      {chevron && <Icon name="chevron" size={16} color={M3.onSurfaceVariant} />}
    </div>
  );
}
function SectionMini({ title, action, onAction }) {
  return (
    <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center', margin:'20px 0 10px' }}>
      <div style={{ ...TYPE.label, fontSize:12, color:M3.onSurfaceVariant, fontWeight:700, textTransform:'uppercase', letterSpacing:'0.08em' }}>{title}</div>
      {action && <Press onClick={onAction}><span style={{ ...TYPE.label, fontSize:13, color:M3.primary, fontWeight:600 }}>{action}</span></Press>}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────
// LIST DETAIL
// ─────────────────────────────────────────────────────────────────
function PTListDetail({ state, nav, params, actions }) {
  const special = params.listId === 'priority' || params.listId === 'all';
  const list = findList(state, params.listId) || { name: params.listId==='priority'?'Priority':'All tasks', color:'accentC', icon: params.listId==='priority'?'flag':'list' };
  const col = M3[list.color] || M3.primary;
  const tks = special
    ? (params.listId==='priority' ? state.tasks.filter(t=>t.priority==='high'||t.flag) : state.tasks)
    : state.tasks.filter(t => t.listId === params.listId);
  const prog = progressOf(tks);
  const project = list.projectId ? findProject(state, list.projectId) : null;
  return (
    <div style={{ display:'flex', flexDirection:'column', height:'100%', background:M3.surface }}>
      <div style={{ background:withAlpha(col,0.18), padding:'8px 4px 20px', flexShrink:0 }}>
        <div style={{ display:'flex', alignItems:'center', padding:'0 4px' }}>
          <Press onClick={()=>nav.pop()}><IconBtn name="back" color={M3.onSurface} /></Press>
          <div style={{ flex:1 }} />
          <IconBtn name="sort" color={M3.onSurface} />
          <IconBtn name="more" color={M3.onSurface} />
        </div>
        <div style={{ padding:'8px 20px 0', display:'flex', alignItems:'center', gap:14 }}>
          <div style={{ width:44, height:44, borderRadius:SHAPE.md, background:withAlpha(col,0.3), display:'flex', alignItems:'center', justifyContent:'center' }}>
            <Icon name={list.icon} size={22} color={col} />
          </div>
          <div style={{ flex:1 }}>
            {project && <div style={{ ...TYPE.label, fontSize:12, color:M3.onSurfaceVariant, fontWeight:600 }}>{project.name}</div>}
            <div style={{ ...TYPE.display, fontSize:28, color:M3.onSurface, lineHeight:1 }}>{list.name}</div>
          </div>
          <ProgressRing pct={prog.pct} size={48} stroke={5} color={col} />
        </div>
      </div>
      <div style={{ flex:1, overflowY:'auto' }}>
        <SectionHeader title="Tasks" count={tks.length} />
        {tks.map(t => <TaskRow key={t.id} task={t} state={state} nav={nav} actions={actions} showList={special} />)}
        {!tks.length && <EmptyState icon="check2" title="No tasks" sub="Add one with the button below." />}
        <div style={{ height:120 }} />
      </div>
      {!special && (
        <div style={{ position:'absolute', bottom:24, right:20 }}>
          <Press onClick={()=>nav.openSheet('newTask',{ listId:list.id })}>
            <FAB label={`Add task`} icon="add" extended size="md" color="primary" />
          </Press>
        </div>
      )}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────
// UPCOMING — agenda / calendar
// ─────────────────────────────────────────────────────────────────
function PTUpcoming({ state, nav, actions }) {
  const [selected, setSelected] = React.useState(0);
  const days = [
    { n:16, d:'Thu', tasks: state.tasks.filter(t=>!t.done).slice(0,6) },
    { n:17, d:'Fri', tasks: state.tasks.slice(2,5) },
    { n:18, d:'Sat', tasks: state.tasks.slice(4,6) },
    { n:19, d:'Sun', tasks: state.tasks.slice(5,7) },
    { n:20, d:'Mon', tasks: state.tasks.slice(0,3) },
    { n:21, d:'Tue', tasks: state.tasks.slice(3,5) },
    { n:22, d:'Wed', tasks: state.tasks.slice(6,8) },
  ];
  const day = days[selected];
  return (
    <div style={{ display:'flex', flexDirection:'column', height:'100%' }}>
      <TopBarLite title="Upcoming" nav={nav} trailing={[{icon:'calendar', action:()=>{}}]} />
      <div style={{ padding:'0 12px 14px', flexShrink:0 }}>
        <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center', padding:'0 12px 10px' }}>
          <div style={{ ...TYPE.title, fontSize:16, color:M3.onSurface }}>May 2026</div>
          <Icon name="chevronDown" size={18} color={M3.onSurfaceVariant} />
        </div>
        <div style={{ display:'flex', gap:4, justifyContent:'space-between' }}>
          {days.map((d,i) => {
            const active = i===selected;
            const has = d.tasks.length>0;
            return (
              <Press key={i} onClick={()=>setSelected(i)} style={{ flex:1 }}>
                <div style={{ padding:'9px 0', borderRadius:SHAPE.lg, background: active?M3.primary:'transparent',
                  display:'flex', flexDirection:'column', alignItems:'center', gap:3, transition:`background ${DUR.fade}ms` }}>
                  <div style={{ ...TYPE.label, fontSize:11, color:active?M3.onPrimary:M3.onSurfaceVariant, textTransform:'uppercase' }}>{d.d}</div>
                  <div style={{ ...TYPE.title, fontSize:17, color:active?M3.onPrimary:M3.onSurface }}>{d.n}</div>
                  <div style={{ width:4, height:4, borderRadius:'50%', background: has ? (active?M3.onPrimary:M3.primary) : 'transparent' }} />
                </div>
              </Press>
            );
          })}
        </div>
      </div>
      <div style={{ flex:1, overflowY:'auto' }}>
        <div style={{ ...TYPE.label, fontSize:13, color:M3.primary, fontWeight:600, padding:'2px 20px 4px', textTransform:'uppercase', letterSpacing:'0.06em' }}>
          {DAY_FULL[{Thu:'TH',Fri:'FR',Sat:'SA',Sun:'SU',Mon:'MO',Tue:'TU',Wed:'WE'}[day.d]]} · May {day.n}
        </div>
        {day.tasks.map(t => <TaskRow key={t.id} task={t} state={state} nav={nav} actions={actions} />)}
        {!day.tasks.length && <EmptyState icon="calendar" title="Clear day" sub="Nothing scheduled." />}
        <div style={{ height:110 }} />
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────
// SEARCH
// ─────────────────────────────────────────────────────────────────
function PTSearch({ state, nav, actions }) {
  const [q, setQ] = React.useState('');
  const inputRef = React.useRef();
  React.useEffect(() => { setTimeout(()=>inputRef.current?.focus(), 300); }, []);
  const lower = q.trim().toLowerCase();
  const matches = lower ? state.tasks.filter(t => t.title.toLowerCase().includes(lower)) : [];
  return (
    <div style={{ display:'flex', flexDirection:'column', height:'100%', background:M3.surface }}>
      <div style={{ padding:'8px 12px 12px', flexShrink:0 }}>
        <div style={{ height:52, background:M3.surfaceContainerHigh, borderRadius:SHAPE.full, display:'flex', alignItems:'center', padding:'0 10px 0 16px', gap:10 }}>
          <Press onClick={()=>nav.pop()}><Icon name="back" size={22} color={M3.onSurfaceVariant} /></Press>
          <input ref={inputRef} value={q} onChange={e=>setQ(e.target.value)} placeholder="Search tasks, people, tags"
            style={{ flex:1, border:'none', background:'transparent', ...TYPE.body, fontSize:16, color:M3.onSurface }} />
          {q && <Press onClick={()=>setQ('')}><Icon name="close" size={20} color={M3.onSurfaceVariant} /></Press>}
        </div>
      </div>
      {q.trim() ? (
        <div style={{ flex:1, overflowY:'auto' }}>
          <SectionHeader title="Tasks" count={matches.length} />
          {matches.map(r => <TaskRow key={r.id} task={r} state={state} nav={nav} actions={actions} />)}
          {!matches.length && <EmptyState icon="search" title="No matches" sub={`Nothing for "${q}"`} />}
        </div>
      ) : (
        <div style={{ flex:1, padding:'0 20px' }}>
          <div style={{ ...TYPE.label, fontSize:12, color:M3.onSurfaceVariant, fontWeight:700, textTransform:'uppercase', letterSpacing:'0.08em', margin:'4px 4px 10px' }}>Recent</div>
          {['Q2 roadmap','release notes','onboarding'].map((r,i)=>(
            <Press key={i} onClick={()=>setQ(r)}>
              <div style={{ display:'flex', alignItems:'center', gap:12, padding:'12px 4px' }}>
                <Icon name="clock" size={18} color={M3.onSurfaceVariant} />
                <div style={{ ...TYPE.body, fontSize:15, color:M3.onSurface }}>{r}</div>
              </div>
            </Press>
          ))}
        </div>
      )}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────
// SETTINGS
// ─────────────────────────────────────────────────────────────────
function PTSettings({ state, nav, theme, setTheme }) {
  const me = findPerson(state, 'me');
  return (
    <div style={{ display:'flex', flexDirection:'column', height:'100%', background:M3.surface }}>
      <div style={{ padding:'4px', display:'flex', alignItems:'center', flexShrink:0 }}>
        <Press onClick={()=>nav.pop()}><IconBtn name="back" color={M3.onSurface} /></Press>
        <div style={{ ...TYPE.title, fontSize:20, color:M3.onSurface, flex:1, paddingLeft:4 }}>Settings</div>
      </div>
      <div style={{ flex:1, overflowY:'auto', padding:'0 20px' }}>
        <div style={{ background:M3.tertiaryContainer, borderRadius:SHAPE.xl, padding:'18px', display:'flex', alignItems:'center', gap:14, marginBottom:20 }}>
          <PersonAvatar person={me} size={52} />
          <div style={{ flex:1 }}>
            <div style={{ ...TYPE.title, fontSize:17, color:M3.onTertiaryContainer }}>{me.name}</div>
            <div style={{ ...TYPE.label, fontSize:12, color:M3.onTertiaryContainer, opacity:0.85 }}>Pro · 2-year streak · 1,402 done</div>
          </div>
        </div>

        {/* Appearance / theme */}
        <div style={{ ...TYPE.label, fontSize:12, color:M3.onSurfaceVariant, fontWeight:700, textTransform:'uppercase', letterSpacing:'0.08em', margin:'4px 4px 8px' }}>Appearance</div>
        <div style={{ background:M3.surfaceContainerLow, borderRadius:SHAPE.lg, padding:14, marginBottom:20 }}>
          <div style={{ ...TYPE.body, fontSize:15, fontWeight:500, color:M3.onSurface, marginBottom:10 }}>Theme</div>
          <Segmented value={theme} onChange={setTheme}
            options={[{value:'light',label:'Light',icon:'sun'},{value:'dark',label:'Dark',icon:'moon'}]} />
        </div>

        {/* Manage */}
        <div style={{ ...TYPE.label, fontSize:12, color:M3.onSurfaceVariant, fontWeight:700, textTransform:'uppercase', letterSpacing:'0.08em', margin:'4px 4px 8px' }}>Manage</div>
        <div style={{ background:M3.surfaceContainerLow, borderRadius:SHAPE.lg, overflow:'hidden', marginBottom:20 }}>
          {[
            { icon:'people', t:'People', s:`${state.people.length} people`, action:()=>{ nav.pop(); nav.goTab(2); } },
            { icon:'tag', t:'Tags', s:`${state.tags.length} tags`, action:()=>{ nav.pop(); nav.goTab(3); } },
            { icon:'layers', t:'Projects', s:`${state.projects.length} projects`, action:()=>{ nav.pop(); nav.goTab(1); } },
          ].map((r,j,arr)=>(
            <Press key={j} onClick={r.action}>
              <div style={{ display:'flex', alignItems:'center', gap:14, padding:'12px 14px', borderBottom: j<arr.length-1?`1px solid ${M3.outlineVariant}`:'none' }}>
                <div style={{ width:36, height:36, borderRadius:SHAPE.sm, background:M3.surfaceContainerHigh, display:'flex', alignItems:'center', justifyContent:'center' }}>
                  <Icon name={r.icon} size={18} color={M3.onSurfaceVariant} />
                </div>
                <div style={{ flex:1 }}>
                  <div style={{ ...TYPE.body, fontSize:15, color:M3.onSurface, fontWeight:500 }}>{r.t}</div>
                  <div style={{ ...TYPE.label, fontSize:12, color:M3.onSurfaceVariant, marginTop:2 }}>{r.s}</div>
                </div>
                <Icon name="chevron" size={16} color={M3.onSurfaceVariant} />
              </div>
            </Press>
          ))}
        </div>

        <div style={{ ...TYPE.label, fontSize:12, color:M3.onSurfaceVariant, fontWeight:700, textTransform:'uppercase', letterSpacing:'0.08em', margin:'4px 4px 8px' }}>Defaults</div>
        <div style={{ background:M3.surfaceContainerLow, borderRadius:SHAPE.lg, overflow:'hidden' }}>
          {[
            { icon:'folder', t:'Default list', s:'Work' },
            { icon:'bell', t:'Notifications', s:'Daily summary, 8:00 AM' },
            { icon:'repeat', t:'Start of week', s:'Monday' },
          ].map((r,j,arr)=>(
            <div key={j} style={{ display:'flex', alignItems:'center', gap:14, padding:'12px 14px', borderBottom: j<arr.length-1?`1px solid ${M3.outlineVariant}`:'none' }}>
              <div style={{ width:36, height:36, borderRadius:SHAPE.sm, background:M3.surfaceContainerHigh, display:'flex', alignItems:'center', justifyContent:'center' }}>
                <Icon name={r.icon} size={18} color={M3.onSurfaceVariant} />
              </div>
              <div style={{ flex:1 }}>
                <div style={{ ...TYPE.body, fontSize:15, color:M3.onSurface, fontWeight:500 }}>{r.t}</div>
                <div style={{ ...TYPE.label, fontSize:12, color:M3.onSurfaceVariant, marginTop:2 }}>{r.s}</div>
              </div>
              <Icon name="chevron" size={16} color={M3.onSurfaceVariant} />
            </div>
          ))}
        </div>
        <div style={{ height:40 }} />
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────
// DRAWER
// ─────────────────────────────────────────────────────────────────
function PTDrawer({ state, nav }) {
  const me = findPerson(state, 'me');
  const navItems = [
    { icon:'today', t:'Today', tab:0 },
    { icon:'layers', t:'Projects', tab:1 },
    { icon:'people', t:'People', tab:2 },
    { icon:'tag', t:'Tags', tab:3 },
    { icon:'upcoming', t:'Upcoming', tab:4 },
  ];
  return (
    <div style={{ width:'86%', height:'100%', background:M3.surface, display:'flex', flexDirection:'column',
      borderTopRightRadius:28, borderBottomRightRadius:28, overflow:'hidden' }}>
      <div style={{ padding:'24px 20px 14px' }}>
        <PersonAvatar person={me} size={44} />
        <div style={{ ...TYPE.title, fontSize:17, color:M3.onSurface, marginTop:12 }}>{me.name}</div>
        <div style={{ ...TYPE.label, fontSize:12, color:M3.onSurfaceVariant, marginTop:2 }}>mira@orbital.co</div>
      </div>
      <div style={{ flex:1, overflowY:'auto', padding:'0 12px' }}>
        {navItems.map((r,i)=>(
          <Press key={i} onClick={()=>{ nav.goTab(r.tab); nav.closeDrawer(); }}>
            <div style={{ display:'flex', alignItems:'center', gap:14, padding:'12px 16px', borderRadius:SHAPE.full, marginBottom:2 }}>
              <Icon name={r.icon} size={20} color={M3.onSurfaceVariant} />
              <div style={{ flex:1, ...TYPE.body, fontSize:15, color:M3.onSurface, fontWeight:500 }}>{r.t}</div>
            </div>
          </Press>
        ))}
        <div style={{ ...TYPE.label, fontSize:12, color:M3.onSurfaceVariant, fontWeight:700, textTransform:'uppercase', letterSpacing:'0.08em', padding:'18px 16px 8px' }}>Lists</div>
        {state.lists.map((l,i)=>(
          <Press key={i} onClick={()=>{ nav.push('listDetail',{ listId:l.id }); nav.closeDrawer(); }}>
            <div style={{ display:'flex', alignItems:'center', gap:14, padding:'11px 16px', borderRadius:SHAPE.full, marginBottom:2 }}>
              <div style={{ width:18, height:18, borderRadius:SHAPE.xs, background:M3[l.color] }} />
              <div style={{ flex:1, ...TYPE.body, fontSize:15, color:M3.onSurface, fontWeight:500 }}>{l.name}</div>
              <div style={{ ...TYPE.label, fontSize:12, color:M3.onSurfaceVariant }}>{state.tasks.filter(t=>t.listId===l.id).length}</div>
            </div>
          </Press>
        ))}
      </div>
      <div style={{ padding:'12px', borderTop:`1px solid ${M3.outlineVariant}` }}>
        <Press onClick={()=>{ nav.push('settings'); nav.closeDrawer(); }}>
          <div style={{ display:'flex', alignItems:'center', gap:14, padding:'12px 16px', borderRadius:SHAPE.full }}>
            <Icon name="settings" size={20} color={M3.onSurfaceVariant} />
            <div style={{ ...TYPE.body, fontSize:15, color:M3.onSurface, fontWeight:500 }}>Settings</div>
          </div>
        </Press>
      </div>
    </div>
  );
}

Object.assign(window, { PTTaskDetail, MetaRow, SectionMini, PTListDetail, PTUpcoming, PTSearch, PTSettings, PTDrawer });
