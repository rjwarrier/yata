// YATA — lively shared widgets + motion primitives

// ── Motion ──
const EASE = {
  emphasized:'cubic-bezier(0.2, 0, 0, 1)',
  emphDecel: 'cubic-bezier(0.05, 0.7, 0.1, 1)',
  emphAccel: 'cubic-bezier(0.3, 0, 0.8, 0.15)',
  standard:  'cubic-bezier(0.2, 0, 0, 1)',
  spring:    'cubic-bezier(0.34, 1.56, 0.64, 1)',
  bounce:    'cubic-bezier(0.68, -0.6, 0.32, 1.6)',
};
const DUR = { nav:380, sheet:340, fade:200, micro:140 };

// Springy press wrapper
function Press({ children, onClick, style, disabled, scale = 0.96 }) {
  const [down, setDown] = React.useState(false);
  return (
    <div
      onPointerDown={() => !disabled && setDown(true)}
      onPointerUp={() => setDown(false)}
      onPointerLeave={() => setDown(false)}
      onClick={!disabled ? onClick : undefined}
      style={{
        cursor: disabled ? 'default' : 'pointer',
        transition:`transform ${DUR.micro}ms ${EASE.spring}`,
        transform: down ? `scale(${scale})` : 'scale(1)',
        ...style,
      }}
    >{children}</div>
  );
}

// ── Progress ring (SVG) ──
function ProgressRing({ pct, size = 44, stroke = 5, color, track, children, label }) {
  const r = (size - stroke) / 2;
  const circ = 2 * Math.PI * r;
  const off = circ * (1 - Math.max(0, Math.min(1, pct)));
  return (
    <div style={{ position:'relative', width:size, height:size, flexShrink:0 }}>
      <svg width={size} height={size} style={{ transform:'rotate(-90deg)' }}>
        <circle cx={size/2} cy={size/2} r={r} fill="none" stroke={track || M3.surfaceContainerHighest} strokeWidth={stroke} />
        <circle cx={size/2} cy={size/2} r={r} fill="none" stroke={color || M3.primary} strokeWidth={stroke}
          strokeLinecap="round" strokeDasharray={circ} strokeDashoffset={off}
          style={{ transition:`stroke-dashoffset ${DUR.nav}ms ${EASE.emphDecel}` }} />
      </svg>
      <div style={{ position:'absolute', inset:0, display:'flex', alignItems:'center', justifyContent:'center',
        ...TYPE.label, fontSize: size*0.26, fontWeight:700, color: color || M3.primary }}>
        {children != null ? children : (label != null ? label : `${Math.round(pct*100)}`)}
      </div>
    </div>
  );
}

// ── Person avatar (initials + color; supports photo url) ──
function PersonAvatar({ person, size = 32, ring, style }) {
  if (!person) return null;
  const bg = M3[person.color] || person.color || M3.tertiary;
  return (
    <div style={{
      width:size, height:size, borderRadius:'50%', background: person.photo ? undefined : bg,
      backgroundImage: person.photo ? `url(${person.photo})` : undefined, backgroundSize:'cover', backgroundPosition:'center',
      color: M3.onAccent, display:'inline-flex', alignItems:'center', justifyContent:'center',
      ...TYPE.label, fontSize:size*0.4, fontWeight:700, flexShrink:0,
      boxShadow: ring ? `0 0 0 2px ${ring}` : 'none', ...style,
    }}>{!person.photo && person.initials}</div>
  );
}

// ── Overlapping assignee stack ──
function AssigneeStack({ people, size = 24, max = 3 }) {
  if (!people || !people.length) return null;
  const shown = people.slice(0, max);
  const extra = people.length - shown.length;
  return (
    <div style={{ display:'inline-flex', alignItems:'center' }}>
      {shown.map((p, i) => (
        <div key={p.id} style={{ marginLeft: i ? -size*0.32 : 0, zIndex: shown.length - i }}>
          <PersonAvatar person={p} size={size} ring={M3.surface} />
        </div>
      ))}
      {extra > 0 && (
        <div style={{ marginLeft:-size*0.32, width:size, height:size, borderRadius:'50%',
          background:M3.surfaceContainerHighest, color:M3.onSurfaceVariant, boxShadow:`0 0 0 2px ${M3.surface}`,
          display:'inline-flex', alignItems:'center', justifyContent:'center', ...TYPE.label, fontSize:size*0.36, fontWeight:700 }}>
          +{extra}
        </div>
      )}
    </div>
  );
}

// ── Tag chip (soft tinted) ──
function TagChip({ tag, size = 'md', onRemove, onClick }) {
  if (!tag) return null;
  const col = M3[tag.color] || tag.color || M3.primary;
  const h = size === 'sm' ? 24 : 30;
  return (
    <Press onClick={onClick} style={{ display:'inline-flex' }}>
      <div style={{
        height:h, padding: size==='sm' ? '0 9px' : '0 11px', borderRadius:SHAPE.full,
        background: withAlpha(col, 0.16), color: col,
        display:'inline-flex', alignItems:'center', gap:5,
        ...TYPE.label, fontSize: size==='sm'?12:13, fontWeight:600, whiteSpace:'nowrap',
      }}>
        <span style={{ width:6, height:6, borderRadius:'50%', background:col }} />
        {tag.name}
        {onRemove && <span onClick={(e)=>{e.stopPropagation();onRemove();}} style={{ display:'inline-flex', marginLeft:1 }}><Icon name="close" size={12} color={col} /></span>}
      </div>
    </Press>
  );
}

