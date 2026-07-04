// YATA — bottom sheets: new task, recurrence (RRULE), assignee, tag, list, person/project/tag editors

const SWATCHES = ['accentA','accentB','accentC','accentD','accentE','accentF','accentG','accentH'];

function SheetHandle() {
  return <div style={{ width:32, height:4, borderRadius:2, background:M3.outline, margin:'8px auto 14px', opacity:0.5 }} />;
}
function SheetHeader({ title, onDone, doneLabel = 'Done' }) {
  return (
    <div style={{ display:'flex', alignItems:'center', justifyContent:'space-between', padding:'0 20px 12px' }}>
      <div style={{ ...TYPE.title, fontSize:19, color:M3.onSurface }}>{title}</div>
      {onDone && <Press onClick={onDone}><span style={{ ...TYPE.label, fontSize:15, fontWeight:600, color:M3.primary }}>{doneLabel}</span></Press>}
    </div>
  );
}
function ColorPicker({ value, onChange }) {
  return (
    <div style={{ display:'flex', gap:10, flexWrap:'wrap' }}>
      {SWATCHES.map(c => (
        <Press key={c} onClick={()=>onChange(c)}>
          <div style={{ width:36, height:36, borderRadius:'50%', background:M3[c],
            boxShadow: value===c ? `0 0 0 3px ${M3.surface}, 0 0 0 5px ${M3[c]}` : 'none',
            display:'flex', alignItems:'center', justifyContent:'center' }}>
            {value===c && <Icon name="check" size={18} color={M3.onAccent} />}
          </div>
        </Press>
      ))}
    </div>
  );
}
function Stepper({ value, onChange, min = 1, max = 30 }) {
  return (
    <div style={{ display:'flex', alignItems:'center', gap:2, background:M3.surfaceContainerHigh, borderRadius:SHAPE.full, padding:3 }}>
      <Press onClick={()=>onChange(Math.max(min, value-1))}><div style={sbtn}><Icon name="close" size={0} color="transparent" /><span style={{ fontSize:20, fontWeight:600, color:M3.onSurface, lineHeight:1 }}>−</span></div></Press>
      <div style={{ minWidth:34, textAlign:'center', ...TYPE.title, fontSize:16, color:M3.onSurface }}>{value}</div>
      <Press onClick={()=>onChange(Math.min(max, value+1))}><div style={sbtn}><Icon name="add" size={18} color={M3.onSurface} /></div></Press>
    </div>
  );
}
const sbtn = { width:34, height:34, borderRadius:'50%', background:M3.surface, display:'flex', alignItems:'center', justifyContent:'center' };

