// YATA — Material 3 Expressive design tokens
// Original palette. Warm coral primary, lime secondary, lavender tertiary.
// Expressive shape scale: XS 8, SM 12, MD 16, LG 20, XL 28, Full 9999.

const M3 = {
  // Primary (coral)
  primary:              '#8E4A3B',
  onPrimary:            '#FFFFFF',
  primaryContainer:     '#FFDAD1',
  onPrimaryContainer:   '#3A0B01',
  primaryFixed:         '#FFDAD1',
  primaryFixedDim:      '#FFB4A2',

  // Secondary (lime/olive)
  secondary:            '#5D6140',
  onSecondary:          '#FFFFFF',
  secondaryContainer:   '#E2E6BC',
  onSecondaryContainer: '#1B1D04',

  // Tertiary (lavender)
  tertiary:             '#5F5791',
  onTertiary:           '#FFFFFF',
  tertiaryContainer:    '#E5DEFF',
  onTertiaryContainer:  '#1B1148',

  // Error
  error:                '#BA1A1A',
  errorContainer:       '#FFDAD6',
  onErrorContainer:     '#410002',

  // Neutral surfaces (M3 tonal)
  surface:              '#FFF8F6',
  surfaceDim:           '#E8D6D1',
  surfaceBright:        '#FFF8F6',
  surfaceContainerLowest:'#FFFFFF',
  surfaceContainerLow:  '#FFF0EC',
  surfaceContainer:     '#FCEAE4',
  surfaceContainerHigh: '#F6E4DE',
  surfaceContainerHighest:'#F0DED8',
  onSurface:            '#231916',
  onSurfaceVariant:     '#53433F',
  outline:              '#85736E',
  outlineVariant:       '#D8C2BC',

  // Dark mode (for one screen demo)
  dSurface:             '#1A1110',
  dSurfaceContainer:    '#261C1A',
  dSurfaceContainerHigh:'#312724',
  dOnSurface:           '#F0DED8',
  dOnSurfaceVariant:    '#D8C2BC',
  dPrimary:             '#FFB4A2',
  dOnPrimary:           '#561F11',
  dPrimaryContainer:    '#723524',
  dOnPrimaryContainer:  '#FFDAD1',

  // Decorative "expressive" accents for list colors
  accentA:              '#F2B8A6', // peach
  accentB:              '#C7D08A', // lime
  accentC:              '#B8A8E8', // lavender
  accentD:              '#F5D06F', // mustard
  accentE:              '#8CC4A3', // sage
  accentF:              '#E69AB8', // rose
};

const SHAPE = {
  xs: 8, sm: 12, md: 16, lg: 20, xl: 28, full: 9999,
};

const ELEV = {
  l0: 'none',
  l1: '0 1px 2px rgba(35,25,22,0.10), 0 1px 3px 1px rgba(35,25,22,0.06)',
  l2: '0 1px 2px rgba(35,25,22,0.10), 0 2px 6px 2px rgba(35,25,22,0.08)',
  l3: '0 4px 8px 3px rgba(35,25,22,0.08), 0 1px 3px rgba(35,25,22,0.12)',
};

// Typography — Inter Tight (display) + Inter (body)
const TYPE = {
  display:  { fontFamily: '"Inter Tight", Inter, system-ui, sans-serif', fontWeight: 500, letterSpacing: '-0.02em' },
  headline: { fontFamily: '"Inter Tight", Inter, system-ui, sans-serif', fontWeight: 500, letterSpacing: '-0.01em' },
  title:    { fontFamily: '"Inter Tight", Inter, system-ui, sans-serif', fontWeight: 600, letterSpacing: '-0.005em' },
  body:     { fontFamily: 'Inter, system-ui, sans-serif', fontWeight: 400 },
  label:    { fontFamily: 'Inter, system-ui, sans-serif', fontWeight: 500, letterSpacing: '0.01em' },
  mono:     { fontFamily: '"JetBrains Mono", ui-monospace, monospace' },
};

