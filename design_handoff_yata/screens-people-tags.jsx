// YATA — People (create/assign, local) + Tags (browse/filter)

// ─────────────────────────────────────────────────────────────────
// PEOPLE  — manage local persons
// ─────────────────────────────────────────────────────────────────
function PTPeople({ state, nav, actions }) {
  return (
    <div style={{ display:'flex', flexDirection:'column', height:'100%' }}>
      <TopBarLite title="People" nav={nav} trailing={[{icon:'personAdd', action:()=>nav.openSheet('personEditor')}]} />
      <div style={{ flex:1, overflowY:'auto', padding:'0 20px' }}>
        <div style={{ ...TYPE.body, fontSize:14, color:M3.onSurfaceVariant, margin:'0 4px 14px', lineHeight:1.5 }}>
          People are stored on this device only. Create someone, then assign them to any task.
        </div>
        <div style={{ background:M3.surfaceContainerLow, borderRadius:SHAPE.xl, overflow:'hidden' }}>
          {state.people.map((p,i) => {
            const tks = tasksForPerson(state, p.id);
            const prog = progressOf(tks);
            return (
              <Press key={p.id} onClick={()=>nav.push('personDetail',{ personId:p.id })}>
                <div style={{ display:'flex', alignItems:'center', gap:14, padding:'12px 16px',
                  borderBottom: i<state.people.length-1 ? `1px solid ${M3.outlineVariant}` : 'none' }}>
                  <PersonAvatar person={p} size={44} />
                  <div style={{ flex:1, minWidth:0 }}>
                    <div style={{ ...TYPE.body, fontSize:15, fontWeight:600, color:M3.onSurface, display:'flex', alignItems:'center', gap:8 }}>
                      {p.name}
                      {p.me && <span style={{ ...TYPE.label, fontSize:10, fontWeight:700, color:M3.onTertiaryContainer, background:M3.tertiaryContainer, padding:'1px 7px', borderRadius:SHAPE.full }}>YOU</span>}
                    </div>
                    <div style={{ ...TYPE.label, fontSize:12, color:M3.onSurfaceVariant, marginTop:2 }}>
                      {prog.total} assigned · {prog.done} done
                    </div>
                  </div>
                  {prog.total > 0 && <ProgressRing pct={prog.pct} size={38} stroke={4} color={M3[p.color]} />}
                  <Icon name="chevron" size={16} color={M3.onSurfaceVariant} />
                </div>
              </Press>
            );
          })}
        </div>
        <Press onClick={()=>nav.openSheet('personEditor')}>
          <div style={{ marginTop:12, height:52, borderRadius:SHAPE.lg, border:`1.5px dashed ${M3.outlineVariant}`,
            display:'flex', alignItems:'center', justifyContent:'center', gap:8, ...TYPE.label, fontSize:14, fontWeight:600, color:M3.primary }}>
            <Icon name="personAdd" size={18} color={M3.primary} /> Add person
          </div>
        </Press>
        <div style={{ height:100 }} />
      </div>
    </div>
  );
}

