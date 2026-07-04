// YATA — interactive prototype screens (no Phone wrapper; rendered inside the prototype's shared shell)
// Each screen is a pure render fn taking `nav` (push/pop/goTab/openSheet) and `state` (tasks, lists, etc).

// ── M3 Expressive easings + durations ───────────────────────────
const EASE = {
  emphasized: 'cubic-bezier(0.2, 0, 0, 1)',
  emphDecel:  'cubic-bezier(0.05, 0.7, 0.1, 1)',
  emphAccel:  'cubic-bezier(0.3, 0, 0.8, 0.15)',
  standard:   'cubic-bezier(0.2, 0, 0, 1)',
  spring:     'cubic-bezier(0.34, 1.56, 0.64, 1)',
};
const DUR = { nav: 380, sheet: 320, fade: 200, micro: 140 };

// Press-able wrapper — adds tap ripple-ish feedback
function Press({ children, onClick, style, disabled }) {
  const [down, setDown] = React.useState(false);
  return (
    <div
      onPointerDown={() => !disabled && setDown(true)}
      onPointerUp={() => setDown(false)}
      onPointerLeave={() => setDown(false)}
      onClick={!disabled ? onClick : undefined}
      style={{
        cursor: disabled ? 'default' : 'pointer',
        transition: `transform ${DUR.micro}ms ${EASE.standard}, background-color ${DUR.micro}ms`,
        transform: down ? 'scale(0.97)' : 'scale(1)',
        ...style,
      }}
    >
      {children}
    </div>
  );
}

// ── Mock data ───────────────────────────────────────────────────
function makeInitialState() {
  return {
    tasks: [
      { id: 't1', title: 'Review Q2 roadmap draft', time: '9:00 AM', section: 'Morning', list: 'Work', listColor: M3.accentC, flag: true, done: false, priority: false, listId: 'work' },
      { id: 't2', title: 'Gym — upper body', time: '10:30 AM', section: 'Morning', list: 'Personal', listColor: M3.accentE, done: true, listId: 'personal' },
      { id: 't3', title: 'Reply to Anders about the proposal', section: 'Morning', list: 'Work', listColor: M3.accentC, done: false, listId: 'work' },
      { id: 't4', title: 'Call with Priya re: onboarding flow', time: '2:00 PM', section: 'Afternoon', list: 'Work', listColor: M3.accentC, done: false, priority: true, listId: 'work',
        notes: 'Focus: how to cut steps 2–3. Bring the user research from last sprint, and Figma link.',
        labels: ['design', '@priya', 'q2-goal'],
        subtasks: [
          { id: 's1', title: 'Prep talking points doc', done: true },
          { id: 's2', title: 'Share latest mocks in advance', done: true },
          { id: 's3', title: 'Draft success metrics', done: false },
          { id: 's4', title: 'Send calendar invite to Teo', done: false },
        ] },
      { id: 't5', title: 'Pick up dry cleaning', section: 'Afternoon', list: 'Errands', listColor: M3.accentD, done: false, listId: 'errands' },
    ],
    lists: [
      { id: 'work', name: 'Work', count: 14, color: M3.accentC, icon: 'folder' },
      { id: 'personal', name: 'Personal', count: 8, color: M3.accentE, icon: 'home' },
      { id: 'errands', name: 'Errands', count: 5, color: M3.accentD, icon: 'inbox' },
      { id: 'family', name: 'Family', count: 3, color: M3.accentF, icon: 'user' },
      { id: 'reading', name: 'Reading', count: 22, color: M3.accentA, icon: 'star' },
      { id: 'side', name: 'Side projects', count: 11, color: M3.accentB, icon: 'sparkle' },
    ],
  };
}

