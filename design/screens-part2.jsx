// YATA screens — part 2: List detail, Task detail, New task sheet, Search, Labels, Settings

// ─── 4. LIST DETAIL (Work) — expressive header with big colored block ──
function ScreenListDetail() {
  return (
    <Phone>
      <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        {/* Colored hero */}
        <div style={{ background: M3.accentC, padding: '8px 4px 24px' }}>
          <div style={{ display: 'flex', alignItems: 'center', padding: '0 4px' }}>
            <IconBtn name="back" color={M3.onSurface} />
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
                <Icon name="folder" size={22} color={M3.onSurface} />
              </div>
              <div>
                <div style={{ ...TYPE.display, fontSize: 32, color: M3.onSurface, lineHeight: 1 }}>Work</div>
                <div style={{ ...TYPE.label, fontSize: 13, color: M3.onSurface, opacity: 0.7, marginTop: 4 }}>
                  14 tasks · 3 overdue
                </div>
              </div>
            </div>
            {/* Progress */}
            <div style={{ marginTop: 18 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', ...TYPE.label, fontSize: 12, color: M3.onSurface, marginBottom: 6 }}>
                <span>This week</span><span>9 / 14</span>
              </div>
              <div style={{ height: 6, borderRadius: 3, background: 'rgba(35,25,22,0.18)', overflow: 'hidden' }}>
                <div style={{ width: '64%', height: '100%', background: M3.onSurface, borderRadius: 3 }} />
              </div>
            </div>
          </div>
        </div>

        {/* Chips */}
        <div style={{ padding: '14px 20px 8px', display: 'flex', gap: 8, overflow: 'hidden' }}>
          <Chip label="All" selected tint={M3.tertiaryContainer} />
          <Chip label="Due this week" />
          <Chip label="@priya" icon="user" />
          <Chip label="Design" icon="tag" />
        </div>

        {/* Tasks */}
        <div style={{ flex: 1, overflow: 'hidden' }}>
          <SectionHeader title="Overdue" count="3" />
          <TaskItem title="Write postmortem for launch incident" time="Apr 15" list="Work" listColor={M3.accentC} flag={M3.error} priority="high" />
          <TaskItem title="Audit accessibility on settings page" time="Apr 16" list="Work" listColor={M3.accentC} />
          <TaskItem title="Finalize Q2 OKR draft" time="Apr 17" list="Work" listColor={M3.accentC} flag={M3.error} />

          <SectionHeader title="This week" count="6" />
          <TaskItem title="Review Q2 roadmap draft" time="Today · 9:00" list="Work" listColor={M3.accentC} />
          <TaskItem title="Call with Priya re: onboarding" time="Today · 2:00" list="Work" listColor={M3.accentC} priority="high" />
          <TaskItem title="Ship release notes v2.4" time="Sat" list="Work" listColor={M3.accentC} />
        </div>

        <div style={{ position: 'absolute', bottom: 28, right: 20 }}>
          <FAB label="Add to Work" icon="add" extended size="md" color="primary" />
        </div>
      </div>
    </Phone>
  );
}

// ─── 5. TASK DETAIL ──────────────────────────────────────────────
function ScreenTaskDetail() {
  return (
    <Phone>
      <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        <div style={{ display: 'flex', padding: '4px', alignItems: 'center' }}>
          <IconBtn name="back" color={M3.onSurface} />
          <div style={{ flex: 1 }} />
          <IconBtn name="star" color={M3.primary} />
          <IconBtn name="archive" color={M3.onSurface} />
          <IconBtn name="more" color={M3.onSurface} />
        </div>

        <div style={{ flex: 1, overflow: 'hidden', padding: '4px 20px' }}>
          {/* Title + checkbox */}
          <div style={{ display: 'flex', gap: 14, alignItems: 'flex-start' }}>
            <div style={{ paddingTop: 6 }}>
              <Check checked={false} color={M3.primary} size={28} />
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ ...TYPE.display, fontSize: 26, color: M3.onSurface, lineHeight: 1.15 }}>
                Call with Priya re: onboarding flow
              </div>
            </div>
          </div>

          {/* Meta cards */}
          <div style={{ marginTop: 20, display: 'flex', flexDirection: 'column', gap: 8 }}>
            <MetaRow icon="calendar" label="Today" value="2:00 PM – 2:45 PM" accent={M3.primary} />
            <MetaRow icon="bell" label="Reminder" value="15 minutes before" />
            <MetaRow icon="repeat" label="Repeats" value="Never" />
            <MetaRow icon="folder" label="List" value="Work" swatch={M3.accentC} />
            <MetaRow icon="flag" label="Priority" value="High" accent={M3.error} />
          </div>

          {/* Labels */}
          <div style={{ marginTop: 18 }}>
            <div style={{ ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant, fontWeight: 600,
              textTransform: 'uppercase', letterSpacing: '0.08em', marginBottom: 8 }}>Labels</div>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              <Chip label="design" selected tint={M3.accentA} icon="tag" />
              <Chip label="@priya" selected tint={M3.accentC} icon="user" />
              <Chip label="q2-goal" selected tint={M3.accentD} icon="tag" />
              <Chip label="+ add" />
            </div>
          </div>

          {/* Subtasks */}
          <div style={{ marginTop: 20 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
              <div style={{ ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant, fontWeight: 600,
                textTransform: 'uppercase', letterSpacing: '0.08em' }}>Subtasks · 2 of 4</div>
              <span style={{ ...TYPE.label, fontSize: 13, color: M3.primary, fontWeight: 600 }}>+ add</span>
            </div>
            <div style={{ height: 4, borderRadius: 2, background: M3.surfaceContainerHigh, overflow: 'hidden', marginBottom: 12 }}>
              <div style={{ width: '50%', height: '100%', background: M3.primary }} />
            </div>
            {[
              { t: 'Prep talking points doc', d: true },
              { t: 'Share latest mocks in advance', d: true },
              { t: 'Draft success metrics', d: false },
              { t: 'Send calendar invite to Teo', d: false },
            ].map((s, i) => (
              <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '8px 0' }}>
                <Check checked={s.d} color={M3.primary} size={20} />
                <div style={{ ...TYPE.body, fontSize: 14,
                  color: s.d ? M3.onSurfaceVariant : M3.onSurface,
                  textDecoration: s.d ? 'line-through' : 'none' }}>
                  {s.t}
                </div>
              </div>
            ))}
          </div>

          {/* Notes */}
          <div style={{ marginTop: 16, background: M3.surfaceContainerLow,
            borderRadius: SHAPE.lg, padding: '12px 14px' }}>
            <div style={{ ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant, fontWeight: 600, marginBottom: 4 }}>
              Notes
            </div>
            <div style={{ ...TYPE.body, fontSize: 14, color: M3.onSurface, lineHeight: 1.5 }}>
              Focus: how to cut steps 2–3. Bring the user research from last sprint, and Figma link.
            </div>
          </div>
        </div>
      </div>
    </Phone>
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

// ─── 6. NEW TASK bottom sheet ────────────────────────────────────
function ScreenNewTask() {
  return (
    <Phone>
      <div style={{ display: 'flex', flexDirection: 'column', height: '100%', position: 'relative' }}>
        {/* Dim bg showing today list behind */}
        <div style={{ flex: 1, background: M3.surface, opacity: 0.4 }}>
          <div style={{ padding: '16px 20px' }}>
            <div style={{ ...TYPE.display, fontSize: 34, color: M3.onSurface }}>Today</div>
          </div>
        </div>
        {/* Scrim */}
        <div style={{
          position: 'absolute', inset: 0, background: 'rgba(0,0,0,0.35)',
        }} />
        {/* Sheet */}
        <div style={{
          position: 'absolute', left: 0, right: 0, bottom: 0,
          background: M3.surfaceContainerLowest,
          borderRadius: `${SHAPE.xl}px ${SHAPE.xl}px 0 0`,
          padding: '8px 20px 24px',
          boxShadow: ELEV.l3,
        }}>
          <div style={{ width: 32, height: 4, borderRadius: 2, background: M3.outline,
            margin: '0 auto 16px', opacity: 0.5 }} />

          {/* Title input — expressive large field */}
          <div style={{
            ...TYPE.display, fontSize: 22, color: M3.onSurface, padding: '12px 4px',
            borderBottom: `2px solid ${M3.primary}`,
          }}>
            Draft retro agenda<span style={{ color: M3.primary, marginLeft: 2 }}>|</span>
          </div>
          <div style={{ ...TYPE.body, fontSize: 13, color: M3.onSurfaceVariant, padding: '8px 4px 16px' }}>
            Add notes or details…
          </div>

          {/* Quick chip row */}
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 16 }}>
            <Chip label="Today · 4 PM" icon="calendar" selected tint={M3.primaryContainer} />
            <Chip label="High" icon="flag" selected tint={M3.errorContainer} />
            <Chip label="Work" icon="folder" selected tint={M3.accentC} />
            <Chip label="+ label" icon="tag" />
            <Chip label="+ remind" icon="bell" />
          </div>

          {/* Tool row */}
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
            <FAB icon="arrow" size="sm" color="primary" />
          </div>
        </div>
      </div>
    </Phone>
  );
}

