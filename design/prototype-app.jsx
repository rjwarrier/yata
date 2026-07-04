// YATA prototype — app shell with navigation stack, transitions, drawer, sheet

function PrototypeApp() {
  const [taskState, setTaskState] = React.useState(makeInitialState);
  const [tab, setTab] = React.useState(0); // 0 today, 1 upcoming, 2 inbox, 3 browse
  const [stack, setStack] = React.useState([]); // pushed screens
  const [drawer, setDrawer] = React.useState(false);
  const [sheet, setSheet] = React.useState(null); // { name, params }
  const [theme, setTheme] = React.useState('light');

  // ── Actions: state mutations ──
  const actions = React.useMemo(() => ({
    toggleDone: (id) => setTaskState(s => ({
      ...s, tasks: s.tasks.map(t => t.id === id ? { ...t, done: !t.done } : t),
    })),
    toggleSubtask: (taskId, subId) => setTaskState(s => ({
      ...s, tasks: s.tasks.map(t => t.id === taskId
        ? { ...t, subtasks: t.subtasks.map(st => st.id === subId ? { ...st, done: !st.done } : st) }
        : t),
    })),
    addTask: ({ title, listId }) => setTaskState(s => {
      const list = s.lists.find(l => l.id === listId);
      const newTask = {
        id: 'n' + Date.now(),
        title,
        section: 'Afternoon',
        list: list?.name || 'Work',
        listColor: list?.color || M3.accentC,
        listId: listId || 'work',
        done: false,
      };
      return { ...s, tasks: [...s.tasks, newTask] };
    }),
  }), []);

  // ── Nav ──
  const nav = React.useMemo(() => ({
    goTab: (i) => { setStack([]); setTab(i); },
    push: (name, params) => setStack(prev => [...prev, { name, params, id: Date.now() + Math.random() }]),
    pop: () => setStack(prev => prev.slice(0, -1)),
    popAll: () => setStack([]),
    openDrawer: () => setDrawer(true),
    closeDrawer: () => setDrawer(false),
    openSheet: (name, params) => setSheet({ name, params }),
    closeSheet: () => setSheet(null),
  }), []);

  // Hardware-style back (Esc key)
  React.useEffect(() => {
    const onKey = (e) => {
      if (e.key === 'Escape') {
        if (sheet) nav.closeSheet();
        else if (drawer) nav.closeDrawer();
        else if (stack.length) nav.pop();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [sheet, drawer, stack, nav]);

  const isDark = theme === 'dark';

  // Custom phone frame
  return (
    <div style={{
      width: '100vw', height: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
      background: '#1a1a1a',
      backgroundImage: 'radial-gradient(circle at 30% 20%, #3a2820 0%, #1a1110 60%)',
    }}>
      <PrototypeFrame dark={isDark}>
        {/* Active tab (cross-fade between tabs) */}
        <TabSwitcher tab={tab} state={taskState} nav={nav} actions={actions} />

        {/* Bottom nav (always visible on tabs) */}
        <div style={{
          position: 'absolute', bottom: 0, left: 0, right: 0, zIndex: 5,
          opacity: stack.length ? 0 : 1,
          transform: stack.length ? 'translateY(20px)' : 'translateY(0)',
          pointerEvents: stack.length ? 'none' : 'auto',
          transition: `opacity ${DUR.fade}ms, transform ${DUR.fade}ms`,
        }}>
          <PTBottomNav active={tab} onChange={(i) => nav.goTab(i)} />
        </div>

        {/* FAB (Today/Inbox tab only, when no stack) */}
        {(tab === 0 || tab === 2) && stack.length === 0 && (
          <div style={{
            position: 'absolute', bottom: 84, right: 20, zIndex: 6,
            transition: `transform ${DUR.fade}ms ${EASE.spring}, opacity ${DUR.fade}ms`,
            opacity: sheet ? 0 : 1,
            transform: sheet ? 'scale(0.3)' : 'scale(1)',
          }}>
            <Press onClick={() => nav.openSheet('newTask')}>
              <FAB label="New task" icon="add" extended size="md" color="primary" />
            </Press>
          </div>
        )}

        {/* Pushed screens stack */}
        {stack.map((s, i) => (
          <PushedScreen key={s.id} screen={s} state={taskState} nav={nav} actions={actions}
            theme={theme} setTheme={setTheme} depth={i} total={stack.length} />
        ))}

        {/* Drawer */}
        <DrawerOverlay open={drawer} onClose={nav.closeDrawer}>
          <PTDrawer state={taskState} nav={nav} />
        </DrawerOverlay>

        {/* Bottom sheet */}
        <SheetOverlay open={!!sheet} onClose={nav.closeSheet}>
          {sheet && sheet.name === 'newTask' && (
            <PTNewTaskSheet nav={nav} actions={actions} params={sheet.params} />
          )}
        </SheetOverlay>
      </PrototypeFrame>

      {/* Hint overlay */}
      <Hints />
    </div>
  );
}

// ── Tab switcher with cross-fade ──
function TabSwitcher({ tab, state, nav, actions }) {
  const screens = [PTToday, PTUpcoming, PTInbox, PTBrowse];
  return (
    <div style={{ position: 'absolute', inset: 0 }}>
      {screens.map((S, i) => (
        <div key={i} style={{
          position: 'absolute', inset: 0,
          opacity: i === tab ? 1 : 0,
          transform: i === tab ? 'scale(1)' : 'scale(0.98)',
          transition: `opacity ${DUR.fade}ms ${EASE.standard}, transform ${DUR.fade}ms ${EASE.standard}`,
          pointerEvents: i === tab ? 'auto' : 'none',
        }}>
          <S state={state} nav={nav} actions={actions} />
        </div>
      ))}
    </div>
  );
}

// ── Pushed screen with slide-in transition ──
function PushedScreen({ screen, state, nav, actions, theme, setTheme, depth, total }) {
  const [entered, setEntered] = React.useState(false);
  React.useEffect(() => {
    requestAnimationFrame(() => requestAnimationFrame(() => setEntered(true)));
  }, []);

  const screenMap = {
    listDetail: PTListDetail,
    taskDetail: PTTaskDetail,
    search: PTSearch,
    labels: PTLabels,
    settings: PTSettings,
  };
  const S = screenMap[screen.name];
  if (!S) return null;
  const isTop = depth === total - 1;
  return (
    <div style={{
      position: 'absolute', inset: 0,
      transform: entered ? (isTop ? 'translateX(0)' : 'translateX(-30%)') : 'translateX(100%)',
      opacity: entered ? (isTop ? 1 : 0.5) : 0,
      transition: `transform ${DUR.nav}ms ${EASE.emphDecel}, opacity ${DUR.nav}ms ${EASE.standard}`,
      zIndex: 10 + depth, background: M3.surface,
      boxShadow: isTop ? '-8px 0 24px rgba(0,0,0,0.12)' : 'none',
    }}>
      <S state={state} nav={nav} actions={actions} params={screen.params} theme={theme} setTheme={setTheme} />
    </div>
  );
}

// ── Drawer overlay ──
function DrawerOverlay({ open, onClose, children }) {
  const [mounted, setMounted] = React.useState(open);
  const [entered, setEntered] = React.useState(false);
  React.useEffect(() => {
    if (open) {
      setMounted(true);
      requestAnimationFrame(() => requestAnimationFrame(() => setEntered(true)));
    } else {
      setEntered(false);
      const t = setTimeout(() => setMounted(false), DUR.sheet);
      return () => clearTimeout(t);
    }
  }, [open]);
  if (!mounted) return null;
  return (
    <div style={{ position: 'absolute', inset: 0, zIndex: 50 }}>
      <div onClick={onClose} style={{
        position: 'absolute', inset: 0, background: 'rgba(0,0,0,0.4)',
        opacity: entered ? 1 : 0,
        transition: `opacity ${DUR.sheet}ms ${EASE.standard}`,
      }} />
      <div style={{
        position: 'absolute', top: 0, left: 0, bottom: 0,
        transform: entered ? 'translateX(0)' : 'translateX(-100%)',
        transition: `transform ${DUR.sheet}ms ${EASE.emphDecel}`,
      }}>
        {children}
      </div>
    </div>
  );
}

// ── Bottom sheet overlay ──
function SheetOverlay({ open, onClose, children }) {
  const [mounted, setMounted] = React.useState(open);
  const [entered, setEntered] = React.useState(false);
  React.useEffect(() => {
    if (open) {
      setMounted(true);
      requestAnimationFrame(() => requestAnimationFrame(() => setEntered(true)));
    } else {
      setEntered(false);
      const t = setTimeout(() => setMounted(false), DUR.sheet);
      return () => clearTimeout(t);
    }
  }, [open]);
  if (!mounted) return null;
  return (
    <div style={{ position: 'absolute', inset: 0, zIndex: 60 }}>
      <div onClick={onClose} style={{
        position: 'absolute', inset: 0, background: 'rgba(0,0,0,0.35)',
        opacity: entered ? 1 : 0,
        transition: `opacity ${DUR.sheet}ms`,
      }} />
      <div style={{
        position: 'absolute', left: 0, right: 0, bottom: 0,
        background: M3.surfaceContainerLowest,
        borderRadius: `${SHAPE.xl}px ${SHAPE.xl}px 0 0`,
        boxShadow: ELEV.l3,
        transform: entered ? 'translateY(0)' : 'translateY(100%)',
        transition: `transform ${DUR.sheet}ms ${EASE.emphDecel}`,
      }}>
        {children}
      </div>
    </div>
  );
}

// ── Bottom nav ──
function PTBottomNav({ active, onChange }) {
  const items = [
    { icon: 'today', label: 'Today' },
    { icon: 'upcoming', label: 'Upcoming' },
    { icon: 'inbox', label: 'Inbox' },
    { icon: 'search', label: 'Browse' },
  ];
  return (
    <div style={{
      height: 72, background: M3.surfaceContainer,
      display: 'flex', padding: '8px 8px', gap: 4,
      borderTop: `1px solid ${M3.outlineVariant}`,
    }}>
      {items.map((it, i) => {
        const isA = i === active;
        return (
          <Press key={i} onClick={() => onChange(i)} style={{ flex: 1 }}>
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
              <div style={{
                height: 32, padding: '0 20px', borderRadius: SHAPE.full,
                background: isA ? M3.secondaryContainer : 'transparent',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                transition: `background ${DUR.fade}ms ${EASE.standard}`,
              }}>
                <Icon name={it.icon} size={22}
                  color={isA ? M3.onSecondaryContainer : M3.onSurfaceVariant}
                  filled={isA} />
              </div>
              <div style={{
                ...TYPE.label, fontSize: 11, fontWeight: isA ? 600 : 500,
                color: isA ? M3.onSurface : M3.onSurfaceVariant,
                transition: `color ${DUR.fade}ms`,
              }}>{it.label}</div>
            </div>
          </Press>
        );
      })}
    </div>
  );
}

// ── Phone frame ──
function PrototypeFrame({ children, dark }) {
  const [scale, setScale] = React.useState(1);
  React.useEffect(() => {
    const calc = () => {
      const s = Math.min(1,
        (window.innerHeight - 48) / 864,
        (window.innerWidth - 40) / 430
      );
      setScale(Math.max(0.55, s));
    };
    calc();
    window.addEventListener('resize', calc);
    return () => window.removeEventListener('resize', calc);
  }, []);
  const surface = dark ? M3.dSurface : M3.surface;
  return (
    <div style={{
      width: 390, height: 844, borderRadius: 50,
      transform: `scale(${scale})`,
      transformOrigin: 'center center',
      background: surface,
      border: `10px solid #0a0a0a`,
      boxShadow: '0 30px 80px rgba(0,0,0,0.5), 0 4px 12px rgba(0,0,0,0.3), inset 0 0 0 1px rgba(255,255,255,0.05)',
      display: 'flex', flexDirection: 'column', boxSizing: 'border-box',
      overflow: 'hidden', position: 'relative',
    }}>
      {/* Status bar */}
      <div style={{
        height: 38, display: 'flex', alignItems: 'center',
        justifyContent: 'space-between', padding: '0 22px',
        position: 'relative', flexShrink: 0, background: surface, zIndex: 100,
      }}>
        <span style={{ ...TYPE.label, fontSize: 14, fontWeight: 600, color: dark ? '#F0DED8' : M3.onSurface, letterSpacing: 0.2 }}>9:41</span>
        <div style={{
          position: 'absolute', left: '50%', top: 9, transform: 'translateX(-50%)',
          width: 110, height: 30, borderRadius: 22, background: '#0a0a0a',
        }} />
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <svg width="16" height="12" viewBox="0 0 16 12"><path d="M1 11h2V8H1zM5 11h2V6H5zM9 11h2V4H9zM13 11h2V2h-2z" fill={dark ? '#F0DED8' : M3.onSurface}/></svg>
          <svg width="16" height="12" viewBox="0 0 16 12"><path d="M8 11.2L.5 3.7a11 11 0 0115 0L8 11.2z" fill={dark ? '#F0DED8' : M3.onSurface}/></svg>
          <svg width="24" height="12" viewBox="0 0 24 12"><rect x="1" y="1.5" width="20" height="9" rx="2" fill="none" stroke={dark ? '#F0DED8' : M3.onSurface} strokeWidth="1.2"/><rect x="3" y="3" width="16" height="6" rx="1" fill={dark ? '#F0DED8' : M3.onSurface}/></svg>
        </div>
      </div>
      {/* Content */}
      <div style={{ flex: 1, position: 'relative', overflow: 'hidden', background: surface }}>
        {children}
      </div>
      {/* Nav handle */}
      <div style={{
        height: 28, display: 'flex', alignItems: 'center', justifyContent: 'center',
        background: surface, flexShrink: 0, zIndex: 100,
      }}>
        <div style={{ width: 130, height: 4, borderRadius: 2, background: dark ? '#F0DED8' : '#231916' }} />
      </div>
    </div>
  );
}

// ── Helpful hints around the phone ──
function Hints() {
  const [show, setShow] = React.useState(true);
  React.useEffect(() => {
    const t = setTimeout(() => setShow(false), 7000);
    return () => clearTimeout(t);
  }, []);
  if (!show) return (
    <div onClick={() => setShow(true)} style={{
      position: 'fixed', top: 20, right: 20, padding: '8px 14px',
      background: 'rgba(255,255,255,0.1)', color: '#fff', borderRadius: 999,
      fontFamily: 'Inter, system-ui', fontSize: 12, cursor: 'pointer',
    }}>?</div>
  );
  return (
    <div style={{
      position: 'fixed', top: 28, left: 28, maxWidth: 280,
      background: 'rgba(255,255,255,0.06)', backdropFilter: 'blur(20px)',
      border: '1px solid rgba(255,255,255,0.1)',
      borderRadius: 16, padding: '14px 16px', color: '#F0DED8',
      fontFamily: 'Inter, system-ui',
    }}>
      <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 8, fontFamily: '"Inter Tight", system-ui' }}>
        YATA · interactive prototype
      </div>
      <div style={{ fontSize: 12, lineHeight: 1.5, opacity: 0.8 }}>
        Tap around — bottom nav, list cards, tasks, FAB, menu icon.<br/>
        <span style={{ opacity: 0.6 }}>Esc = back</span>
      </div>
      <div onClick={() => setShow(false)} style={{
        marginTop: 10, fontSize: 11, opacity: 0.6, cursor: 'pointer',
      }}>Hide</div>
    </div>
  );
}

Object.assign(window, { PrototypeApp });
