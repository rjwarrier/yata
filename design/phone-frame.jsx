// Custom Android-ish phone frame — lets each screen supply its own app bar / nav.

function StatusBar({ dark = false, bg }) {
  const c = dark ? '#F0DED8' : '#231916';
  return (
    <div style={{
      height: 36, display: 'flex', alignItems: 'center',
      justifyContent: 'space-between', padding: '0 20px',
      position: 'relative', background: bg || 'transparent',
      fontFamily: 'Inter, system-ui, sans-serif', flexShrink: 0,
    }}>
      <span style={{ fontSize: 14, fontWeight: 600, color: c, letterSpacing: 0.2 }}>9:41</span>
      <div style={{
        position: 'absolute', left: '50%', top: 8, transform: 'translateX(-50%)',
        width: 22, height: 22, borderRadius: 100, background: '#1a1a1a',
      }} />
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        {/* signal */}
        <svg width="16" height="12" viewBox="0 0 16 12"><path d="M1 11h2V8H1zM5 11h2V6H5zM9 11h2V4H9zM13 11h2V2h-2z" fill={c}/></svg>
        {/* wifi */}
        <svg width="16" height="12" viewBox="0 0 16 12"><path d="M8 11.2L.5 3.7a11 11 0 0115 0L8 11.2z" fill={c}/></svg>
        {/* battery */}
        <svg width="24" height="12" viewBox="0 0 24 12"><rect x="1" y="1.5" width="20" height="9" rx="2" fill="none" stroke={c} strokeWidth="1.2"/><rect x="3" y="3" width="16" height="6" rx="1" fill={c}/><rect x="21.5" y="4.5" width="1.8" height="3" rx="0.6" fill={c}/></svg>
      </div>
    </div>
  );
}

function NavBar({ dark = false, bg }) {
  return (
    <div style={{
      height: 28, display: 'flex', alignItems: 'center', justifyContent: 'center',
      background: bg || 'transparent', flexShrink: 0,
    }}>
      <div style={{
        width: 120, height: 4, borderRadius: 2,
        background: dark ? '#F0DED8' : '#231916', opacity: 0.9,
      }} />
    </div>
  );
}

function Phone({ children, dark = false, bg }) {
  const surface = bg || (dark ? M3.dSurface : M3.surface);
  return (
    <div style={{
      width: 380, height: 820, borderRadius: 44,
      background: surface,
      border: `10px solid #1a1a1a`,
      boxShadow: '0 24px 60px rgba(35,25,22,0.22), 0 2px 6px rgba(35,25,22,0.12)',
      display: 'flex', flexDirection: 'column', boxSizing: 'border-box',
      overflow: 'hidden', position: 'relative',
    }}>
      <StatusBar dark={dark} bg={surface} />
      <div style={{ flex: 1, overflow: 'hidden', position: 'relative', background: surface }}>
        {children}
      </div>
      <NavBar dark={dark} bg={surface} />
    </div>
  );
}

Object.assign(window, { Phone, StatusBar, NavBar });