// ─── 7. SEARCH (active, results) ─────────────────────────────────
function ScreenSearch() {
  const results = [
    { t: 'Review Q2 roadmap draft', list: 'Work', color: M3.accentC, match: 'roadmap', today: true },
    { t: 'Roadmap review with leadership', list: 'Work', color: M3.accentC, match: 'Roadmap', date: 'Fri' },
    { t: 'Q3 roadmap brainstorm notes', list: 'Reading', color: M3.accentA, match: 'roadmap', date: 'Apr 5' },
  ];
  return (
    <Phone>
      <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        {/* Search bar */}
        <div style={{ padding: '8px 12px 12px' }}>
          <div style={{
            height: 52, background: M3.surfaceContainerHigh,
            borderRadius: SHAPE.full, display: 'flex', alignItems: 'center',
            padding: '0 6px 0 16px', gap: 10,
          }}>
            <Icon name="back" size={22} color={M3.onSurfaceVariant} />
            <div style={{ flex: 1, ...TYPE.body, fontSize: 16, color: M3.onSurface }}>
              roadmap<span style={{ color: M3.primary, marginLeft: 1 }}>|</span>
            </div>
            <IconBtn name="mic" color={M3.onSurfaceVariant} />
            <Avatar initial="M" color={M3.tertiary} size={32} />
            <div style={{ width: 6 }} />
          </div>
        </div>

        {/* Scope chips */}
        <div style={{ padding: '0 20px 12px', display: 'flex', gap: 8, overflow: 'hidden' }}>
          <Chip label="Tasks · 8" selected tint={M3.tertiaryContainer} />
          <Chip label="Lists · 1" />
          <Chip label="Labels · 2" />
          <Chip label="Notes · 3" />
        </div>

        {/* Filters applied */}
        <div style={{ padding: '0 20px 8px', display: 'flex', gap: 6, alignItems: 'center' }}>
          <span style={{ ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant }}>Filters:</span>
          <Chip label="Open" selected onRemove />
          <Chip label="Last 30 days" selected onRemove />
        </div>

        {/* Results */}
        <div style={{ flex: 1, overflow: 'hidden' }}>
          <SectionHeader title="Top matches" count="3" />
          {results.map((r, i) => (
            <div key={i} style={{ padding: '12px 20px', display: 'flex', gap: 14, alignItems: 'flex-start' }}>
              <Check checked={false} color={r.color} />
              <div style={{ flex: 1 }}>
                <div style={{ ...TYPE.body, fontSize: 15, color: M3.onSurface }}>
                  {r.t.split(new RegExp(`(${r.match})`, 'i')).map((p, j) => (
                    p.toLowerCase() === r.match.toLowerCase()
                      ? <mark key={j} style={{ background: M3.primaryContainer, color: M3.onPrimaryContainer, padding: '0 2px', borderRadius: 3 }}>{p}</mark>
                      : <span key={j}>{p}</span>
                  ))}
                </div>
                <div style={{ display: 'flex', gap: 10, marginTop: 4, ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant }}>
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                    <span style={{ width: 8, height: 8, borderRadius: '50%', background: r.color }} />
                    {r.list}
                  </span>
                  <span>{r.today ? 'Today' : r.date}</span>
                </div>
              </div>
            </div>
          ))}
          <SectionHeader title="In lists" count="1" />
          <div style={{ padding: '4px 20px 12px', display: 'flex', gap: 12, alignItems: 'center' }}>
            <div style={{ width: 36, height: 36, borderRadius: SHAPE.sm, background: M3.accentC, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Icon name="folder" size={18} color="#2a1a14" />
            </div>
            <div style={{ flex: 1, ...TYPE.body, fontSize: 15, color: M3.onSurface }}>
              <mark style={{ background: M3.primaryContainer, color: M3.onPrimaryContainer, padding: '0 2px', borderRadius: 3 }}>Roadmap</mark> · 2026
            </div>
            <div style={{ ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant }}>7 tasks</div>
          </div>
        </div>
      </div>
    </Phone>
  );
}

// ─── 8. LABELS manager ───────────────────────────────────────────
function ScreenLabels() {
  const labels = [
    { name: 'design', count: 12, color: M3.accentA },
    { name: 'engineering', count: 18, color: M3.accentC },
    { name: 'meeting', count: 7, color: M3.accentD },
    { name: 'blocked', count: 3, color: M3.accentF },
    { name: 'research', count: 9, color: M3.accentE },
    { name: 'admin', count: 4, color: M3.accentB },
    { name: 'quick-win', count: 6, color: M3.primaryFixedDim },
  ];
  return (
    <Phone>
      <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        <TopBar title="Labels" large leading="back" trailing={['search', 'add']} />

        <div style={{ padding: '0 20px 12px' }}>
          <div style={{ ...TYPE.body, fontSize: 14, color: M3.onSurfaceVariant, lineHeight: 1.5 }}>
            Add labels to tasks to filter across lists. Tap a label to view every task tagged.
          </div>
        </div>

        {/* Tag cloud */}
        <div style={{ padding: '8px 20px 8px', display: 'flex', flexWrap: 'wrap', gap: 8 }}>
          {labels.map((l, i) => (
            <div key={i} style={{
              height: 36, padding: '0 14px', borderRadius: SHAPE.xs,
              background: l.color, color: '#2a1a14',
              display: 'inline-flex', alignItems: 'center', gap: 8,
              ...TYPE.label, fontSize: 14, fontWeight: 500,
            }}>
              <Icon name="tag" size={14} color="#2a1a14" />
              {l.name}
              <span style={{ opacity: 0.6, fontSize: 12, fontWeight: 600 }}>{l.count}</span>
            </div>
          ))}
        </div>

        <SectionHeader title="Recently used" action="Edit" />
        <div style={{ flex: 1, overflow: 'hidden' }}>
          {labels.slice(0, 4).map((l, i) => (
            <div key={i} style={{
              display: 'flex', alignItems: 'center', gap: 14, padding: '12px 20px',
            }}>
              <div style={{ width: 36, height: 36, borderRadius: SHAPE.xs, background: l.color,
                display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Icon name="tag" size={16} color="#2a1a14" />
              </div>
              <div style={{ flex: 1 }}>
                <div style={{ ...TYPE.body, fontSize: 15, color: M3.onSurface, fontWeight: 500 }}>{l.name}</div>
                <div style={{ ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant, marginTop: 2 }}>
                  {l.count} tasks · used 2h ago
                </div>
              </div>
              <Icon name="more" size={20} color={M3.onSurfaceVariant} />
            </div>
          ))}
        </div>
      </div>
    </Phone>
  );
}

// ─── 9. SETTINGS ─────────────────────────────────────────────────
function ScreenSettings() {
  const rows = [
    { group: 'Account', items: [
      { icon: 'user', t: 'Profile', s: 'mira@orbital.co', end: 'chevron' },
      { icon: 'bell', t: 'Notifications', s: 'Daily summary, 8:00 AM', end: 'chevron' },
      { icon: 'calendar', t: 'Calendar sync', s: '2 calendars connected', end: 'chevron' },
    ]},
    { group: 'Appearance', items: [
      { icon: 'sparkle', t: 'Theme', s: 'Dynamic · Coral', end: 'chevron' },
      { icon: 'grid', t: 'Density', s: 'Comfortable', end: 'chevron' },
    ]},
    { group: 'Defaults', items: [
      { icon: 'folder', t: 'Default list', s: 'Inbox', end: 'chevron' },
      { icon: 'flag', t: 'Default priority', s: 'None', end: 'chevron' },
      { icon: 'repeat', t: 'Start of week', s: 'Monday', end: 'chevron' },
    ]},
  ];
  return (
    <Phone>
      <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        <TopBar title="Settings" large leading="back" trailing={['search']} />

        {/* Profile hero */}
        <div style={{ padding: '0 20px 20px' }}>
          <div style={{
            background: M3.tertiaryContainer, borderRadius: SHAPE.xl,
            padding: '18px 18px', display: 'flex', alignItems: 'center', gap: 14,
          }}>
            <Avatar initial="M" color={M3.tertiary} size={52} />
            <div style={{ flex: 1 }}>
              <div style={{ ...TYPE.title, fontSize: 17, color: M3.onTertiaryContainer, fontWeight: 600 }}>
                Mira Castellanos
              </div>
              <div style={{ ...TYPE.label, fontSize: 12, color: M3.onTertiaryContainer, opacity: 0.8 }}>
                Pro · 2-year streak · 1,402 tasks done
              </div>
            </div>
            <Btn label="Manage" variant="tonal" size="sm" color="primary" />
          </div>
        </div>

        <div style={{ flex: 1, overflow: 'hidden', padding: '0 20px' }}>
          {rows.map((g, i) => (
            <div key={i} style={{ marginBottom: 20 }}>
              <div style={{ ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant,
                fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.08em',
                margin: '4px 4px 8px' }}>{g.group}</div>
              <div style={{ background: M3.surfaceContainerLow, borderRadius: SHAPE.lg, overflow: 'hidden' }}>
                {g.items.map((r, j) => (
                  <div key={j} style={{
                    display: 'flex', alignItems: 'center', gap: 14, padding: '12px 14px',
                    borderBottom: j < g.items.length - 1 ? `1px solid ${M3.outlineVariant}` : 'none',
                  }}>
                    <div style={{ width: 36, height: 36, borderRadius: SHAPE.sm,
                      background: M3.surfaceContainerHigh,
                      display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                      <Icon name={r.icon} size={18} color={M3.onSurfaceVariant} />
                    </div>
                    <div style={{ flex: 1 }}>
                      <div style={{ ...TYPE.body, fontSize: 15, color: M3.onSurface, fontWeight: 500 }}>{r.t}</div>
                      <div style={{ ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant, marginTop: 2 }}>{r.s}</div>
                    </div>
                    {r.end === 'chevron' && <Icon name="chevron" size={16} color={M3.onSurfaceVariant} />}
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>
    </Phone>
  );
}

// ─── 10. DARK MODE (Today) ───────────────────────────────────────
function ScreenDark() {
  return (
    <Phone dark bg={M3.dSurface}>
      <div style={{ display: 'flex', flexDirection: 'column', height: '100%', color: M3.dOnSurface }}>
        {/* Hero */}
        <div style={{ padding: '12px 20px 20px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <IconBtn name="menu" color={M3.dOnSurface} />
            <div style={{ display: 'flex' }}>
              <IconBtn name="search" color={M3.dOnSurface} />
              <Avatar initial="M" color={M3.dPrimaryContainer} size={32} />
              <div style={{ width: 12 }} />
            </div>
          </div>
          <div style={{ padding: '16px 4px 0' }}>
            <div style={{ ...TYPE.label, fontSize: 13, color: M3.dPrimary, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.08em' }}>
              Thursday · Apr 18
            </div>
            <div style={{ ...TYPE.display, fontSize: 40, lineHeight: 1, marginTop: 6, color: M3.dOnSurface }}>
              Evening,<br/>Mira
            </div>
            <div style={{ display: 'flex', gap: 8, marginTop: 18 }}>
              <div style={{
                height: 44, padding: '0 18px', borderRadius: SHAPE.full,
                background: M3.dPrimaryContainer, color: M3.dOnPrimaryContainer,
                display: 'inline-flex', alignItems: 'center', gap: 10,
                ...TYPE.label, fontSize: 14, fontWeight: 600,
              }}>
                <span style={{
                  width: 28, height: 28, borderRadius: '50%', background: M3.dPrimary,
                  color: M3.dOnPrimary, display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                  fontSize: 14, fontWeight: 700,
                }}>2</span>
                tasks left
              </div>
              <div style={{
                height: 44, padding: '0 14px', borderRadius: SHAPE.full,
                background: M3.dSurfaceContainerHigh, color: M3.dOnSurface,
                display: 'inline-flex', alignItems: 'center', gap: 6,
                ...TYPE.label, fontSize: 13, fontWeight: 500,
              }}>
                <Icon name="sparkle" size={16} color={M3.dPrimary} />
                5 done
              </div>
            </div>
          </div>
        </div>

        <div style={{ flex: 1, overflow: 'hidden' }}>
          <div style={{ padding: '20px 20px 8px', ...TYPE.title, fontSize: 15, color: M3.dOnSurface }}>
            Evening · 2
          </div>
          {[
            { t: 'Submit expense report', time: '6:00 PM', list: 'Admin', color: M3.accentD, p: true },
            { t: 'Water plants', time: '7:30 PM', list: 'Home', color: M3.accentE },
          ].map((x, i) => (
            <div key={i} style={{ padding: '14px 20px', display: 'flex', gap: 14, alignItems: 'flex-start' }}>
              <Check checked={false} color={x.color} />
              <div style={{ flex: 1 }}>
                <div style={{ ...TYPE.body, fontSize: 16, color: M3.dOnSurface }}>{x.t}</div>
                <div style={{ display: 'flex', gap: 10, marginTop: 6, ...TYPE.label, fontSize: 12, color: M3.dOnSurfaceVariant }}>
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                    <Icon name="clock" size={13} color={M3.dOnSurfaceVariant} />
                    {x.time}
                  </span>
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                    <span style={{ width: 8, height: 8, borderRadius: '50%', background: x.color }} />
                    {x.list}
                  </span>
                </div>
              </div>
              {x.p && <Icon name="flag" size={18} color="#FFB4AB" />}
            </div>
          ))}

          {/* Completed block */}
          <div style={{ padding: '20px 20px 8px', ...TYPE.title, fontSize: 15, color: M3.dOnSurface, display: 'flex', justifyContent: 'space-between' }}>
            <span>Completed · 5</span>
            <Icon name="chevronDown" size={18} color={M3.dOnSurfaceVariant} />
          </div>
          {['Stand-up', 'Ship release notes', 'Gym'].map((t, i) => (
            <div key={i} style={{ padding: '10px 20px', display: 'flex', gap: 14, alignItems: 'center' }}>
              <Check checked color={M3.dPrimary} />
              <div style={{ ...TYPE.body, fontSize: 15, color: M3.dOnSurfaceVariant, textDecoration: 'line-through', opacity: 0.7 }}>{t}</div>
            </div>
          ))}
        </div>

        {/* FAB + bottom nav (dark variant) */}
        <div style={{ position: 'absolute', bottom: 96, right: 20 }}>
          <div style={{
            height: 56, padding: '0 22px 0 18px', borderRadius: SHAPE.lg,
            background: M3.dPrimaryContainer, color: M3.dOnPrimaryContainer,
            display: 'inline-flex', alignItems: 'center', gap: 10,
            boxShadow: ELEV.l3, ...TYPE.label, fontSize: 16, fontWeight: 600,
          }}>
            <Icon name="add" size={24} color={M3.dOnPrimaryContainer} />
            New task
          </div>
        </div>

        <div style={{
          height: 80, background: M3.dSurfaceContainer,
          display: 'flex', padding: '12px 8px', gap: 4,
        }}>
          {[
            { icon: 'today', label: 'Today', active: true },
            { icon: 'upcoming', label: 'Upcoming' },
            { icon: 'inbox', label: 'Inbox' },
            { icon: 'search', label: 'Browse' },
          ].map((it, i) => (
            <div key={i} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
              <div style={{
                height: 32, padding: '0 20px', borderRadius: SHAPE.full,
                background: it.active ? M3.dPrimaryContainer : 'transparent',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <Icon name={it.icon} size={22} color={it.active ? M3.dOnPrimaryContainer : M3.dOnSurfaceVariant} filled={it.active} />
              </div>
              <div style={{ ...TYPE.label, fontSize: 11, fontWeight: it.active ? 600 : 500,
                color: it.active ? M3.dOnSurface : M3.dOnSurfaceVariant }}>{it.label}</div>
            </div>
          ))}
        </div>
      </div>
    </Phone>
  );
}

Object.assign(window, {
  ScreenListDetail, ScreenTaskDetail, ScreenNewTask,
  ScreenSearch, ScreenLabels, ScreenSettings, ScreenDark,
});
