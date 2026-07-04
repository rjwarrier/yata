// Shared UI primitives — M3 Expressive components
// Buttons, chips, FAB, list item, bottom nav, text field.

// ─── FAB (expressive: squircle, large) ───
function FAB({ label, icon = 'add', extended = false, color = 'primary', size = 'md' }) {
  const palette = {
    primary:   { bg: M3.primaryContainer,   fg: M3.onPrimaryContainer },
    secondary: { bg: M3.secondaryContainer, fg: M3.onSecondaryContainer },
    tertiary:  { bg: M3.tertiaryContainer,  fg: M3.onTertiaryContainer },
    surface:   { bg: M3.surfaceContainerHigh, fg: M3.onSurface },
  }[color];
  const dim = { sm: 40, md: 56, lg: 96 }[size];
  const radius = { sm: SHAPE.md, md: SHAPE.lg, lg: SHAPE.xl }[size];
  return (
    <div style={{
      height: dim, minWidth: dim, paddingLeft: extended ? 22 : 0, paddingRight: extended ? 26 : 0,
      borderRadius: radius, background: palette.bg, color: palette.fg,
      display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 12,
      boxShadow: ELEV.l3, cursor: 'pointer',
      ...TYPE.label, fontSize: 16, fontWeight: 600,
    }}>
      <Icon name={icon} size={size === 'lg' ? 32 : 24} color={palette.fg} />
      {extended && label}
    </div>
  );
}

// ─── Filter chip ───
function Chip({ label, icon, selected = false, tint, onRemove }) {
  const bg = selected ? (tint || M3.secondaryContainer) : 'transparent';
  const fg = selected ? M3.onSecondaryContainer : M3.onSurfaceVariant;
  const border = selected ? 'transparent' : M3.outlineVariant;
  return (
    <div style={{
      height: 32, padding: '0 12px', borderRadius: SHAPE.xs,
      background: bg, color: fg, border: `1px solid ${border}`,
      display: 'inline-flex', alignItems: 'center', gap: 6,
      ...TYPE.label, fontSize: 13, fontWeight: 500, whiteSpace: 'nowrap',
    }}>
      {selected && <Icon name="check" size={16} color={fg} />}
      {icon && !selected && <Icon name={icon} size={16} color={fg} />}
      {label}
      {onRemove && <Icon name="close" size={14} color={fg} />}
    </div>
  );
}

// ─── Checkbox (round, M3 Expressive) ───
function Check({ checked, color = M3.primary, size = 24 }) {
  return (
    <div style={{
      width: size, height: size, borderRadius: '50%',
      border: checked ? 'none' : `2px solid ${M3.outline}`,
      background: checked ? color : 'transparent',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      flexShrink: 0,
    }}>
      {checked && <Icon name="check" size={size - 8} color="#fff" />}
    </div>
  );
}

// ─── Task list item ───
function TaskItem({ title, meta, checked = false, priority, list, listColor, time, flag }) {
  const titleStyle = checked ? {
    textDecoration: 'line-through', color: M3.onSurfaceVariant, opacity: 0.7,
  } : { color: M3.onSurface };
  return (
    <div style={{
      display: 'flex', alignItems: 'flex-start', gap: 14,
      padding: '11px 20px', minHeight: 56,
    }}>
      <div style={{ paddingTop: 2 }}>
        <Check checked={checked} color={listColor || M3.primary} />
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ ...TYPE.body, fontSize: 16, lineHeight: '22px', ...titleStyle }}>
          {title}
        </div>
        {(meta || list || time) && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 6, flexWrap: 'wrap' }}>
            {time && (
              <div style={{ display: 'inline-flex', alignItems: 'center', gap: 4,
                ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant }}>
                <Icon name="clock" size={13} color={M3.onSurfaceVariant} />
                {time}
              </div>
            )}
            {list && (
              <div style={{ display: 'inline-flex', alignItems: 'center', gap: 4,
                ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant }}>
                <div style={{ width: 8, height: 8, borderRadius: '50%', background: listColor }} />
                {list}
              </div>
            )}
            {meta && (
              <span style={{ ...TYPE.label, fontSize: 12, color: M3.onSurfaceVariant }}>{meta}</span>
            )}
          </div>
        )}
      </div>
      {flag && <Icon name="flag" size={18} color={flag} />}
      {priority === 'high' && (
        <div style={{
          width: 8, height: 8, borderRadius: '50%', background: M3.error, marginTop: 8,
        }} />
      )}
    </div>
  );
}