// ─────────────────────────────────────────────────────────────────
// NEW TASK  (enhanced — all fields inline)
// ─────────────────────────────────────────────────────────────────
function PTNewTaskSheet({ state, nav, actions, params }) {
  const [title, setTitle] = React.useState('');
  const [panel, setPanel] = React.useState(null); // list|priority|people|tags|repeat
  const [listId, setListId] = React.useState(params?.listId || 'work');
  const [priority, setPriority] = React.useState('none');
  const [assignees, setAssignees] = React.useState(['me']);
  const [tagIds, setTagIds] = React.useState([]);
  const [rec, setRec] = React.useState(null);
  const inputRef = React.useRef();
  React.useEffect(()=>{ setTimeout(()=>inputRef.current?.focus(), 350); }, []);
  const list = findList(state, listId);
  const submit = () => {
    if (!title.trim()) return;
    actions.addTask({ title:title.trim(), listId, priority, assigneeIds:assignees, tagIds, recurrence:rec });
    nav.closeSheet();
  };
  const toggle = (arr,set,id) => set(arr.includes(id) ? arr.filter(x=>x!==id) : [...arr,id]);
  const chip = (active,label,icon,key,tint) => (
    <Press onClick={()=>setPanel(panel===key?null:key)}>
      <div style={{ height:34, padding:'0 12px', borderRadius:SHAPE.full,
        background: active?withAlpha(tint||M3.primary,0.18):'transparent', border:`1px solid ${active?'transparent':M3.outlineVariant}`,
        display:'inline-flex', alignItems:'center', gap:6, ...TYPE.label, fontSize:13, fontWeight:600,
        color: active?(tint||M3.primary):M3.onSurfaceVariant }}>
        <Icon name={icon} size={15} color={active?(tint||M3.primary):M3.onSurfaceVariant} />{label}
      </div>
    </Press>
  );
  return (
    <div style={{ paddingBottom:20, maxHeight:'82vh', display:'flex', flexDirection:'column' }}>
      <SheetHandle />
      <div style={{ padding:'0 20px', overflowY:'auto' }}>
        <input ref={inputRef} value={title} onChange={e=>setTitle(e.target.value)} onKeyDown={e=>e.key==='Enter'&&submit()}
          placeholder="What needs doing?"
          style={{ ...TYPE.display, fontSize:22, color:M3.onSurface, padding:'6px 2px 12px', width:'100%', border:'none', background:'transparent', borderBottom:`2px solid ${M3.primary}` }} />
        <div style={{ display:'flex', gap:8, flexWrap:'wrap', margin:'14px 0 4px' }}>
          {chip(true,'Today','calendar','date',M3.primary)}
          {chip(listId!=='work'||panel==='list', list?.name||'List','folder','list', list?M3[list.color]:M3.primary)}
          {chip(priority!=='none', PRIORITY[priority].label==='None'?'Priority':PRIORITY[priority].label,'flag','priority',priorityColor(priority))}
          {chip(assignees.length>0, assignees.length===1?'1 person':`${assignees.length} people`,'people','people',M3.tertiary)}
          {chip(tagIds.length>0, tagIds.length?`${tagIds.length} tags`:'Tags','tag','tags',M3.accentF)}
          {chip(!!rec, rec?'Repeats':'Repeat','repeat','repeat',M3.tertiary)}
        </div>

        {/* Inline panels */}
        {panel==='list' && (
          <Panel>
            {state.projects.map(pr => (
              <div key={pr.id} style={{ marginBottom:8 }}>
                <div style={{ ...TYPE.label, fontSize:11, color:M3.onSurfaceVariant, fontWeight:700, textTransform:'uppercase', margin:'2px 2px 6px' }}>{pr.name}</div>
                <div style={{ display:'flex', flexWrap:'wrap', gap:8 }}>
                  {pr.listIds.map(lid => { const l=findList(state,lid); const on=listId===lid; return (
                    <Press key={lid} onClick={()=>setListId(lid)}>
                      <div style={{ height:34, padding:'0 12px', borderRadius:SHAPE.full, background:on?withAlpha(M3[l.color],0.2):M3.surfaceContainerHigh,
                        display:'inline-flex', alignItems:'center', gap:7, ...TYPE.label, fontSize:13, fontWeight:600, color:on?M3[l.color]:M3.onSurface }}>
                        <span style={{ width:9, height:9, borderRadius:'50%', background:M3[l.color] }} />{l.name}
                        {on && <Icon name="check" size={14} color={M3[l.color]} />}
                      </div>
                    </Press>
                  ); })}
                </div>
              </div>
            ))}
          </Panel>
        )}
        {panel==='priority' && (
          <Panel><Segmented value={priority} onChange={setPriority}
            options={[{value:'none',label:'None'},{value:'low',label:'Low'},{value:'med',label:'Med'},{value:'high',label:'High'}]} /></Panel>
        )}
        {panel==='people' && (
          <Panel>
            <div style={{ display:'flex', flexWrap:'wrap', gap:8 }}>
              {state.people.map(p => { const on=assignees.includes(p.id); return (
                <Press key={p.id} onClick={()=>toggle(assignees,setAssignees,p.id)}>
                  <div style={{ height:38, padding:'0 12px 0 4px', borderRadius:SHAPE.full, background:on?M3.tertiaryContainer:M3.surfaceContainerHigh,
                    display:'inline-flex', alignItems:'center', gap:8, ...TYPE.label, fontSize:13, fontWeight:600, color:on?M3.onTertiaryContainer:M3.onSurface }}>
                    <PersonAvatar person={p} size={30} />{p.me?'You':p.name.split(' ')[0]}
                    {on && <Icon name="check" size={15} color={M3.onTertiaryContainer} />}
                  </div>
                </Press>
              ); })}
            </div>
          </Panel>
        )}
        {panel==='tags' && (
          <Panel>
            <div style={{ display:'flex', flexWrap:'wrap', gap:8 }}>
              {state.tags.map(t => { const on=tagIds.includes(t.id); const col=M3[t.color]; return (
                <Press key={t.id} onClick={()=>toggle(tagIds,setTagIds,t.id)}>
                  <div style={{ height:32, padding:'0 12px', borderRadius:SHAPE.full, background:on?withAlpha(col,0.2):M3.surfaceContainerHigh,
                    display:'inline-flex', alignItems:'center', gap:6, ...TYPE.label, fontSize:13, fontWeight:600, color:on?col:M3.onSurface }}>
                    <span style={{ width:7, height:7, borderRadius:'50%', background:col }} />{t.name}
                    {on && <Icon name="check" size={13} color={col} />}
                  </div>
                </Press>
              ); })}
            </div>
          </Panel>
        )}
        {panel==='repeat' && (
          <Panel>
            <div style={{ display:'flex', flexWrap:'wrap', gap:8 }}>
              {[['None',null],['Daily',{freq:'daily',interval:1,ends:{type:'never'}}],
                ['Weekdays',{freq:'weekly',interval:1,byday:['MO','TU','WE','TH','FR'],ends:{type:'never'}}],
                ['Weekly',{freq:'weekly',interval:1,byday:['TH'],ends:{type:'never'}}],
                ['Monthly',{freq:'monthly',interval:1,bymonthday:16,ends:{type:'never'}}]].map(([lab,r]) => {
                const on = recurrenceSummary(rec)===recurrenceSummary(r);
                return (
                  <Press key={lab} onClick={()=>setRec(r)}>
                    <div style={{ height:34, padding:'0 14px', borderRadius:SHAPE.full, background:on?M3.tertiaryContainer:M3.surfaceContainerHigh,
                      ...TYPE.label, fontSize:13, fontWeight:600, color:on?M3.onTertiaryContainer:M3.onSurface, display:'inline-flex', alignItems:'center' }}>{lab}</div>
                  </Press>
                );
              })}
            </div>
            <div style={{ ...TYPE.label, fontSize:12, color:M3.onSurfaceVariant, marginTop:10 }}>For custom rules, open a task → Repeats.</div>
          </Panel>
        )}
      </div>
      <div style={{ display:'flex', alignItems:'center', gap:2, borderTop:`1px solid ${M3.outlineVariant}`, padding:'10px 16px 0', marginTop:8 }}>
        <span style={{ ...TYPE.label, fontSize:12, color:M3.onSurfaceVariant, flex:1, paddingLeft:6 }}>
          {list?`${list.name}`:''}{rec?` · ${recurrenceSummary(rec)}`:''}
        </span>
        <Press onClick={submit}><FAB icon="arrow" size="sm" color="primary" /></Press>
      </div>
    </div>
  );
}
function Panel({ children }) {
  return <div style={{ background:M3.surfaceContainerLow, borderRadius:SHAPE.lg, padding:14, marginBottom:8, animation:`yataSlideUp ${DUR.fade}ms ${EASE.emphDecel}` }}>{children}</div>;
}