// ── Person detail ──
function PTPersonDetail({ state, nav, actions, params }) {
  const p = findPerson(state, params.personId);
  if (!p) return null;
  const col = M3[p.color];
  const tks = tasksForPerson(state, p.id);
  const prog = progressOf(tks);
  const open = tks.filter(t=>!t.done);
  const done = tks.filter(t=>t.done);
  return (
    <div style={{ display:'flex', flexDirection:'column', height:'100%', background:M3.surface }}>
      <div style={{ background:withAlpha(col,0.16), padding:'8px 4px 22px', flexShrink:0 }}>
        <div style={{ display:'flex', alignItems:'center', padding:'0 4px' }}>
          <Press onClick={()=>nav.pop()}><IconBtn name="back" color={M3.onSurface} /></Press>
          <div style={{ flex:1 }} />
          {!p.me && <Press onClick={()=>nav.openSheet('personEditor',{ personId:p.id })}><IconBtn name="edit" color={M3.onSurface} /></Press>}
        </div>
        <div style={{ padding:'8px 20px 0', display:'flex', gap:16, alignItems:'center' }}>
          <PersonAvatar person={p} size={72} />
          <div style={{ flex:1 }}>
            <div style={{ ...TYPE.display, fontSize:26, color:M3.onSurface, lineHeight:1.05 }}>{p.name}</div>
            <div style={{ ...TYPE.label, fontSize:13, color:M3.onSurfaceVariant, marginTop:4 }}>
              {prog.total} tasks · {prog.done} done · {prog.total-prog.done} open
            </div>
          </div>
        </div>
      </div>
      <div style={{ flex:1, overflowY:'auto' }}>
        {open.length>0 && <><SectionHeader title="Open" count={open.length} />
          {open.map(t => <TaskRow key={t.id} task={t} state={state} nav={nav} actions={actions} />)}</>}
        {done.length>0 && <><SectionHeader title="Completed" count={done.length} />
          {done.map(t => <TaskRow key={t.id} task={t} state={state} nav={nav} actions={actions} />)}</>}
        {!tks.length && <EmptyState icon="user" title="No tasks yet" sub={`Assign ${p.name.split(' ')[0]} to a task to see it here.`} />}
        <div style={{ height:40 }} />
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────
// TAGS  — browse & filter
// ─────────────────────────────────────────────────────────────────
function PTTags({ state, nav, actions }) {
  return (
    <div style={{ display:'flex', flexDirection:'column', height:'100%' }}>
      <TopBarLite title="Tags" nav={nav} trailing={[{icon:'search', action:()=>nav.push('search')}]} />
      <div style={{ flex:1, overflowY:'auto', padding:'0 20px' }}>
        <div style={{ ...TYPE.body, fontSize:14, color:M3.onSurfaceVariant, margin:'0 4px 14px', lineHeight:1.5 }}>
          Tags cut across projects and lists. Tap one to see everything tagged.
        </div>
        <div style={{ display:'flex', flexWrap:'wrap', gap:8, marginBottom:8 }}>
          {state.tags.map(t => {
            const cnt = tasksForTag(state, t.id).length;
            const col = M3[t.color];
            return (
              <Press key={t.id} onClick={()=>nav.push('tagDetail',{ tagId:t.id })}>
                <div style={{ height:38, padding:'0 15px', borderRadius:SHAPE.full, background:withAlpha(col,0.16),
                  display:'inline-flex', alignItems:'center', gap:8, ...TYPE.label, fontSize:14, fontWeight:600, color:col }}>
                  <span style={{ width:8, height:8, borderRadius:'50%', background:col }} />
                  {t.name}
                  <span style={{ minWidth:20, height:20, padding:'0 5px', borderRadius:SHAPE.full, background:withAlpha(col,0.22),
                    display:'inline-flex', alignItems:'center', justifyContent:'center', fontSize:11, fontWeight:700 }}>{cnt}</span>
                </div>
              </Press>
            );
          })}
          <Press onClick={()=>nav.openSheet('tagEditor')}>
            <div style={{ height:38, padding:'0 15px', borderRadius:SHAPE.full, border:`1.5px dashed ${M3.outlineVariant}`,
              display:'inline-flex', alignItems:'center', gap:6, ...TYPE.label, fontSize:14, fontWeight:600, color:M3.primary }}>
              <Icon name="add" size={16} color={M3.primary} /> New tag
            </div>
          </Press>
        </div>
        <div style={{ height:100 }} />
      </div>
    </div>
  );
}

// ── Tag detail ──
function PTTagDetail({ state, nav, actions, params }) {
  const tag = findTag(state, params.tagId);
  if (!tag) return null;
  const col = M3[tag.color];
  const tks = tasksForTag(state, tag.id);
  const prog = progressOf(tks);
  return (
    <div style={{ display:'flex', flexDirection:'column', height:'100%', background:M3.surface }}>
      <div style={{ background:withAlpha(col,0.16), padding:'8px 4px 18px', flexShrink:0 }}>
        <div style={{ display:'flex', alignItems:'center', padding:'0 4px' }}>
          <Press onClick={()=>nav.pop()}><IconBtn name="back" color={M3.onSurface} /></Press>
          <div style={{ flex:1 }} />
          <IconBtn name="more" color={M3.onSurface} />
        </div>
        <div style={{ padding:'8px 20px 0', display:'flex', gap:12, alignItems:'center' }}>
          <div style={{ width:44, height:44, borderRadius:SHAPE.md, background:withAlpha(col,0.28),
            display:'flex', alignItems:'center', justifyContent:'center' }}>
            <Icon name="tag" size={22} color={col} />
          </div>
          <div>
            <div style={{ ...TYPE.display, fontSize:26, color:M3.onSurface, lineHeight:1 }}>{tag.name}</div>
            <div style={{ ...TYPE.label, fontSize:13, color:M3.onSurfaceVariant, marginTop:4 }}>{prog.total} tasks · {prog.done} done</div>
          </div>
        </div>
      </div>
      <div style={{ flex:1, overflowY:'auto' }}>
        <SectionHeader title="Tagged" count={tks.length} />
        {tks.map(t => <TaskRow key={t.id} task={t} state={state} nav={nav} actions={actions} />)}
        {!tks.length && <EmptyState icon="tag" title="No tasks" sub="Add this tag to a task from its detail view." />}
        <div style={{ height:40 }} />
      </div>
    </div>
  );
}

Object.assign(window, { PTPeople, PTPersonDetail, PTTags, PTTagDetail });