// ─── Bottom nav (M3 Expressive) ───
function BottomNav({ active = 0, items }) {
  const def = items || [
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
      {def.map((it, i) => {
        const isActive = i === active;
        return (
          <div key={i} style={{
            flex: 1, display: 'flex', flexDirection: 'column',
            alignItems: 'center', justifyContent: 'center', gap: 4,
          }}>
            <div style={{
              height: 32, padding: '0 20px', borderRadius: SHAPE.full,
              background: isActive ? M3.secondaryContainer : 'transparent',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <Icon name={it.icon} size={22}
                color={isActive ? M3.onSecondaryContainer : M3.onSurfaceVariant}
                filled={isActive} />
            </div>
            <div style={{
              ...TYPE.label, fontSize: 11, fontWeight: isActive ? 600 : 500,
              color: isActive ? M3.onSurface : M3.onSurfaceVariant,
            }}>{it.label}</div>
          </div>
        );
      })}
    </div>
  );
}

// ─── Top app bar ───
function TopBar({ title, subtitle, leading = 'menu', trailing = ['search', 'more'], large = false, bg, fg }) {
  const bgc = bg || 'transparent';
  const fgc = fg || M3.onSurface;
  return (
    <div style={{ background: bgc, padding: large ? '8px 4px 16px' : '4px 4px' }}>
      <div style={{ height: 56, display: 'flex', alignItems: 'center', padding: '0 4px' }}>
        <IconBtn name={leading} color={fgc} />
        {!large && (
          <div style={{ flex: 1, paddingLeft: 4, ...TYPE.title, fontSize: 20, color: fgc }}>
            {title}
          </div>
        )}
        {large && <div style={{ flex: 1 }} />}
        {trailing.map((n, i) => <IconBtn key={i} name={n} color={fgc} />)}
      </div>
      {large && (
        <div style={{ padding: '14px 20px 4px' }}>
          <div style={{ ...TYPE.display, fontSize: 34, color: fgc, lineHeight: 1.1 }}>{title}</div>
          {subtitle && (
            <div style={{ ...TYPE.body, fontSize: 14, color: M3.onSurfaceVariant, marginTop: 4 }}>
              {subtitle}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function IconBtn({ name, color, size = 24, bg }) {
  return (
    <div style={{
      width: 48, height: 48, borderRadius: SHAPE.full,
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      background: bg || 'transparent', cursor: 'pointer',
    }}>
      <Icon name={name} size={size} color={color || M3.onSurfaceVariant} />
    </div>
  );
}

// ─── Section header ───
function SectionHeader({ title, count, action }) {
  return (
    <div style={{
      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      padding: '14px 20px 6px',
    }}>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
        <span style={{ ...TYPE.title, fontSize: 15, color: M3.onSurface }}>{title}</span>
        {count !== undefined && (
          <span style={{ ...TYPE.label, fontSize: 13, color: M3.onSurfaceVariant }}>{count}</span>
        )}
      </div>
      {action && (
        <span style={{ ...TYPE.label, fontSize: 13, color: M3.primary, fontWeight: 600 }}>{action}</span>
      )}
    </div>
  );
}

// ─── Text field (M3 filled) ───
function TextField({ label, value, supporting, leading, trailing, focused = false }) {
  return (
    <div style={{
      background: M3.surfaceContainerHighest,
      borderRadius: `${SHAPE.xs}px ${SHAPE.xs}px 0 0`,
      borderBottom: `${focused ? 2 : 1}px solid ${focused ? M3.primary : M3.outline}`,
      padding: '8px 16px', display: 'flex', alignItems: 'center', gap: 12,
      minHeight: 56,
    }}>
      {leading && <Icon name={leading} size={22} color={M3.onSurfaceVariant} />}
      <div style={{ flex: 1 }}>
        {label && (
          <div style={{
            ...TYPE.label, fontSize: focused || value ? 12 : 16,
            color: focused ? M3.primary : M3.onSurfaceVariant,
            transition: 'font-size 120ms',
          }}>{label}</div>
        )}
        {value && (
          <div style={{ ...TYPE.body, fontSize: 16, color: M3.onSurface, marginTop: 2 }}>
            {value}
          </div>
        )}
      </div>
      {trailing && <Icon name={trailing} size={22} color={M3.onSurfaceVariant} />}
    </div>
  );
}

// ─── Button (filled / tonal / outlined / text) ───
function Btn({ label, variant = 'filled', icon, color = 'primary', size = 'md' }) {
  const p = {
    primary:   { bg: M3.primary, fg: M3.onPrimary, tonal: M3.primaryContainer, tonalFg: M3.onPrimaryContainer },
    secondary: { bg: M3.secondary, fg: M3.onSecondary, tonal: M3.secondaryContainer, tonalFg: M3.onSecondaryContainer },
  }[color];
  const styles = {
    filled:   { bg: p.bg, fg: p.fg, border: 'transparent' },
    tonal:    { bg: p.tonal, fg: p.tonalFg, border: 'transparent' },
    outlined: { bg: 'transparent', fg: p.bg, border: M3.outline },
    text:     { bg: 'transparent', fg: p.bg, border: 'transparent' },
  }[variant];
  const h = { sm: 32, md: 40, lg: 56 }[size];
  return (
    <div style={{
      height: h, padding: '0 24px', borderRadius: SHAPE.full,
      background: styles.bg, color: styles.fg,
      border: `1px solid ${styles.border}`,
      display: 'inline-flex', alignItems: 'center', gap: 8,
      ...TYPE.label, fontSize: 14, fontWeight: 600,
    }}>
      {icon && <Icon name={icon} size={18} color={styles.fg} />}
      {label}
    </div>
  );
}

// ─── Avatar ───
function Avatar({ initial, color, size = 32 }) {
  return (
    <div style={{
      width: size, height: size, borderRadius: '50%',
      background: color, color: '#fff',
      display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
      ...TYPE.label, fontSize: size * 0.42, fontWeight: 600, flexShrink: 0,
    }}>{initial}</div>
  );
}

Object.assign(window, {
  FAB, Chip, Check, TaskItem, BottomNav, TopBar, IconBtn, SectionHeader, TextField, Btn, Avatar,
});