// ─────────────────────────────────────────────────────────────────
// RECURRENCE — full RRULE builder
// ─────────────────────────────────────────────────────────────────
function PTRecurrenceSheet({ state, nav, actions, params }) {
  const task = state.tasks.find(t=>t.id===params.taskId);
  const [enabled, setEnabled] = React.useState(!!task?.recurrence);
  const [r, setR] = React.useState(task?.recurrence || { freq:'weekly', interval:1, byday:['TH'], ends:{type:'never'} });
  const upd = (patch) => setR(prev => ({ ...prev, ...patch }));
  const toggleDay = (d) => {
    const cur = r.byday || [];
    upd({ byday: cur.includes(d) ? cur.filter(x=>x!==d) : [...cur, d] });
  };
  const save = () => { actions.setRecurrence(params.taskId, enabled ? r : null); nav.closeSheet(); };
  const preview = enabled ? r : null;
  return (
    <div style={{ paddingBottom:24, maxHeight:'86vh', overflowY:'auto' }}>
      <SheetHandle />
      <SheetHeader title="Repeat" onDone={save} doneLabel="Save" />
      <div style={{ padding:'0 20px' }}>
        {/* summary banner */}
        <div style={{ background:M3.tertiaryContainer, borderRadius:SHAPE.lg, padding:'12px 14px', display:'flex', alignItems:'center', gap:10, marginBottom:16 }}>
          <Icon name="repeat" size={20} color={M3.onTertiaryContainer} />
          <div style={{ flex:1 }}>
            <div style={{ ...TYPE.body, fontSize:15, fontWeight:600, color:M3.onTertiaryContainer }}>{recurrenceSummary(preview)}</div>
            {enabled && <div style={{ ...TYPE.mono, fontSize:11, color:M3.onTertiaryContainer, opacity:0.7, marginTop:2 }}>{toRRULE(r)}</div>}
          </div>
          <Toggle on={enabled} onClick={()=>setEnabled(!enabled)} />
        </div>

        {enabled && (<>
          <FieldLabel>Frequency</FieldLabel>
          <Segmented value={r.freq} onChange={(f)=>upd({ freq:f })}
            options={[{value:'daily',label:'Day'},{value:'weekly',label:'Week'},{value:'monthly',label:'Month'},{value:'yearly',label:'Year'}]} />

          <div style={{ display:'flex', alignItems:'center', justifyContent:'space-between', margin:'18px 0 0' }}>
            <FieldLabel nomargin>Every</FieldLabel>
            <Stepper value={r.interval||1} onChange={(v)=>upd({ interval:v })} />
          </div>

          {r.freq==='weekly' && (<>
            <FieldLabel>On days</FieldLabel>
            <div style={{ display:'flex', gap:6, justifyContent:'space-between' }}>
              {DAY_ORDER.map(d => { const on=(r.byday||[]).includes(d); return (
                <Press key={d} onClick={()=>toggleDay(d)} style={{ flex:1 }}>
                  <div style={{ height:42, borderRadius:SHAPE.md, background:on?M3.primary:M3.surfaceContainerHigh,
                    display:'flex', alignItems:'center', justifyContent:'center', ...TYPE.label, fontSize:12, fontWeight:700,
                    color:on?M3.onPrimary:M3.onSurfaceVariant, transition:`background ${DUR.micro}ms` }}>
                    {DAY_LABEL[d][0]}
                  </div>
                </Press>
              ); })}
            </div>
          </>)}

          {r.freq==='monthly' && (<>
            <FieldLabel>Day of month</FieldLabel>
            <div style={{ display:'flex', alignItems:'center', gap:12 }}>
              <span style={{ ...TYPE.body, fontSize:15, color:M3.onSurface }}>On the {ordinal(r.bymonthday||16)}</span>
              <div style={{ flex:1 }} />
              <Stepper value={r.bymonthday||16} onChange={(v)=>upd({ bymonthday:v })} min={1} max={31} />
            </div>
          </>)}

          <FieldLabel>Ends</FieldLabel>
          <div style={{ display:'flex', flexDirection:'column', gap:8 }}>
            {[['never','Never'],['after','After a number of times'],['on','On a date']].map(([k,lab]) => {
              const on = (r.ends?.type||'never')===k;
              return (
                <Press key={k} onClick={()=>upd({ ends: k==='after'?{type:'after',count:r.ends?.count||10}:k==='on'?{type:'on',date:r.ends?.date||'2026-12-31'}:{type:'never'} })}>
                  <div style={{ display:'flex', alignItems:'center', gap:12, padding:'10px 12px', borderRadius:SHAPE.md,
                    background:on?withAlpha(M3.primary,0.12):M3.surfaceContainerLow, border:`1.5px solid ${on?M3.primary:'transparent'}` }}>
                    <div style={{ width:20, height:20, borderRadius:'50%', border:`2px solid ${on?M3.primary:M3.outline}`, display:'flex', alignItems:'center', justifyContent:'center' }}>
                      {on && <div style={{ width:10, height:10, borderRadius:'50%', background:M3.primary }} />}
                    </div>
                    <span style={{ flex:1, ...TYPE.body, fontSize:15, color:M3.onSurface }}>{lab}</span>
                    {on && k==='after' && <Stepper value={r.ends.count} onChange={(v)=>upd({ ends:{type:'after',count:v} })} min={1} max={99} />}
                    {on && k==='on' && <span style={{ ...TYPE.label, fontSize:13, color:M3.primary, fontWeight:600 }}>{r.ends.date}</span>}
                  </div>
                </Press>
              );
            })}
          </div>
        </>)}
        <div style={{ height:20 }} />
      </div>
    </div>
  );
}
function FieldLabel({ children, nomargin }) {
  return <div style={{ ...TYPE.label, fontSize:12, color:M3.onSurfaceVariant, fontWeight:700, textTransform:'uppercase', letterSpacing:'0.08em', margin: nomargin?0:'18px 0 10px' }}>{children}</div>;
}