// M3 Icon — minimal line icons (original, drawn from geometric primitives only)
function Icon({ name, size = 24, color = 'currentColor', filled = false }) {
  const sw = filled ? 0 : 1.8;
  const common = { width: size, height: size, viewBox: '0 0 24 24', fill: 'none', stroke: color, strokeWidth: sw, strokeLinecap: 'round', strokeLinejoin: 'round' };
  switch (name) {
    case 'menu':     return <svg {...common}><path d="M3 7h18M3 12h18M3 17h18"/></svg>;
    case 'search':   return <svg {...common}><circle cx="10.5" cy="10.5" r="6.5"/><path d="M20 20l-4.8-4.8"/></svg>;
    case 'add':      return <svg {...common}><path d="M12 5v14M5 12h14"/></svg>;
    case 'check':    return <svg {...common}><path d="M5 12.5l4.5 4.5L19 7.5"/></svg>;
    case 'more':     return <svg {...common}><circle cx="5" cy="12" r="1.6" fill={color}/><circle cx="12" cy="12" r="1.6" fill={color}/><circle cx="19" cy="12" r="1.6" fill={color}/></svg>;
    case 'back':     return <svg {...common}><path d="M19 12H5M11 6l-6 6 6 6"/></svg>;
    case 'close':    return <svg {...common}><path d="M6 6l12 12M18 6L6 18"/></svg>;
    case 'filter':   return <svg {...common}><path d="M4 6h16M7 12h10M10 18h4"/></svg>;
    case 'sort':     return <svg {...common}><path d="M7 4v16M3 8l4-4 4 4M17 20V4M13 16l4 4 4-4"/></svg>;
    case 'star':     return <svg {...common} fill={filled ? color : 'none'}><path d="M12 3l2.7 6 6.3.6-4.8 4.3 1.5 6.4L12 17l-5.7 3.3 1.5-6.4L3 9.6 9.3 9z"/></svg>;
    case 'calendar': return <svg {...common}><rect x="3.5" y="5" width="17" height="15.5" rx="2"/><path d="M3.5 10h17M8 3v4M16 3v4"/></svg>;
    case 'clock':    return <svg {...common}><circle cx="12" cy="12" r="8.5"/><path d="M12 7v5l3 2"/></svg>;
    case 'list':     return <svg {...common}><path d="M4 6h16M4 12h16M4 18h16"/></svg>;
    case 'grid':     return <svg {...common}><rect x="3.5" y="3.5" width="7" height="7" rx="1.5"/><rect x="13.5" y="3.5" width="7" height="7" rx="1.5"/><rect x="3.5" y="13.5" width="7" height="7" rx="1.5"/><rect x="13.5" y="13.5" width="7" height="7" rx="1.5"/></svg>;
    case 'home':     return <svg {...common}><path d="M4 11l8-7 8 7v9a1 1 0 01-1 1h-4v-6h-6v6H5a1 1 0 01-1-1z"/></svg>;
    case 'tag':      return <svg {...common}><path d="M12.5 3h7.5v7.5L10 20l-7.5-7.5z"/><circle cx="16" cy="8" r="1.3" fill={color}/></svg>;
    case 'inbox':    return <svg {...common}><path d="M3.5 13h5l1.5 2.5h4L15.5 13h5M4 13l2.5-8h11l2.5 8v6a1 1 0 01-1 1h-14a1 1 0 01-1-1z"/></svg>;
    case 'today':    return <svg {...common}><rect x="3.5" y="5" width="17" height="15.5" rx="2"/><path d="M3.5 10h17"/><circle cx="12" cy="15" r="2.5" fill={color}/></svg>;
    case 'upcoming': return <svg {...common}><path d="M4 6l8 8 8-8M4 14l8 8 8-8"/></svg>;
    case 'flag':     return <svg {...common}><path d="M5 21V4M5 4h11l-2 4 2 4H5"/></svg>;
    case 'bell':     return <svg {...common}><path d="M6 16V11a6 6 0 0112 0v5l1.5 2.5h-15zM10 20.5a2 2 0 004 0"/></svg>;
    case 'folder':   return <svg {...common}><path d="M3.5 7.5v11a1 1 0 001 1h15a1 1 0 001-1v-9a1 1 0 00-1-1h-8l-2-2h-5a1 1 0 00-1 1z"/></svg>;
    case 'settings': return <svg {...common}><circle cx="12" cy="12" r="3"/><path d="M12 3v2.5M12 18.5V21M3 12h2.5M18.5 12H21M5.6 5.6l1.8 1.8M16.6 16.6l1.8 1.8M5.6 18.4l1.8-1.8M16.6 7.4l1.8-1.8"/></svg>;
    case 'edit':     return <svg {...common}><path d="M4 20h4l11-11-4-4L4 16z"/></svg>;
    case 'delete':   return <svg {...common}><path d="M5 7h14M10 7V4h4v3M7 7l1 13h8l1-13M10 11v6M14 11v6"/></svg>;
    case 'arrow':    return <svg {...common}><path d="M5 12h14M13 6l6 6-6 6"/></svg>;
    case 'repeat':   return <svg {...common}><path d="M4 7h12l-3-3M20 17H8l3 3"/></svg>;
    case 'attach':   return <svg {...common}><path d="M14 7l-6.5 6.5a3 3 0 104.2 4.2l7-7a5 5 0 10-7-7L5 10.5"/></svg>;
    case 'subtask':  return <svg {...common}><path d="M5 5v8a2 2 0 002 2h12M14 10l5 5-5 5"/></svg>;
    case 'priority':return <svg {...common}><path d="M6 3v18M6 4h12l-3 4 3 4H6"/></svg>;
    case 'archive': return <svg {...common}><rect x="3" y="4" width="18" height="4" rx="1"/><path d="M5 8v11a1 1 0 001 1h12a1 1 0 001-1V8M10 13h4"/></svg>;
    case 'mic':     return <svg {...common}><rect x="9" y="3" width="6" height="12" rx="3"/><path d="M5 11a7 7 0 0014 0M12 18v3"/></svg>;
    case 'user':    return <svg {...common}><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0116 0"/></svg>;
    case 'chevron': return <svg {...common}><path d="M9 6l6 6-6 6"/></svg>;
    case 'chevronDown': return <svg {...common}><path d="M6 9l6 6 6-6"/></svg>;
    case 'sparkle': return <svg {...common}><path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8zM19 3l.8 2.2L22 6l-2.2.8L19 9l-.8-2.2L16 6l2.2-.8z" fill={color} stroke="none"/></svg>;
    default: return null;
  }
}

Object.assign(window, { M3, SHAPE, ELEV, TYPE, Icon });