// ── Recurrence badge ──
function RecurrenceBadge({ recurrence, compact }) {
  if (!recurrence) return null;
  return (
    <div style={{ display:'inline-flex', alignItems:'center', gap:4,
      ...TYPE.label, fontSize:12, color:M3.tertiary, fontWeight:600 }}>
      <Icon name="repeat" size={13} color={M3.tertiary} />
      {!compact && recurrenceSummary(recurrence)}
    </div>
  );
}

// ── Priority indicator (stacked bars) ──
function PriorityBars({ priority, size = 14 }) {
  const meta = PRIORITY[priority] || PRIORITY.none;
  if (!meta.dots) return null;
  const col = priorityColor(priority);
  return (
    <div style={{ display:'inline-flex', alignItems:'flex-end', gap:2, height:size }}>
      {[0,1,2].map(i => (
        <div key={i} style={{ width:3, borderRadius:2,
          height: size * (0.5 + i*0.25),
          background: i < meta.dots ? col : withAlpha(col, 0.22) }} />
      ))}
    </div>
  );
}

// ── Segmented control ──
function Segmented({ options, value, onChange, size = 'md' }) {
  const h = size === 'sm' ? 34 : 40;
  return (
    <div style={{ display:'flex', background:M3.surfaceContainerHigh, borderRadius:SHAPE.full, padding:3, gap:2 }}>
      {options.map(o => {
        const active = o.value === value;
        return (
          <Press key={o.value} onClick={() => onChange(o.value)} style={{ flex:1 }}>
            <div style={{ height:h-6, borderRadius:SHAPE.full,
              background: active ? M3.surface : 'transparent',
              boxShadow: active ? ELEV.l1 : 'none',
              display:'flex', alignItems:'center', justifyContent:'center', gap:6,
              ...TYPE.label, fontSize: size==='sm'?12:13, fontWeight:600,
              color: active ? M3.onSurface : M3.onSurfaceVariant,
              transition:`background ${DUR.fade}ms, color ${DUR.fade}ms` }}>
              {o.icon && <Icon name={o.icon} size={16} color={active?M3.primary:M3.onSurfaceVariant} />}
              {o.label}
            </div>
          </Press>
        );
      })}
    </div>
  );
}

// ── Confetti burst (celebration) ──
function Confetti({ trigger }) {
  const [bursts, setBursts] = React.useState([]);
  const seen = React.useRef(0);
  React.useEffect(() => {
    if (trigger && trigger > seen.current) {
      seen.current = trigger;
      const id = trigger;
      const cols = [M3.primary, M3.accentC, M3.accentD, M3.accentE, M3.accentF, M3.tertiary];
      const pieces = Array.from({ length: 26 }, (_, i) => ({
        id: i,
        x: (Math.random()*2-1)*130,
        y: -60 - Math.random()*150,
        rot: Math.random()*720-360,
        col: cols[i % cols.length],
        d: 3 + Math.random()*5,
        delay: Math.random()*40,
      }));
      setBursts(b => [...b, { id, pieces }]);
      setTimeout(() => setBursts(b => b.filter(x => x.id !== id)), 1100);
    }
  }, [trigger]);
  return (
    <div style={{ position:'absolute', left:'50%', top:'42%', pointerEvents:'none', zIndex:80 }}>
      {bursts.map(b => (
        <div key={b.id}>
          {b.pieces.map(p => (
            <span key={p.id} style={{
              position:'absolute', width:p.d, height:p.d*1.6, background:p.col, borderRadius:1,
              left:0, top:0, animation:`yataConf 1000ms ${p.delay}ms cubic-bezier(0.1,0.6,0.3,1) forwards`,
              ['--tx']:`${p.x}px`, ['--ty']:`${p.y}px`, ['--rot']:`${p.rot}deg`,
            }} />
          ))}
        </div>
      ))}
    </div>
  );
}

// ── Utility: hex → rgba with alpha ──
function withAlpha(hex, a) {
  if (!hex || hex[0] !== '#') return hex;
  let h = hex.slice(1);
  if (h.length === 3) h = h.split('').map(c=>c+c).join('');
  const n = parseInt(h, 16);
  return `rgba(${(n>>16)&255},${(n>>8)&255},${n&255},${a})`;
}

// Inject confetti keyframes + no-focus-outline once
(function injectYataCSS(){
  if (document.getElementById('yata-css')) return;
  const s = document.createElement('style');
  s.id = 'yata-css';
  s.textContent = `
    @keyframes yataConf { 0%{transform:translate(0,0) rotate(0);opacity:1;} 100%{transform:translate(var(--tx),var(--ty)) rotate(var(--rot));opacity:0;} }
    @keyframes yataPop { 0%{transform:scale(0.6);opacity:0;} 60%{transform:scale(1.08);} 100%{transform:scale(1);opacity:1;} }
    @keyframes yataSlideUp { from{transform:translateY(10px);opacity:0;} to{transform:translateY(0);opacity:1;} }
    input:focus, textarea:focus { outline:none; }
  `;
  document.head.appendChild(s);
})();

Object.assign(window, {
  EASE, DUR, Press, ProgressRing, PersonAvatar, AssigneeStack, TagChip,
  RecurrenceBadge, PriorityBars, Segmented, Confetti, withAlpha,
});
