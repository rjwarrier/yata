// YATA prototype — pushed screens and modals

// ─────────────────────────────────────────────────────────────────
// LIST DETAIL — full pushed screen
// ─────────────────────────────────────────────────────────────────
function PTListDetail({ state, nav, params, actions }) {
  const list = state.lists.find(l => l.id === params.listId) || { name: params.listId === 'priority' ? 'Priority' : 'All', count: 12, color: M3.accentC, icon: 'flag' };
  const tasksInList = state.tasks.filter(t => params.listId === 'all' || params.listId === 'priority' ? (params.listId === 'priority' ? (t.priority || t.flag) : true) : t.listId === params.listId);
  const done = tasksInList.filter(t => t.done).length;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: M3.surface }}>
      <div style={{ background: list.color, padding: '8px 4px 24px', flexShrink: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', padding: '0 4px' }}>
          <Press onClick={() => nav.pop()}><IconBtn name="back" color={M3.onSurface} /></Press>
          <div style={{ flex: 1 }} />
          <IconBtn name="sort" color={M3.onSurface} />
          <IconBtn name="more" color={M3.onSurface} />
        </div>
        <div style={{ padding: '12px 20px 0' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div style={{
              width: 44, height: 44, borderRadius: SHAPE.md,
              background: 'rgba(35,25,22,0.12)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <Icon name={list.icon} size={22} color={M3.onSurface} />
            </div>
            <div>
              <div style={{ ...TYPE.display, fontSize: 32, color: M3.onSurface, lineHeight: 1 }}>{list.name}</div>
              <div style={{ ...TYPE.label, fontSize: 13, color: M3.onSurface, opacity: 0.7, marginTop: 4 }}>
                {tasksInList.length} tasks · {done} done
              </div>
            </div>
          </div>
          <div style={{ marginTop: 18 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', ...TYPE.label, fontSize: 12, color: M3.onSurface, marginBottom: 6 }}>
              <span>This week</span><span>{done} / {tasksInList.length}</span>
            </div>
            <div style={{ height: 6, borderRadius: 3, background: 'rgba(35,25,22,0.18)', overflow: 'hidden' }}>
              <div style={{
                width: `${tasksInList.length ? (done / tasksInList.length) * 100 : 0}%`,
                height: '100%', background: M3.onSurface, borderRadius: 3,
                transition: `width ${DUR.nav}ms ${EASE.emphDecel}`,
              }} />
            </div>
          </div>
        </div>
      </div>
      <div style={{ padding: '14px 20px 8px', display: 'flex', gap: 8, overflowX: 'auto', overflowY: 'hidden', WebkitOverflowScrolling: 'touch' }}>
        <Chip label="All" selected tint={M3.tertiaryContainer} />
        <Chip label="Due this week" />
        <Chip label="@priya" icon="user" />
        <Chip label="Design" icon="tag" />
      </div>
      <div style={{ flex: 1, overflowY: 'auto' }}>
        <SectionHeader title="This week" count={tasksInList.length} />
        {tasksInList.map(t => (
          <InteractiveTaskItem key={t.id} task={t}
            onCheck={() => actions.toggleDone(t.id)}
            onOpen={() => nav.push('taskDetail', { taskId: t.id })} />
        ))}
        <div style={{ height: 120 }} />
      </div>
      <div style={{ position: 'absolute', bottom: 28, right: 20 }}>
        <Press onClick={() => nav.openSheet('newTask', { listId: list.id })}>
          <FAB label={`Add to ${list.name}`} icon="add" extended size="md" color="primary" />
        </Press>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────
// TASK DETAIL
// ─────────────────────────────────────────────────────────────────
function PTTaskDetail({ state, nav, params, actions }) {
  const task = state.tasks.find(t => t.id === params.taskId) || state.tasks[3];
  const subs = task.subtasks || [];
  const subDone = subs.filter(s => s.done).length;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: M3.surface }}>
      <div style={{ display: 'flex', padding: '4px', alignItems: 'center', flexShrink: 0 }}>
        <Press onClick={() => nav.pop()}><IconBtn name="back" color={M3.onSurface} /></Press>
        <div style={{ flex: 1 }} />
        <IconBtn name="star" color={M3.primary} />
        <IconBtn name="archive" color={M3.onSurface} />
        <IconBtn name="more" color={M3.onSurface} />
      </div>
      <div style={{ flex: 1, overflowY: 'auto', padding: '4px 20px' }}>
        <div style={{ display: 'flex', gap: 14, alignItems: 'flex-start' }}>
          <Press onClick={() => actions.toggleDone(task.id)} style={{ paddingTop: 6 }}>
            <Check checked={task.done} color={task.listColor || M3.primary} size={28} />
          </Press>
          <div style={{ flex: 1 }}>
            <div style={{
              ...TYPE.display, fontSize: 26, color: M3.onSurface, lineHeight: 1.15,
              textDecoration: task.done ? 'line-through' : 'none',
              opacity: task.done ? 0.6 : 1,
              transition: `opacity ${DUR.fade}ms`,
            }}>
              {task.title}
            </div>
          </div>
        </div>

        <div style={{ marginTop: 20, display: 'flex', flexDirection: 'column', gap: 8 }}>
          <MetaRow icon="calendar" label="Today" value={task.time ? `${task.time}` : '—'} accent={M3.primary} />
          <MetaRow icon="bell" label="Reminder" value="15 minutes before" />
          <MetaRow icon="repeat" label="Repeats" value="Never" />
          <MetaRow icon="folder" label="List" value={task.list || 'Inbox'} swatch={task.listColor} />
          <MetaRow icon="flag" label="Priority" value={task.priority ? 'High' : 'None'} accent={task.priority ? M3.error : null} />
        </div>

        {task.labels && (
          <div style={{ marginTop: 18 }}>
            <div style={{ ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.08em', marginBottom: 8 }}>Labels</div>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              {task.labels.map((l, i) => (
                <Chip key={i} label={l} selected tint={[M3.accentA, M3.accentC, M3.accentD][i % 3]} icon={l.startsWith('@') ? 'user' : 'tag'} />
              ))}
              <Chip label="+ add" />
            </div>
          </div>
        )}

        {subs.length > 0 && (
          <div style={{ marginTop: 20 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
              <div style={{ ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.08em' }}>Subtasks · {subDone} of {subs.length}</div>
              <span style={{ ...TYPE.label, fontSize: 13, color: M3.primary, fontWeight: 600 }}>+ add</span>
            </div>
            <div style={{ height: 4, borderRadius: 2, background: M3.surfaceContainerHigh, overflow: 'hidden', marginBottom: 12 }}>
              <div style={{
                width: `${(subDone / subs.length) * 100}%`, height: '100%', background: M3.primary,
                transition: `width ${DUR.nav}ms ${EASE.emphDecel}`,
              }} />
            </div>
            {subs.map(s => (
              <Press key={s.id} onClick={() => actions.toggleSubtask(task.id, s.id)}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '8px 0' }}>
                  <Check checked={s.done} color={M3.primary} size={20} />
                  <div style={{
                    ...TYPE.body, fontSize: 14,
                    color: s.done ? M3.onSurfaceVariant : M3.onSurface,
                    textDecoration: s.done ? 'line-through' : 'none',
                    transition: `all ${DUR.fade}ms`,
                  }}>{s.title}</div>
                </div>
              </Press>
            ))}
          </div>
        )}

        {task.notes && (
          <div style={{ marginTop: 16, background: M3.surfaceContainerLow, borderRadius: SHAPE.lg, padding: '12px 14px' }}>
            <div style={{ ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant, fontWeight: 600, marginBottom: 4 }}>Notes</div>
            <div style={{ ...TYPE.body, fontSize: 14, color: M3.onSurface, lineHeight: 1.5 }}>{task.notes}</div>
          </div>
        )}
        <div style={{ height: 40 }} />
      </div>
    </div>
  );
}

function MetaRow({ icon, label, value, accent, swatch }) {
  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: 14,
      padding: '10px 12px', background: M3.surfaceContainerLow,
      borderRadius: SHAPE.md,
    }}>
      <div style={{
        width: 32, height: 32, borderRadius: SHAPE.xs,
        background: M3.surfaceContainerHigh,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <Icon name={icon} size={18} color={accent || M3.onSurfaceVariant} />
      </div>
      <div style={{ flex: 1 }}>
        <div style={{ ...TYPE.label, fontSize: 11, color: M3.onSurfaceVariant, textTransform: 'uppercase', letterSpacing: '0.06em' }}>{label}</div>
        <div style={{ ...TYPE.body, fontSize: 15, color: accent || M3.onSurface, fontWeight: 500, display: 'flex', alignItems: 'center', gap: 6 }}>
          {swatch && <span style={{ width: 10, height: 10, borderRadius: '50%', background: swatch, display: 'inline-block' }} />}
          {value}
        </div>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────
// SEARCH
// ─────────────────────────────────────────────────────────────────
function PTSearch({ state, nav, actions }) {
  const [q, setQ] = React.useState('roadmap');
  const inputRef = React.useRef();
  React.useEffect(() => { inputRef.current?.focus(); }, []);
  const lower = q.trim().toLowerCase();
  const matches = lower ? state.tasks.filter(t => t.title.toLowerCase().includes(lower)) : [];
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: M3.surface }}>
      <div style={{ padding: '8px 12px 12px', flexShrink: 0 }}>
        <div style={{
          height: 52, background: M3.surfaceContainerHigh,
          borderRadius: SHAPE.full, display: 'flex', alignItems: 'center',
          padding: '0 6px 0 16px', gap: 10,
        }}>
          <Press onClick={() => nav.pop()}><Icon name="back" size={22} color={M3.onSurfaceVariant} /></Press>
          <input ref={inputRef} value={q} onChange={(e) => setQ(e.target.value)}
            placeholder="Search tasks, lists, labels"
            style={{
              flex: 1, border: 'none', outline: 'none', background: 'transparent',
              ...TYPE.body, fontSize: 16, color: M3.onSurface,
            }} />
          {q && <Press onClick={() => setQ('')}><Icon name="close" size={20} color={M3.onSurfaceVariant} /></Press>}
          <IconBtn name="mic" color={M3.onSurfaceVariant} />
          <Avatar initial="M" color={M3.tertiary} size={32} />
          <div style={{ width: 6 }} />
        </div>
      </div>
      {q.trim() ? (
        <>
          <div style={{ padding: '0 20px 12px', display: 'flex', gap: 8, overflowX: 'auto', overflowY: 'hidden', flexShrink: 0, WebkitOverflowScrolling: 'touch' }}>
            <Chip label={`Tasks · ${matches.length}`} selected tint={M3.tertiaryContainer} />
            <Chip label="Lists · 1" />
            <Chip label="Labels · 2" />
            <Chip label="Notes · 3" />
          </div>
          <div style={{ flex: 1, overflowY: 'auto' }}>
            <SectionHeader title="Top matches" count={matches.length} />
            {matches.map(r => (
              <Press key={r.id} onClick={() => nav.push('taskDetail', { taskId: r.id })}>
                <div style={{ padding: '12px 20px', display: 'flex', gap: 14, alignItems: 'flex-start' }}>
                  <Check checked={r.done} color={r.listColor} />
                  <div style={{ flex: 1 }}>
                    <div style={{ ...TYPE.body, fontSize: 15, color: M3.onSurface }}>
                      {r.title.split(new RegExp(`(${q})`, 'i')).map((p, j) => (
                        p.toLowerCase() === q.toLowerCase()
                          ? <mark key={j} style={{ background: M3.primaryContainer, color: M3.onPrimaryContainer, padding: '0 2px', borderRadius: 3 }}>{p}</mark>
                          : <span key={j}>{p}</span>
                      ))}
                    </div>
                    <div style={{ display: 'flex', gap: 10, marginTop: 4, ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant }}>
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                        <span style={{ width: 8, height: 8, borderRadius: '50%', background: r.listColor }} />
                        {r.list}
                      </span>
                      <span>{r.time || 'Today'}</span>
                    </div>
                  </div>
                </div>
              </Press>
            ))}
            {!matches.length && (
              <div style={{ padding: 40, textAlign: 'center', ...TYPE.body, fontSize: 14, color: M3.onSurfaceVariant }}>
                No matches for "{q}"
              </div>
            )}
          </div>
        </>
      ) : (
        <div style={{ flex: 1, padding: '0 20px' }}>
          <div style={{ ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.08em', margin: '4px 4px 10px' }}>Recent</div>
          {['Q2 roadmap', 'release notes', 'expense report'].map((r, i) => (
            <Press key={i} onClick={() => setQ(r)}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 4px' }}>
                <Icon name="clock" size={18} color={M3.onSurfaceVariant} />
                <div style={{ ...TYPE.body, fontSize: 15, color: M3.onSurface }}>{r}</div>
              </div>
            </Press>
          ))}
        </div>
      )}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────
// LABELS
// ─────────────────────────────────────────────────────────────────
function PTLabels({ nav }) {
  const labels = [
    { name: 'design', count: 12, color: M3.accentA },
    { name: 'engineering', count: 18, color: M3.accentC },
    { name: 'meeting', count: 7, color: M3.accentD },
    { name: 'blocked', count: 3, color: M3.accentF },
    { name: 'research', count: 9, color: M3.accentE },
    { name: 'admin', count: 4, color: M3.accentB },
    { name: 'quick-win', count: 6, color: '#FFB4A2' },
  ];
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: M3.surface }}>
      <div style={{ padding: '4px', display: 'flex', alignItems: 'center', flexShrink: 0 }}>
        <Press onClick={() => nav.pop()}><IconBtn name="back" color={M3.onSurface} /></Press>
        <div style={{ ...TYPE.title, fontSize: 20, color: M3.onSurface, flex: 1, paddingLeft: 4 }}>Labels</div>
        <IconBtn name="search" color={M3.onSurfaceVariant} />
        <IconBtn name="add" color={M3.onSurfaceVariant} />
      </div>
      <div style={{ flex: 1, overflowY: 'auto' }}>
        <div style={{ padding: '4px 20px 12px' }}>
          <div style={{ ...TYPE.body, fontSize: 14, color: M3.onSurfaceVariant, lineHeight: 1.5 }}>
            Add labels to tasks to filter across lists. Tap a label to view every task tagged.
          </div>
        </div>
        <div style={{ padding: '8px 20px 8px', display: 'flex', flexWrap: 'wrap', gap: 8 }}>
          {labels.map((l, i) => (
            <Press key={i}>
              <div style={{
                height: 36, padding: '0 14px', borderRadius: SHAPE.xs,
                background: l.color, color: '#2a1a14',
                display: 'inline-flex', alignItems: 'center', gap: 8,
                ...TYPE.label, fontSize: 14, fontWeight: 500,
              }}>
                <Icon name="tag" size={14} color="#2a1a14" />
                {l.name}
                <span style={{ opacity: 0.6, fontSize: 12, fontWeight: 600 }}>{l.count}</span>
              </div>
            </Press>
          ))}
        </div>
        <SectionHeader title="Recently used" action="Edit" />
        {labels.slice(0, 4).map((l, i) => (
          <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '12px 20px' }}>
            <div style={{ width: 36, height: 36, borderRadius: SHAPE.xs, background: l.color, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Icon name="tag" size={16} color="#2a1a14" />
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ ...TYPE.body, fontSize: 15, color: M3.onSurface, fontWeight: 500 }}>{l.name}</div>
              <div style={{ ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant, marginTop: 2 }}>{l.count} tasks · used 2h ago</div>
            </div>
            <Icon name="more" size={20} color={M3.onSurfaceVariant} />
          </div>
        ))}
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────
// SETTINGS
// ─────────────────────────────────────────────────────────────────
function PTSettings({ nav, theme, setTheme }) {
  const rows = [
    { group: 'Account', items: [
      { icon: 'user', t: 'Profile', s: 'mira@orbital.co' },
      { icon: 'bell', t: 'Notifications', s: 'Daily summary, 8:00 AM' },
      { icon: 'calendar', t: 'Calendar sync', s: '2 calendars connected' },
    ]},
    { group: 'Appearance', items: [
      { icon: 'sparkle', t: 'Theme', s: theme === 'dark' ? 'Dark · Coral' : 'Light · Coral', action: () => setTheme(theme === 'dark' ? 'light' : 'dark'), end: 'toggle', val: theme === 'dark' },
      { icon: 'grid', t: 'Density', s: 'Comfortable' },
    ]},
    { group: 'Defaults', items: [
      { icon: 'folder', t: 'Default list', s: 'Inbox' },
      { icon: 'flag', t: 'Default priority', s: 'None' },
      { icon: 'repeat', t: 'Start of week', s: 'Monday' },
    ]},
  ];
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: M3.surface }}>
      <div style={{ padding: '4px', display: 'flex', alignItems: 'center', flexShrink: 0 }}>
        <Press onClick={() => nav.pop()}><IconBtn name="back" color={M3.onSurface} /></Press>
        <div style={{ ...TYPE.title, fontSize: 20, color: M3.onSurface, flex: 1, paddingLeft: 4 }}>Settings</div>
        <IconBtn name="search" color={M3.onSurfaceVariant} />
      </div>
      <div style={{ flex: 1, overflowY: 'auto' }}>
        <div style={{ padding: '0 20px 20px' }}>
          <div style={{
            background: M3.tertiaryContainer, borderRadius: SHAPE.xl,
            padding: '18px 18px', display: 'flex', alignItems: 'center', gap: 14,
          }}>
            <Avatar initial="M" color={M3.tertiary} size={52} />
            <div style={{ flex: 1 }}>
              <div style={{ ...TYPE.title, fontSize: 17, color: M3.onTertiaryContainer, fontWeight: 600 }}>Mira Castellanos</div>
              <div style={{ ...TYPE.label, fontSize: 12, color: M3.onTertiaryContainer, opacity: 0.8 }}>Pro · 2-year streak · 1,402 tasks done</div>
            </div>
            <Btn label="Manage" variant="tonal" size="sm" color="primary" />
          </div>
        </div>
        <div style={{ padding: '0 20px' }}>
          {rows.map((g, i) => (
            <div key={i} style={{ marginBottom: 20 }}>
              <div style={{ ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.08em', margin: '4px 4px 8px' }}>{g.group}</div>
              <div style={{ background: M3.surfaceContainerLow, borderRadius: SHAPE.lg, overflow: 'hidden' }}>
                {g.items.map((r, j) => (
                  <Press key={j} onClick={r.action || (() => {})}>
                    <div style={{
                      display: 'flex', alignItems: 'center', gap: 14, padding: '12px 14px',
                      borderBottom: j < g.items.length - 1 ? `1px solid ${M3.outlineVariant}` : 'none',
                    }}>
                      <div style={{ width: 36, height: 36, borderRadius: SHAPE.sm, background: M3.surfaceContainerHigh, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        <Icon name={r.icon} size={18} color={M3.onSurfaceVariant} />
                      </div>
                      <div style={{ flex: 1 }}>
                        <div style={{ ...TYPE.body, fontSize: 15, color: M3.onSurface, fontWeight: 500 }}>{r.t}</div>
                        <div style={{ ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant, marginTop: 2 }}>{r.s}</div>
                      </div>
                      {r.end === 'toggle' ? (
                        <Toggle on={r.val} />
                      ) : (
                        <Icon name="chevron" size={16} color={M3.onSurfaceVariant} />
                      )}
                    </div>
                  </Press>
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function Toggle({ on }) {
  return (
    <div style={{
      width: 52, height: 32, borderRadius: 16,
      background: on ? M3.primary : M3.surfaceContainerHigh,
      border: on ? 'none' : `2px solid ${M3.outline}`,
      position: 'relative',
      transition: `background ${DUR.fade}ms`,
    }}>
      <div style={{
        position: 'absolute', top: on ? 4 : 6, left: on ? 22 : 4,
        width: on ? 24 : 16, height: on ? 24 : 16, borderRadius: '50%',
        background: on ? '#fff' : M3.outline,
        transition: `left ${DUR.fade}ms ${EASE.emphDecel}, top ${DUR.fade}ms, width ${DUR.fade}ms, height ${DUR.fade}ms`,
      }} />
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────
// NEW TASK SHEET (modal)
// ─────────────────────────────────────────────────────────────────
function PTNewTaskSheet({ nav, actions, params }) {
  const [title, setTitle] = React.useState('');
  const inputRef = React.useRef();
  React.useEffect(() => { setTimeout(() => inputRef.current?.focus(), 350); }, []);
  const submit = () => {
    if (!title.trim()) return;
    actions.addTask({ title: title.trim(), listId: params?.listId });
    nav.closeSheet();
  };
  return (
    <div style={{ padding: '8px 20px 24px' }}>
      <div style={{ width: 32, height: 4, borderRadius: 2, background: M3.outline, margin: '0 auto 16px', opacity: 0.5 }} />
      <input ref={inputRef} value={title} onChange={(e) => setTitle(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && submit()}
        placeholder="Draft retro agenda"
        style={{
          ...TYPE.display, fontSize: 22, color: M3.onSurface, padding: '12px 4px',
          width: '100%', border: 'none', outline: 'none', background: 'transparent',
          borderBottom: `2px solid ${M3.primary}`,
        }} />
      <div style={{ ...TYPE.body, fontSize: 13, color: M3.onSurfaceVariant, padding: '8px 4px 16px' }}>
        Add notes or details…
      </div>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 16 }}>
        <Chip label="Today · 4 PM" icon="calendar" selected tint={M3.primaryContainer} />
        <Chip label="High" icon="flag" selected tint={M3.errorContainer} />
        <Chip label="Work" icon="folder" selected tint={M3.accentC} />
        <Chip label="+ label" icon="tag" />
        <Chip label="+ remind" icon="bell" />
      </div>
      <div style={{
        display: 'flex', alignItems: 'center', gap: 2,
        borderTop: `1px solid ${M3.outlineVariant}`, paddingTop: 10,
      }}>
        <IconBtn name="calendar" color={M3.onSurfaceVariant} />
        <IconBtn name="flag" color={M3.onSurfaceVariant} />
        <IconBtn name="tag" color={M3.onSurfaceVariant} />
        <IconBtn name="subtask" color={M3.onSurfaceVariant} />
        <IconBtn name="attach" color={M3.onSurfaceVariant} />
        <IconBtn name="mic" color={M3.onSurfaceVariant} />
        <div style={{ flex: 1 }} />
        <Press onClick={submit}>
          <FAB icon="arrow" size="sm" color="primary" />
        </Press>
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────
// NAV DRAWER
// ─────────────────────────────────────────────────────────────────
function PTDrawer({ state, nav }) {
  const groups = [
    { items: [
      { icon: 'today', t: 'Today', c: 7, action: () => { nav.goTab(0); nav.closeDrawer(); } },
      { icon: 'upcoming', t: 'Upcoming', c: 24, action: () => { nav.goTab(1); nav.closeDrawer(); } },
      { icon: 'inbox', t: 'Inbox', c: 5, action: () => { nav.goTab(2); nav.closeDrawer(); } },
      { icon: 'flag', t: 'Priority', c: 4, action: () => { nav.push('listDetail', { listId: 'priority' }); nav.closeDrawer(); } },
    ]},
  ];
  return (
    <div style={{
      width: '85%', height: '100%', background: M3.surface,
      display: 'flex', flexDirection: 'column',
      borderTopRightRadius: 28, borderBottomRightRadius: 28, overflow: 'hidden',
    }}>
      <div style={{ padding: '24px 20px 16px' }}>
        <Avatar initial="M" color={M3.tertiary} size={44} />
        <div style={{ ...TYPE.title, fontSize: 17, color: M3.onSurface, marginTop: 12, fontWeight: 600 }}>Mira Castellanos</div>
        <div style={{ ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant, marginTop: 2 }}>mira@orbital.co</div>
      </div>
      <div style={{ flex: 1, overflowY: 'auto', padding: '0 12px' }}>
        {groups[0].items.map((r, i) => (
          <Press key={i} onClick={r.action}>
            <div style={{
              display: 'flex', alignItems: 'center', gap: 14, padding: '12px 16px',
              borderRadius: SHAPE.full, marginBottom: 2,
            }}>
              <Icon name={r.icon} size={20} color={M3.onSurfaceVariant} />
              <div style={{ flex: 1, ...TYPE.body, fontSize: 15, color: M3.onSurface, fontWeight: 500 }}>{r.t}</div>
              <div style={{ ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant }}>{r.c}</div>
            </div>
          </Press>
        ))}
        <div style={{ ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.08em', padding: '20px 16px 8px' }}>My lists</div>
        {state.lists.map((l, i) => (
          <Press key={i} onClick={() => { nav.push('listDetail', { listId: l.id }); nav.closeDrawer(); }}>
            <div style={{
              display: 'flex', alignItems: 'center', gap: 14, padding: '12px 16px',
              borderRadius: SHAPE.full, marginBottom: 2,
            }}>
              <div style={{ width: 20, height: 20, borderRadius: SHAPE.xs, background: l.color }} />
              <div style={{ flex: 1, ...TYPE.body, fontSize: 15, color: M3.onSurface, fontWeight: 500 }}>{l.name}</div>
              <div style={{ ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant }}>{l.count}</div>
            </div>
          </Press>
        ))}
      </div>
      <div style={{ padding: '12px', borderTop: `1px solid ${M3.outlineVariant}` }}>
        <Press onClick={() => { nav.push('settings'); nav.closeDrawer(); }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '12px 16px', borderRadius: SHAPE.full }}>
            <Icon name="settings" size={20} color={M3.onSurfaceVariant} />
            <div style={{ ...TYPE.body, fontSize: 15, color: M3.onSurface, fontWeight: 500 }}>Settings</div>
          </div>
        </Press>
      </div>
    </div>
  );
}

Object.assign(window, {
  PTListDetail, PTTaskDetail, PTSearch, PTLabels, PTSettings, PTNewTaskSheet, PTDrawer, Toggle, MetaRow,
});