// ─────────────────────────────────────────────────────────────────
// ASSIGNEE picker
// ─────────────────────────────────────────────────────────────────
function PTAssigneeSheet({ state, nav, actions, params }) {
  const task = state.tasks.find(t=>t.id===params.taskId);
  const assigned = task?.assigneeIds || [];
  return (
    <div style={{ paddingBottom:24, maxHeight:'80vh', overflowY:'auto' }}>
      <SheetHandle />
      <SheetHeader title="Assign people" onDone={()=>nav.closeSheet()} />
      <div style={{ padding:'0 12px' }}>
        {state.people.map(p => { const on = assigned.includes(p.id); return (
          <Press key={p.id} onClick={()=>actions.toggleAssignee(task.id, p.id)}>
            <div style={{ display:'flex', alignItems:'center', gap:14, padding:'10px 12px', borderRadius:SHAPE.md }}>
              <PersonAvatar person={p} size={40} />
              <div style={{ flex:1, ...TYPE.body, fontSize:15, fontWeight:600, color:M3.onSurface }}>{p.me?'You':p.name}</div>
              <div style={{ width:24, height:24, borderRadius:'50%', background:on?M3.primary:'transparent', border:on?'none':`2px solid ${M3.outline}`, display:'flex', alignItems:'center', justifyContent:'center' }}>
                {on && <Icon name="check" size={16} color={M3.onAccent} />}
              </div>
            </div>
          </Press>
        ); })}
        <Press onClick={()=>nav.openSheet('personEditor')}>
          <div style={{ display:'flex', alignItems:'center', gap:14, padding:'12px', marginTop:4 }}>
            <div style={{ width:40, height:40, borderRadius:'50%', border:`1.5px dashed ${M3.outlineVariant}`, display:'flex', alignItems:'center', justifyContent:'center' }}>
              <Icon name="personAdd" size={20} color={M3.primary} />
            </div>
            <div style={{ ...TYPE.body, fontSize:15, fontWeight:600, color:M3.primary }}>Create new person</div>
          </div>
        </Press>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────
// TAG picker
// ─────────────────────────────────────────────────────────────────
function PTTagPickerSheet({ state, nav, actions, params }) {
  const task = state.tasks.find(t=>t.id===params.taskId);
  const on = task?.tagIds || [];
  return (
    <div style={{ paddingBottom:24, maxHeight:'80vh', overflowY:'auto' }}>
      <SheetHandle />
      <SheetHeader title="Tags" onDone={()=>nav.closeSheet()} />
      <div style={{ padding:'0 20px', display:'flex', flexWrap:'wrap', gap:8 }}>
        {state.tags.map(t => { const sel=on.includes(t.id); const col=M3[t.color]; return (
          <Press key={t.id} onClick={()=>actions.toggleTaskTag(task.id, t.id)}>
            <div style={{ height:38, padding:'0 14px', borderRadius:SHAPE.full, background:sel?withAlpha(col,0.2):M3.surfaceContainerHigh,
              display:'inline-flex', alignItems:'center', gap:8, ...TYPE.label, fontSize:14, fontWeight:600, color:sel?col:M3.onSurface }}>
              <span style={{ width:8, height:8, borderRadius:'50%', background:col }} />{t.name}
              {sel && <Icon name="check" size={15} color={col} />}
            </div>
          </Press>
        ); })}
        <Press onClick={()=>nav.openSheet('tagEditor')}>
          <div style={{ height:38, padding:'0 14px', borderRadius:SHAPE.full, border:`1.5px dashed ${M3.outlineVariant}`,
            display:'inline-flex', alignItems:'center', gap:6, ...TYPE.label, fontSize:14, fontWeight:600, color:M3.primary }}>
            <Icon name="add" size={16} color={M3.primary} /> New tag
          </div>
        </Press>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────
// LIST picker
// ─────────────────────────────────────────────────────────────────
function PTListPickerSheet({ state, nav, actions, params }) {
  const task = state.tasks.find(t=>t.id===params.taskId);
  return (
    <div style={{ paddingBottom:24, maxHeight:'80vh', overflowY:'auto' }}>
      <SheetHandle />
      <SheetHeader title="Move to list" onDone={()=>nav.closeSheet()} />
      <div style={{ padding:'0 20px' }}>
        {state.projects.map(pr => (
          <div key={pr.id} style={{ marginBottom:12 }}>
            <div style={{ ...TYPE.label, fontSize:11, color:M3.onSurfaceVariant, fontWeight:700, textTransform:'uppercase', margin:'0 2px 6px' }}>{pr.name}</div>
            <div style={{ background:M3.surfaceContainerLow, borderRadius:SHAPE.lg, overflow:'hidden' }}>
              {pr.listIds.map((lid,i,arr) => { const l=findList(state,lid); const on=task?.listId===lid; return (
                <Press key={lid} onClick={()=>{ actions.setTaskList(task.id, lid); nav.closeSheet(); }}>
                  <div style={{ display:'flex', alignItems:'center', gap:12, padding:'12px 14px', borderBottom:i<arr.length-1?`1px solid ${M3.outlineVariant}`:'none' }}>
                    <span style={{ width:12, height:12, borderRadius:4, background:M3[l.color] }} />
                    <span style={{ flex:1, ...TYPE.body, fontSize:15, color:M3.onSurface, fontWeight:500 }}>{l.name}</span>
                    {on && <Icon name="check" size={18} color={M3.primary} />}
                  </div>
                </Press>
              ); })}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────
// PERSON editor  (create / edit local person)
// ─────────────────────────────────────────────────────────────────
function PTPersonEditorSheet({ state, nav, actions, params }) {
  const existing = params?.personId ? findPerson(state, params.personId) : null;
  const [name, setName] = React.useState(existing?.name || '');
  const [color, setColor] = React.useState(existing?.color || 'accentG');
  const inputRef = React.useRef();
  React.useEffect(()=>{ setTimeout(()=>inputRef.current?.focus(), 350); }, []);
  const initials = (name.trim().split(/\s+/).map(w=>w[0]).slice(0,2).join('') || '?').toUpperCase();
  const save = () => {
    if (!name.trim()) return;
    if (existing) actions.updatePerson(existing.id, { name:name.trim(), color });
    else actions.addPerson({ name:name.trim(), color, initials });
    nav.closeSheet();
  };
  return (
    <div style={{ paddingBottom:28 }}>
      <SheetHandle />
      <SheetHeader title={existing?'Edit person':'New person'} onDone={save} doneLabel="Save" />
      <div style={{ padding:'0 20px' }}>
        <div style={{ display:'flex', flexDirection:'column', alignItems:'center', gap:12, margin:'4px 0 20px' }}>
          <div style={{ width:80, height:80, borderRadius:'50%', background:M3[color], color:M3.onAccent,
            display:'flex', alignItems:'center', justifyContent:'center', ...TYPE.display, fontSize:32, fontWeight:700, position:'relative' }}>
            {initials}
            <div style={{ position:'absolute', bottom:-2, right:-2, width:28, height:28, borderRadius:'50%', background:M3.surfaceContainerHigh, border:`2px solid ${M3.surface}`, display:'flex', alignItems:'center', justifyContent:'center' }}>
              <Icon name="camera" size={15} color={M3.onSurfaceVariant} />
            </div>
          </div>
          <span style={{ ...TYPE.label, fontSize:12, color:M3.onSurfaceVariant }}>Initials avatar · local only</span>
        </div>
        <FieldLabel nomargin>Name</FieldLabel>
        <input ref={inputRef} value={name} onChange={e=>setName(e.target.value)} onKeyDown={e=>e.key==='Enter'&&save()}
          placeholder="Full name"
          style={{ ...TYPE.body, fontSize:17, color:M3.onSurface, padding:'10px 14px', width:'100%', marginTop:8,
            background:M3.surfaceContainerHigh, border:'none', borderRadius:SHAPE.sm }} />
        <FieldLabel>Color</FieldLabel>
        <ColorPicker value={color} onChange={setColor} />
        {existing && !existing.me && (
          <Press onClick={()=>{ actions.deletePerson(existing.id); nav.closeSheet(); }}>
            <div style={{ marginTop:24, textAlign:'center', ...TYPE.label, fontSize:14, fontWeight:600, color:M3.error }}>Delete person</div>
          </Press>
        )}
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────
// PROJECT editor  &  TAG editor
// ─────────────────────────────────────────────────────────────────
function PTProjectEditorSheet({ state, nav, actions, params }) {
  const [name, setName] = React.useState('');
  const [color, setColor] = React.useState('accentC');
  const inputRef = React.useRef();
  React.useEffect(()=>{ setTimeout(()=>inputRef.current?.focus(), 350); }, []);
  const save = () => { if(!name.trim())return; actions.addProject({ name:name.trim(), color }); nav.closeSheet(); };
  return (
    <div style={{ paddingBottom:28 }}>
      <SheetHandle />
      <SheetHeader title="New project" onDone={save} doneLabel="Create" />
      <div style={{ padding:'0 20px' }}>
        <input ref={inputRef} value={name} onChange={e=>setName(e.target.value)} onKeyDown={e=>e.key==='Enter'&&save()}
          placeholder="Project name"
          style={{ ...TYPE.display, fontSize:20, color:M3.onSurface, padding:'8px 2px 12px', width:'100%', border:'none', background:'transparent', borderBottom:`2px solid ${M3[color]}` }} />
        <FieldLabel>Color</FieldLabel>
        <ColorPicker value={color} onChange={setColor} />
      </div>
    </div>
  );
}
function PTTagEditorSheet({ state, nav, actions, params }) {
  const [name, setName] = React.useState('');
  const [color, setColor] = React.useState('accentF');
  const inputRef = React.useRef();
  React.useEffect(()=>{ setTimeout(()=>inputRef.current?.focus(), 350); }, []);
  const save = () => { if(!name.trim())return; actions.addTag({ name:name.trim().replace(/^#/,''), color }); nav.closeSheet(); };
  return (
    <div style={{ paddingBottom:28 }}>
      <SheetHandle />
      <SheetHeader title="New tag" onDone={save} doneLabel="Create" />
      <div style={{ padding:'0 20px' }}>
        <div style={{ display:'flex', alignItems:'center', gap:8, borderBottom:`2px solid ${M3[color]}`, paddingBottom:10 }}>
          <span style={{ ...TYPE.display, fontSize:22, color:M3[color] }}>#</span>
          <input ref={inputRef} value={name} onChange={e=>setName(e.target.value)} onKeyDown={e=>e.key==='Enter'&&save()}
            placeholder="tag-name"
            style={{ ...TYPE.body, fontSize:19, color:M3.onSurface, padding:'2px', width:'100%', border:'none', background:'transparent' }} />
        </div>
        <FieldLabel>Color</FieldLabel>
        <ColorPicker value={color} onChange={setColor} />
      </div>
    </div>
  );
}

// Toggle (shared)
function Toggle({ on, onClick }) {
  return (
    <Press onClick={onClick}>
      <div style={{ width:52, height:32, borderRadius:16, background:on?M3.primary:M3.surfaceContainerHighest,
        border:on?'none':`2px solid ${M3.outline}`, position:'relative', transition:`background ${DUR.fade}ms` }}>
        <div style={{ position:'absolute', top:on?4:6, left:on?22:5, width:on?24:16, height:on?24:16, borderRadius:'50%',
          background:on?M3.onPrimary:M3.outline, transition:`all ${DUR.fade}ms ${EASE.emphDecel}` }} />
      </div>
    </Press>
  );
}

Object.assign(window, {
  PTNewTaskSheet, PTRecurrenceSheet, PTAssigneeSheet, PTTagPickerSheet, PTListPickerSheet,
  PTPersonEditorSheet, PTProjectEditorSheet, PTTagEditorSheet, Toggle,
  SheetHandle, SheetHeader, ColorPicker, Stepper, FieldLabel, Panel,
});