// ─────────────────────────────────────────────────────────────────
// 1. TODAY
// ─────────────────────────────────────────────────────────────────
function PTToday({ state, nav, actions }) {
  const today = state.tasks.filter(t => true);
  const sections = ['Morning', 'Afternoon'];
  const dueCount = today.filter(t => !t.done).length;
  const doneCount = today.filter(t => t.done).length;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <div style={{ padding: '8px 20px 12px', flexShrink: 0 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Press onClick={() => nav.openDrawer()}><IconBtn name="menu" color={M3.onSurface} /></Press>
          <div style={{ display: 'flex', alignItems: 'center' }}>
            <Press onClick={() => nav.push('search')}><IconBtn name="search" color={M3.onSurface} /></Press>
            <Press onClick={() => nav.push('settings')}>
              <Avatar initial="M" color={M3.tertiary} size={32} />
            </Press>
            <div style={{ width: 12 }} />
          </div>
        </div>
        <div style={{ padding: '16px 4px 0' }}>
          <div style={{ ...TYPE.label, fontSize: 13, color: M3.primary, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.08em' }}>
            Thursday · May 16
          </div>
          <div style={{ display: 'flex', gap: 8, marginTop: 10, alignItems: 'center' }}>
            <div style={{
              height: 38, padding: '0 14px', borderRadius: SHAPE.full,
              background: M3.primaryContainer, color: M3.onPrimaryContainer,
              display: 'inline-flex', alignItems: 'center', gap: 10,
              ...TYPE.label, fontSize: 14, fontWeight: 600,
              transition: `background ${DUR.fade}ms`,
            }}>
              <span style={{
                width: 28, height: 28, borderRadius: '50%', background: M3.primary,
                color: '#fff', display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                fontSize: 14, fontWeight: 700,
              }}>{dueCount}</span>
              tasks due today
            </div>
            <div style={{
              height: 38, padding: '0 12px', borderRadius: SHAPE.full,
              background: M3.surfaceContainerHigh, color: M3.onSurface,
              display: 'inline-flex', alignItems: 'center', gap: 6,
              ...TYPE.label, fontSize: 13, fontWeight: 500,
            }}>
              <Icon name="sparkle" size={16} color={M3.tertiary} />
              {doneCount} done
            </div>
          </div>
        </div>
      </div>

      <div style={{ padding: '4px 20px 4px', display: 'flex', gap: 8, overflowX: 'auto', overflowY: 'hidden', flexShrink: 0, WebkitOverflowScrolling: 'touch' }}>
        <Chip label="All" selected tint={M3.tertiaryContainer} />
        <Chip label="Priority" icon="flag" />
        <Chip label="Work" icon="tag" />
        <Chip label="Personal" icon="tag" />
      </div>

      <div style={{ flex: 1, overflowY: 'auto', overflowX: 'hidden' }}>
        {sections.map(sec => {
          const items = today.filter(t => t.section === sec);
          if (!items.length) return null;
          return (
            <React.Fragment key={sec}>
              <SectionHeader title={sec} count={items.length} />
              {items.map(t => (
                <InteractiveTaskItem key={t.id} task={t}
                  onCheck={() => actions.toggleDone(t.id)}
                  onOpen={() => nav.push('taskDetail', { taskId: t.id })} />
              ))}
            </React.Fragment>
          );
        })}
        <div style={{ height: 100 }} />
      </div>
    </div>
  );
}

// Task row with tap behaviors (check or open detail)
function InteractiveTaskItem({ task, onCheck, onOpen }) {
  return (
    <div style={{
      display: 'flex', alignItems: 'flex-start', gap: 14,
      padding: '11px 20px', minHeight: 56,
    }}>
      <Press onClick={(e) => { e.stopPropagation && e.stopPropagation(); onCheck(); }} style={{ paddingTop: 2 }}>
        <Check checked={task.done} color={task.listColor || M3.primary} />
      </Press>
      <Press onClick={onOpen} style={{ flex: 1, minWidth: 0 }}>
        <div style={{
          ...TYPE.body, fontSize: 16, lineHeight: '22px',
          color: task.done ? M3.onSurfaceVariant : M3.onSurface,
          textDecoration: task.done ? 'line-through' : 'none',
          opacity: task.done ? 0.7 : 1,
          transition: `opacity ${DUR.fade}ms, color ${DUR.fade}ms`,
        }}>
          {task.title}
        </div>
        {(task.list || task.time) && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 6, flexWrap: 'wrap' }}>
            {task.time && (
              <div style={{ display: 'inline-flex', alignItems: 'center', gap: 4, ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant }}>
                <Icon name="clock" size={13} color={M3.onSurfaceVariant} />
                {task.time}
              </div>
            )}
            {task.list && (
              <div style={{ display: 'inline-flex', alignItems: 'center', gap: 4, ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant }}>
                <div style={{ width: 8, height: 8, borderRadius: '50%', background: task.listColor }} />
                {task.list}
              </div>
            )}
          </div>
        )}
      </Press>
      {task.flag && <Icon name="flag" size={18} color={M3.error} />}
      {task.priority && (
        <div style={{ width: 8, height: 8, borderRadius: '50%', background: M3.error, marginTop: 8 }} />
      )}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────
// 2. UPCOMING
// ─────────────────────────────────────────────────────────────────
function PTUpcoming({ nav }) {
  const [selected, setSelected] = React.useState(2);
  const days = [
    { n: 16, d: 'Thu', dot: true }, { n: 17, d: 'Fri' }, { n: 18, d: 'Sat', dot: true },
    { n: 19, d: 'Sun', dot: true }, { n: 20, d: 'Mon', dot: true }, { n: 21, d: 'Tue' }, { n: 22, d: 'Wed', dot: true },
  ];
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <TopBar title="Upcoming" leading="menu" trailing={['calendar', 'more']} />
      <div style={{ padding: '8px 12px 16px', flexShrink: 0 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0 12px 12px' }}>
          <div style={{ ...TYPE.title, fontSize: 17, color: M3.onSurface }}>May 2026</div>
          <Icon name="chevronDown" size={20} color={M3.onSurfaceVariant} />
        </div>
        <div style={{ display: 'flex', gap: 4, justifyContent: 'space-between' }}>
          {days.map((d, i) => {
            const active = i === selected;
            return (
              <Press key={i} onClick={() => setSelected(i)} style={{ flex: 1 }}>
                <div style={{
                  padding: '10px 0', borderRadius: SHAPE.lg,
                  background: active ? M3.primary : 'transparent',
                  display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4,
                  transition: `background ${DUR.fade}ms ${EASE.standard}`,
                }}>
                  <div style={{ ...TYPE.label, fontSize: 11, color: active ? M3.onPrimary : M3.onSurfaceVariant, textTransform: 'uppercase' }}>{d.d}</div>
                  <div style={{ ...TYPE.title, fontSize: 18, color: active ? M3.onPrimary : M3.onSurface, fontWeight: 600 }}>{d.n}</div>
                  {d.dot && !active && <div style={{ width: 4, height: 4, borderRadius: '50%', background: M3.primary }} />}
                  {active && <div style={{ width: 4, height: 4, borderRadius: '50%', background: M3.onPrimary }} />}
                </div>
              </Press>
            );
          })}
        </div>
      </div>
      <div style={{ flex: 1, overflowY: 'auto', padding: '0 20px' }}>
        <div style={{ ...TYPE.label, fontSize: 13, color: M3.primary, fontWeight: 600, marginBottom: 12 }}>
          {days[selected].d.toUpperCase()}DAY · MAY {days[selected].n}
        </div>
        {[
          { time: '09:00', title: "Farmer's market", list: 'Personal', color: M3.accentE, dur: '1h' },
          { time: '11:30', title: 'Lunch with Teo', list: 'Personal', color: M3.accentE, dur: '1h 30m' },
          { time: '14:00', title: 'Ship v2 release notes', list: 'Work', color: M3.accentC, dur: '2h', priority: true },
          { time: '18:00', title: 'Movie night', list: 'Family', color: M3.accentF, dur: '2h' },
        ].map((e, i) => (
          <Press key={i} onClick={() => nav.push('taskDetail', { taskId: 't4' })}>
            <div style={{ display: 'flex', gap: 12, marginBottom: 8 }}>
              <div style={{ width: 48, paddingTop: 14, ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant, fontVariantNumeric: 'tabular-nums' }}>
                {e.time}
              </div>
              <div style={{
                flex: 1, background: M3.surfaceContainer, borderRadius: SHAPE.lg,
                padding: '12px 14px', borderLeft: `4px solid ${e.color}`,
              }}>
                <div style={{ ...TYPE.body, fontSize: 15, color: M3.onSurface, fontWeight: 500 }}>
                  {e.title}
                  {e.priority && <span style={{ display: 'inline-block', marginLeft: 8, width: 6, height: 6, borderRadius: '50%', background: M3.error, verticalAlign: 'middle' }} />}
                </div>
                <div style={{ display: 'flex', gap: 10, marginTop: 4 }}>
                  <span style={{ ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant }}>{e.list}</span>
                  <span style={{ ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant }}>· {e.dur}</span>
                </div>
              </div>
            </div>
          </Press>
        ))}
        <div style={{ height: 100 }} />
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────
// 3. INBOX (unsorted/quick capture)
// ─────────────────────────────────────────────────────────────────
function PTInbox({ nav, actions }) {
  const inboxItems = [
    { id: 'i1', title: 'Look into the new spaced-repetition app', when: '2h ago' },
    { id: 'i2', title: 'Book annual eye appointment', when: 'yesterday' },
    { id: 'i3', title: 'Research espresso machines under $400', when: 'yesterday' },
    { id: 'i4', title: 'Idea: weekly review template doc', when: 'Mon' },
    { id: 'i5', title: 'Ask Sam about that climbing gym day-pass', when: 'Mon' },
  ];
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <TopBar title="Inbox" large leading="menu" trailing={['search', 'more']} subtitle="Capture now, organize later" />
      <div style={{ padding: '4px 20px 12px' }}>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
          <div style={{
            height: 32, padding: '0 12px', borderRadius: SHAPE.xs,
            background: M3.tertiaryContainer, color: M3.onTertiaryContainer,
            display: 'inline-flex', alignItems: 'center', gap: 6,
            ...TYPE.label, fontSize: 13, fontWeight: 600,
          }}>
            <Icon name="sparkle" size={14} color={M3.onTertiaryContainer} />
            {inboxItems.length} to organize
          </div>
          <Chip label="Auto-sort" icon="sparkle" />
        </div>
      </div>
      <div style={{ flex: 1, overflowY: 'auto', padding: '0 20px' }}>
        {inboxItems.map(it => (
          <div key={it.id} style={{
            background: M3.surfaceContainerLow, borderRadius: SHAPE.lg,
            padding: '14px 14px', marginBottom: 10, display: 'flex', gap: 12, alignItems: 'flex-start',
          }}>
            <div style={{ width: 8, height: 8, borderRadius: '50%', background: M3.primary, marginTop: 8, flexShrink: 0 }} />
            <div style={{ flex: 1 }}>
              <div style={{ ...TYPE.body, fontSize: 15, color: M3.onSurface, lineHeight: 1.4 }}>{it.title}</div>
              <div style={{ ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant, marginTop: 4 }}>Added {it.when}</div>
              <div style={{ display: 'flex', gap: 6, marginTop: 10 }}>
                <Chip label="To Work" icon="folder" />
                <Chip label="To Reading" icon="folder" />
                <Chip label="Today" icon="today" />
              </div>
            </div>
            <Press onClick={() => {}}><Icon name="more" size={20} color={M3.onSurfaceVariant} /></Press>
          </div>
        ))}
        <div style={{ height: 100 }} />
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────
// 4. BROWSE / LISTS
// ─────────────────────────────────────────────────────────────────
function PTBrowse({ state, nav }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <TopBar title="Browse" large leading="menu" trailing={['search', 'more']} />
      <div style={{ flex: 1, overflowY: 'auto', padding: '0 20px' }}>
        <div style={{ ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.08em', margin: '4px 4px 10px' }}>
          Smart
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 24 }}>
          {[
            { id: 'today', n: 'Today', c: state.tasks.filter(t => !t.done).length, icon: 'today', bg: M3.primaryContainer, fg: M3.onPrimaryContainer, action: () => nav.goTab(0) },
            { id: 'upcoming', n: 'Upcoming', c: 24, icon: 'upcoming', bg: M3.tertiaryContainer, fg: M3.onTertiaryContainer, action: () => nav.goTab(1) },
            { id: 'priority', n: 'Priority', c: state.tasks.filter(t => t.priority || t.flag).length, icon: 'flag', bg: M3.secondaryContainer, fg: M3.onSecondaryContainer, action: () => nav.push('listDetail', { listId: 'priority' }) },
            { id: 'all', n: 'All', c: 63, icon: 'list', bg: M3.surfaceContainerHigh, fg: M3.onSurface, action: () => nav.push('listDetail', { listId: 'all' }) },
          ].map((s, i) => (
            <Press key={i} onClick={s.action}>
              <div style={{
                background: s.bg, borderRadius: SHAPE.lg, padding: '16px 14px',
                height: 96, display: 'flex', flexDirection: 'column', justifyContent: 'space-between',
              }}>
                <Icon name={s.icon} size={22} color={s.fg} />
                <div>
                  <div style={{ ...TYPE.title, fontSize: 18, color: s.fg, fontWeight: 600 }}>{s.n}</div>
                  <div style={{ ...TYPE.label, fontSize: 13, color: s.fg, opacity: 0.7 }}>{s.c} tasks</div>
                </div>
              </div>
            </Press>
          ))}
        </div>

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', margin: '4px 4px 10px' }}>
          <div style={{ ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.08em' }}>My lists</div>
          <Icon name="add" size={18} color={M3.primary} />
        </div>
        <div style={{ background: M3.surfaceContainerLow, borderRadius: SHAPE.lg, overflow: 'hidden' }}>
          {state.lists.map((l, i) => (
            <Press key={i} onClick={() => nav.push('listDetail', { listId: l.id })}>
              <div style={{
                display: 'flex', alignItems: 'center', gap: 14, padding: '14px 16px',
                borderBottom: i < state.lists.length - 1 ? `1px solid ${M3.outlineVariant}` : 'none',
              }}>
                <div style={{
                  width: 36, height: 36, borderRadius: SHAPE.sm,
                  background: l.color, display: 'flex', alignItems: 'center', justifyContent: 'center',
                }}>
                  <Icon name={l.icon} size={18} color="#2a1a14" />
                </div>
                <div style={{ flex: 1, ...TYPE.body, fontSize: 15, color: M3.onSurface, fontWeight: 500 }}>
                  {l.name}
                </div>
                <div style={{ ...TYPE.label, fontSize: 13, color: M3.onSurfaceVariant }}>{l.count}</div>
                <Icon name="chevron" size={16} color={M3.onSurfaceVariant} />
              </div>
            </Press>
          ))}
        </div>

        <div style={{ marginTop: 20 }}>
          <Press onClick={() => nav.push('labels')}>
            <div style={{
              background: M3.surfaceContainerLow, borderRadius: SHAPE.lg, padding: '14px 16px',
              display: 'flex', alignItems: 'center', gap: 14,
            }}>
              <div style={{ width: 36, height: 36, borderRadius: SHAPE.sm, background: M3.accentA, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Icon name="tag" size={18} color="#2a1a14" />
              </div>
              <div style={{ flex: 1, ...TYPE.body, fontSize: 15, color: M3.onSurface, fontWeight: 500 }}>Labels</div>
              <Icon name="chevron" size={16} color={M3.onSurfaceVariant} />
            </div>
          </Press>
        </div>

        <div style={{ height: 100 }} />
      </div>
    </div>
  );
}

Object.assign(window, {
  EASE, DUR, Press, makeInitialState,
  PTToday, PTUpcoming, PTInbox, PTBrowse, InteractiveTaskItem,
});
