// YATA screens — part 1: Today, Upcoming, Lists overview, List detail, Task detail

// ─── 1. TODAY (Home) ─────────────────────────────────────────────
function ScreenToday() {
  return (
    <Phone>
      <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        {/* Hero header — expressive: large display type + decorative shape */}
        <div style={{ padding: '12px 20px 20px', position: 'relative' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <IconBtn name="menu" color={M3.onSurface} />
            <div style={{ display: 'flex', gap: 0 }}>
              <IconBtn name="search" color={M3.onSurface} />
              <Avatar initial="M" color={M3.tertiary} size={32} />
              <div style={{ width: 12 }} />
            </div>
          </div>
          <div style={{ padding: '16px 4px 0' }}>
            <div style={{ ...TYPE.label, fontSize: 13, color: M3.primary, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.08em' }}>
              Thursday · Apr 18
            </div>
            <div style={{ ...TYPE.display, fontSize: 40, lineHeight: 1, marginTop: 6, color: M3.onSurface }}>
              Good morning,<br/>Mira
            </div>
            <div style={{ display: 'flex', gap: 8, marginTop: 18, alignItems: 'center' }}>
              <div style={{
                height: 44, padding: '0 18px', borderRadius: SHAPE.full,
                background: M3.primaryContainer, color: M3.onPrimaryContainer,
                display: 'inline-flex', alignItems: 'center', gap: 10,
                ...TYPE.label, fontSize: 14, fontWeight: 600,
              }}>
                <span style={{
                  width: 28, height: 28, borderRadius: '50%', background: M3.primary,
                  color: '#fff', display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                  fontSize: 14, fontWeight: 700,
                }}>7</span>
                tasks due today
              </div>
              <div style={{
                height: 44, padding: '0 14px', borderRadius: SHAPE.full,
                background: M3.surfaceContainerHigh, color: M3.onSurface,
                display: 'inline-flex', alignItems: 'center', gap: 6,
                ...TYPE.label, fontSize: 13, fontWeight: 500,
              }}>
                <Icon name="sparkle" size={16} color={M3.tertiary} />
                3 done
              </div>
            </div>
          </div>
        </div>

        {/* Filter chips */}
        <div style={{ padding: '4px 20px 4px', display: 'flex', gap: 8, overflow: 'hidden' }}>
          <Chip label="All" selected tint={M3.tertiaryContainer} />
          <Chip label="Priority" icon="flag" />
          <Chip label="Work" icon="tag" />
          <Chip label="Personal" icon="tag" />
        </div>

        {/* Task sections */}
        <div style={{ flex: 1, overflow: 'hidden' }}>
          <SectionHeader title="Morning" count="3" />
          <TaskItem title="Review Q2 roadmap draft" time="9:00 AM" list="Work" listColor={M3.accentC} flag={M3.error} />
          <TaskItem title="Gym — upper body" time="10:30 AM" list="Personal" listColor={M3.accentE} checked />
          <TaskItem title="Reply to Anders about the proposal" list="Work" listColor={M3.accentC} />

          <SectionHeader title="Afternoon" count="2" />
          <TaskItem title="Call with Priya re: onboarding flow" time="2:00 PM" list="Work" listColor={M3.accentC} priority="high" />
          <TaskItem title="Pick up dry cleaning" list="Errands" listColor={M3.accentD} />
        </div>

        {/* FAB */}
        <div style={{ position: 'absolute', bottom: 96, right: 20 }}>
          <FAB label="New task" icon="add" extended size="md" color="primary" />
        </div>

        <BottomNav active={0} />
      </div>
    </Phone>
  );
}

// ─── 2. UPCOMING (Timeline view) ────────────────────────────────
function ScreenUpcoming() {
  const days = [
    { n: 18, d: 'Thu', dot: true }, { n: 19, d: 'Fri' }, { n: 20, d: 'Sat', active: true },
    { n: 21, d: 'Sun', dot: true }, { n: 22, d: 'Mon', dot: true }, { n: 23, d: 'Tue' }, { n: 24, d: 'Wed', dot: true },
  ];
  return (
    <Phone>
      <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        <TopBar title="Upcoming" leading="menu" trailing={['calendar', 'more']} />

        {/* Week strip */}
        <div style={{ padding: '8px 12px 16px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0 12px 12px' }}>
            <div style={{ ...TYPE.title, fontSize: 17, color: M3.onSurface }}>April 2026</div>
            <Icon name="chevronDown" size={20} color={M3.onSurfaceVariant} />
          </div>
          <div style={{ display: 'flex', gap: 4, justifyContent: 'space-between' }}>
            {days.map((d, i) => (
              <div key={i} style={{
                flex: 1, padding: '10px 0', borderRadius: SHAPE.lg,
                background: d.active ? M3.primary : 'transparent',
                display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4,
              }}>
                <div style={{ ...TYPE.label, fontSize: 11, color: d.active ? M3.onPrimary : M3.onSurfaceVariant, textTransform: 'uppercase' }}>{d.d}</div>
                <div style={{ ...TYPE.title, fontSize: 18, color: d.active ? M3.onPrimary : M3.onSurface, fontWeight: 600 }}>{d.n}</div>
                {d.dot && !d.active && <div style={{ width: 4, height: 4, borderRadius: '50%', background: M3.primary }} />}
                {d.active && <div style={{ width: 4, height: 4, borderRadius: '50%', background: M3.onPrimary }} />}
              </div>
            ))}
          </div>
        </div>

        {/* Timeline */}
        <div style={{ flex: 1, overflow: 'hidden', padding: '0 20px' }}>
          <div style={{ ...TYPE.label, fontSize: 13, color: M3.primary, fontWeight: 600, marginBottom: 12 }}>
            SATURDAY · APR 20
          </div>
          {[
            { time: '09:00', title: 'Farmer\'s market', list: 'Personal', color: M3.accentE, dur: '1h' },
            { time: '11:30', title: 'Lunch with Teo', list: 'Personal', color: M3.accentE, dur: '1h 30m' },
            { time: '14:00', title: 'Ship v2 release notes', list: 'Work', color: M3.accentC, dur: '2h', priority: true },
            { time: '18:00', title: 'Movie night', list: 'Family', color: M3.accentF, dur: '2h' },
          ].map((e, i) => (
            <div key={i} style={{ display: 'flex', gap: 12, marginBottom: 8 }}>
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
          ))}
        </div>

        <div style={{ position: 'absolute', bottom: 96, right: 20 }}>
          <FAB icon="add" size="md" color="primary" />
        </div>
        <BottomNav active={1} />
      </div>
    </Phone>
  );
}

// ─── 3. LISTS OVERVIEW ──────────────────────────────────────────
function ScreenLists() {
  const lists = [
    { name: 'Work', count: 14, color: M3.accentC, icon: 'folder' },
    { name: 'Personal', count: 8, color: M3.accentE, icon: 'home' },
    { name: 'Errands', count: 5, color: M3.accentD, icon: 'inbox' },
    { name: 'Family', count: 3, color: M3.accentF, icon: 'user' },
    { name: 'Reading', count: 22, color: M3.accentA, icon: 'star' },
    { name: 'Side projects', count: 11, color: M3.accentB, icon: 'sparkle' },
  ];
  return (
    <Phone>
      <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        <TopBar title="Browse" large leading="menu" trailing={['search', 'more']} />

        <div style={{ flex: 1, overflow: 'hidden', padding: '0 20px' }}>
          {/* Smart lists */}
          <div style={{ ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant, fontWeight: 600,
            textTransform: 'uppercase', letterSpacing: '0.08em', margin: '4px 4px 10px' }}>
            Smart
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 24 }}>
            {[
              { n: 'Today', c: 7, icon: 'today', bg: M3.primaryContainer, fg: M3.onPrimaryContainer },
              { n: 'Upcoming', c: 24, icon: 'upcoming', bg: M3.tertiaryContainer, fg: M3.onTertiaryContainer },
              { n: 'Priority', c: 4, icon: 'flag', bg: M3.secondaryContainer, fg: M3.onSecondaryContainer },
              { n: 'All', c: 63, icon: 'list', bg: M3.surfaceContainerHigh, fg: M3.onSurface },
            ].map((s, i) => (
              <div key={i} style={{
                background: s.bg, borderRadius: SHAPE.lg, padding: '16px 14px',
                height: 96, display: 'flex', flexDirection: 'column', justifyContent: 'space-between',
              }}>
                <Icon name={s.icon} size={22} color={s.fg} />
                <div>
                  <div style={{ ...TYPE.title, fontSize: 18, color: s.fg, fontWeight: 600 }}>{s.n}</div>
                  <div style={{ ...TYPE.label, fontSize: 13, color: s.fg, opacity: 0.7 }}>{s.c} tasks</div>
                </div>
              </div>
            ))}
          </div>

          {/* My lists */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', margin: '4px 4px 10px' }}>
            <div style={{ ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant, fontWeight: 600,
              textTransform: 'uppercase', letterSpacing: '0.08em' }}>My lists</div>
            <Icon name="add" size={18} color={M3.primary} />
          </div>
          <div style={{ background: M3.surfaceContainerLow, borderRadius: SHAPE.lg, overflow: 'hidden' }}>
            {lists.map((l, i) => (
              <div key={i} style={{
                display: 'flex', alignItems: 'center', gap: 14, padding: '14px 16px',
                borderBottom: i < lists.length - 1 ? `1px solid ${M3.outlineVariant}` : 'none',
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
            ))}
          </div>
        </div>

        <BottomNav active={3} />
      </div>
    </Phone>
  );
}

Object.assign(window, { ScreenToday, ScreenUpcoming, ScreenLists });
