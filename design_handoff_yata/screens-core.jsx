// YATA — core screens: rich TaskRow, Today, Projects, Project detail

// ── Rich task row (assignees, tags, recurrence, priority) ──
function TaskRow({ task, state, nav, actions, showList = true }) {
  const list = findList(state, task.listId);
  const lc = list ? M3[list.color] : M3.primary;
  const people = (task.assigneeIds||[]).map(id => findPerson(state, id)).filter(Boolean);
  const tags = (task.tagIds||[]).map(id => findTag(state, id)).filter(Boolean);
  return (
    <div style={{ display:'flex', alignItems:'flex-start', gap:13, padding:'10px 20px', minHeight:52 }}>
      <Press scale={0.85} onClick={(e)=>{ e.stopPropagation&&e.stopPropagation(); actions.toggleDone(task.id); }} style={{ paddingTop:2 }}>
        <Check checked={task.done} color={lc} />
      </Press>
      <Press onClick={()=>nav.push('taskDetail',{ taskId:task.id })} style={{ flex:1, minWidth:0 }}>
        <div style={{ display:'flex', alignItems:'center', gap:8 }}>
          <div style={{ ...TYPE.body, fontSize:16, lineHeight:'21px', flex:1, minWidth:0,
            color: task.done ? M3.onSurfaceVariant : M3.onSurface,
            textDecoration: task.done ? 'line-through' : 'none', opacity: task.done ? 0.65 : 1,
            transition:`opacity ${DUR.fade}ms, color ${DUR.fade}ms`,
            whiteSpace:'nowrap', overflow:'hidden', textOverflow:'ellipsis' }}>
            {task.title}
          </div>
          {task.flag && <Icon name="flag" size={16} color={M3.error} />}
          <PriorityBars priority={task.priority} />
        </div>
        {(task.time || (showList&&list) || task.recurrence || tags.length) && (
          <div style={{ display:'flex', alignItems:'center', gap:8, marginTop:5, flexWrap:'wrap' }}>
            {task.time && (
              <span style={{ display:'inline-flex', alignItems:'center', gap:4, ...TYPE.label, fontSize:12, color:M3.onSurfaceVariant }}>
                <Icon name="clock" size={12} color={M3.onSurfaceVariant} />{task.time}
              </span>
            )}
            {showList && list && (
              <span style={{ display:'inline-flex', alignItems:'center', gap:4, ...TYPE.label, fontSize:12, color:M3.onSurfaceVariant }}>
                <span style={{ width:7, height:7, borderRadius:'50%', background:lc }} />{list.name}
              </span>
            )}
            {task.recurrence && <RecurrenceBadge recurrence={task.recurrence} compact />}
            {tags.slice(0,2).map(t => <TagChip key={t.id} tag={t} size="sm" />)}
          </div>
        )}
      </Press>
      {people.length > 0 && (
        <Press onClick={()=>nav.push('taskDetail',{ taskId:task.id })} style={{ paddingTop:2 }}>
          <AssigneeStack people={people} size={24} max={3} />
        </Press>
      )}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────
// TODAY
// ─────────────────────────────────────────────────────────────────
function PTToday({ state, nav, actions }) {
  const [filter, setFilter] = React.useState('all');
  const me = findPerson(state, 'me');
  let today = state.tasks;
  if (filter === 'priority') today = today.filter(t => t.priority==='high' || t.flag);
  else if (filter === 'mine') today = today.filter(t => (t.assigneeIds||[]).includes('me'));
  const dueCount = state.tasks.filter(t => !t.done).length;
  const prog = progressOf(state.tasks);
  const sections = ['Morning','Afternoon'];
  return (
    <div style={{ display:'flex', flexDirection:'column', height:'100%' }}>
      <div style={{ padding:'8px 20px 10px', flexShrink:0 }}>
        <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center' }}>
          <Press onClick={()=>nav.openDrawer()}><IconBtn name="menu" color={M3.onSurface} /></Press>
          <div style={{ display:'flex', alignItems:'center' }}>
            <Press onClick={()=>nav.push('search')}><IconBtn name="search" color={M3.onSurface} /></Press>
            <Press onClick={()=>nav.push('settings')}><div style={{ padding:4 }}><PersonAvatar person={me} size={32} /></div></Press>
          </div>
        </div>
        <div style={{ display:'flex', alignItems:'center', gap:14, padding:'10px 4px 0' }}>
          <div style={{ flex:1 }}>
            <div style={{ ...TYPE.label, fontSize:13, color:M3.primary, fontWeight:600, textTransform:'uppercase', letterSpacing:'0.08em' }}>
              Thursday · May 16
            </div>
            <div style={{ ...TYPE.display, fontSize:26, color:M3.onSurface, marginTop:2, lineHeight:1.05 }}>
              {dueCount} to go
            </div>
          </div>
          <ProgressRing pct={prog.pct} size={56} stroke={6} color={M3.primary}>
            <span style={{ fontSize:13 }}>{Math.round(prog.pct*100)}%</span>
          </ProgressRing>
        </div>
      </div>
      <div style={{ padding:'2px 20px 6px', display:'flex', gap:8, overflowX:'auto', overflowY:'hidden', flexShrink:0, WebkitOverflowScrolling:'touch' }}>
        {[['all','All'],['mine','Assigned to me'],['priority','Priority']].map(([k,l]) => (
          <Press key={k} onClick={()=>setFilter(k)}><Chip label={l} selected={filter===k} tint={M3.secondaryContainer} /></Press>
        ))}
      </div>
      <div style={{ flex:1, overflowY:'auto', overflowX:'hidden' }}>
        {sections.map(sec => {
          const items = today.filter(t => t.section === sec);
          if (!items.length) return null;
          return (
            <React.Fragment key={sec}>
              <SectionHeader title={sec} count={items.length} />
              {items.map(t => <TaskRow key={t.id} task={t} state={state} nav={nav} actions={actions} />)}
            </React.Fragment>
          );
        })}
        {!today.length && <EmptyState icon="check2" title="Nothing here" sub="You're all caught up." />}
        <div style={{ height:110 }} />
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────
// PROJECTS  (overview with progress rings)
// ─────────────────────────────────────────────────────────────────
function PTProjects({ state, nav, actions }) {
  return (
    <div style={{ display:'flex', flexDirection:'column', height:'100%' }}>
      <TopBarLite title="Projects" nav={nav} trailing={[{icon:'search', action:()=>nav.push('search')}]} />
      <div style={{ flex:1, overflowY:'auto', padding:'0 20px' }}>
        <div style={{ ...TYPE.body, fontSize:14, color:M3.onSurfaceVariant, margin:'0 4px 14px' }}>
          {state.projects.length} projects · {state.lists.length} lists
        </div>
        <div style={{ display:'flex', flexDirection:'column', gap:12 }}>
          {state.projects.map(pr => {
            const tks = tasksInProject(state, pr.id);
            const prog = progressOf(tks);
            const col = M3[pr.color];
            const members = uniquePeople(state, tks);
            return (
              <Press key={pr.id} onClick={()=>nav.push('projectDetail',{ projectId:pr.id })}>
                <div style={{ background:M3.surfaceContainerLow, borderRadius:SHAPE.xl, padding:16,
                  border:`1px solid ${M3.outlineVariant}` }}>
                  <div style={{ display:'flex', alignItems:'center', gap:14 }}>
                    <div style={{ width:44, height:44, borderRadius:SHAPE.md, background:withAlpha(col,0.2),
                      display:'flex', alignItems:'center', justifyContent:'center' }}>
                      <Icon name={pr.icon} size={22} color={col} />
                    </div>
                    <div style={{ flex:1, minWidth:0 }}>
                      <div style={{ ...TYPE.title, fontSize:17, color:M3.onSurface }}>{pr.name}</div>
                      <div style={{ ...TYPE.label, fontSize:12, color:M3.onSurfaceVariant, marginTop:2 }}>
                        {pr.listIds.length} lists · {prog.done}/{prog.total} done
                      </div>
                    </div>
                    <ProgressRing pct={prog.pct} size={46} stroke={5} color={col} />
                  </div>
                  <div style={{ display:'flex', alignItems:'center', justifyContent:'space-between', marginTop:12 }}>
                    <div style={{ display:'flex', gap:6 }}>
                      {pr.listIds.map(lid => { const l=findList(state,lid); return (
                        <span key={lid} style={{ width:10, height:10, borderRadius:3, background:M3[l.color] }} />
                      ); })}
                    </div>
                    <AssigneeStack people={members} size={24} max={4} />
                  </div>
                </div>
              </Press>
            );
          })}
        </div>
        <Press onClick={()=>nav.openSheet('projectEditor')}>
          <div style={{ marginTop:12, height:52, borderRadius:SHAPE.lg, border:`1.5px dashed ${M3.outlineVariant}`,
            display:'flex', alignItems:'center', justifyContent:'center', gap:8, ...TYPE.label, fontSize:14, fontWeight:600, color:M3.primary }}>
            <Icon name="add" size={18} color={M3.primary} /> New project
          </div>
        </Press>
        <div style={{ height:100 }} />
      </div>
    </div>
  );
}

// ── Project detail ──
function PTProjectDetail({ state, nav, actions, params }) {
  const pr = findProject(state, params.projectId);
  if (!pr) return null;
  const col = M3[pr.color];
  const tks = tasksInProject(state, pr.id);
  const prog = progressOf(tks);
  const members = uniquePeople(state, tks);
  return (
    <div style={{ display:'flex', flexDirection:'column', height:'100%', background:M3.surface }}>
      <div style={{ background:withAlpha(col,0.16), padding:'8px 4px 20px', flexShrink:0 }}>
        <div style={{ display:'flex', alignItems:'center', padding:'0 4px' }}>
          <Press onClick={()=>nav.pop()}><IconBtn name="back" color={M3.onSurface} /></Press>
          <div style={{ flex:1 }} />
          <IconBtn name="more" color={M3.onSurface} />
        </div>
        <div style={{ padding:'8px 20px 0', display:'flex', gap:16, alignItems:'center' }}>
          <ProgressRing pct={prog.pct} size={72} stroke={7} color={col}>
            <span style={{ fontSize:18 }}>{Math.round(prog.pct*100)}%</span>
          </ProgressRing>
          <div style={{ flex:1 }}>
            <div style={{ ...TYPE.display, fontSize:26, color:M3.onSurface, lineHeight:1.05 }}>{pr.name}</div>
            <div style={{ ...TYPE.label, fontSize:13, color:M3.onSurfaceVariant, marginTop:4 }}>{prog.done} of {prog.total} tasks done</div>
            <div style={{ marginTop:8 }}><AssigneeStack people={members} size={26} max={5} /></div>
          </div>
        </div>
      </div>
      <div style={{ flex:1, overflowY:'auto' }}>
        {pr.listIds.map(lid => {
          const list = findList(state, lid);
          const litems = state.tasks.filter(t => t.listId === lid);
          const lp = progressOf(litems);
          return (
            <div key={lid} style={{ marginTop:8 }}>
              <Press onClick={()=>nav.push('listDetail',{ listId:lid })}>
                <div style={{ display:'flex', alignItems:'center', gap:10, padding:'8px 20px 6px' }}>
                  <span style={{ width:12, height:12, borderRadius:4, background:M3[list.color] }} />
                  <span style={{ ...TYPE.title, fontSize:15, color:M3.onSurface, flex:1 }}>{list.name}</span>
                  <span style={{ ...TYPE.label, fontSize:12, color:M3.onSurfaceVariant }}>{lp.done}/{lp.total}</span>
                  <Icon name="chevron" size={16} color={M3.onSurfaceVariant} />
                </div>
              </Press>
              {litems.slice(0,4).map(t => <TaskRow key={t.id} task={t} state={state} nav={nav} actions={actions} showList={false} />)}
            </div>
          );
        })}
        <div style={{ height:120 }} />
      </div>
      <div style={{ position:'absolute', bottom:24, right:20 }}>
        <Press onClick={()=>nav.openSheet('newTask',{ listId:pr.listIds[0], projectId:pr.id })}>
          <FAB label="Add task" icon="add" extended size="md" color="primary" />
        </Press>
      </div>
    </div>
  );
}

// ── small shared bits ──
function uniquePeople(state, tasks) {
  const ids = new Set();
  tasks.forEach(t => (t.assigneeIds||[]).forEach(id => ids.add(id)));
  return [...ids].map(id => findPerson(state, id)).filter(Boolean);
}
function EmptyState({ icon, title, sub }) {
  return (
    <div style={{ padding:'56px 32px', textAlign:'center' }}>
      <div style={{ width:64, height:64, borderRadius:'50%', background:M3.surfaceContainerHigh,
        display:'flex', alignItems:'center', justifyContent:'center', margin:'0 auto 16px' }}>
        <Icon name={icon} size={30} color={M3.onSurfaceVariant} />
      </div>
      <div style={{ ...TYPE.title, fontSize:17, color:M3.onSurface }}>{title}</div>
      <div style={{ ...TYPE.body, fontSize:14, color:M3.onSurfaceVariant, marginTop:4 }}>{sub}</div>
    </div>
  );
}
function TopBarLite({ title, nav, leading, trailing = [] }) {
  return (
    <div style={{ padding:'4px 4px', flexShrink:0 }}>
      <div style={{ height:56, display:'flex', alignItems:'center', padding:'0 4px', gap:2 }}>
        {leading
          ? <Press onClick={leading.action}><IconBtn name={leading.icon} color={M3.onSurface} /></Press>
          : <Press onClick={()=>nav.openDrawer()}><IconBtn name="menu" color={M3.onSurface} /></Press>}
        <div style={{ flex:1, paddingLeft:4, ...TYPE.title, fontSize:22, color:M3.onSurface }}>{title}</div>
        {trailing.map((t,i)=>(<Press key={i} onClick={t.action}><IconBtn name={t.icon} color={M3.onSurface} /></Press>))}
      </div>
    </div>
  );
}

Object.assign(window, { TaskRow, PTToday, PTProjects, PTProjectDetail, EmptyState, TopBarLite, uniquePeople });
