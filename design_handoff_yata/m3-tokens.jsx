// YATA — Material 3 design tokens (light + dark, dynamic)
// Warm coral primary, olive secondary, lavender tertiary. Expressive shape scale.

// M3 is a live object; applyTheme() mutates it in place so every component that
// reads M3.xxx at render time picks up the active theme after a re-render.
const M3 = {};

const PALETTES = {
  light: {
    primary:'#8E4A3B', onPrimary:'#FFFFFF', primaryContainer:'#FFDAD1', onPrimaryContainer:'#3A0B01',
    secondary:'#5D6140', onSecondary:'#FFFFFF', secondaryContainer:'#E2E6BC', onSecondaryContainer:'#1B1D04',
    tertiary:'#5F5791', onTertiary:'#FFFFFF', tertiaryContainer:'#E5DEFF', onTertiaryContainer:'#1B1148',
    error:'#BA1A1A', onError:'#FFFFFF', errorContainer:'#FFDAD6', onErrorContainer:'#410002',
    success:'#3B7A57', successContainer:'#B9EBC9', onSuccessContainer:'#052014',
    surface:'#FFF8F6', surfaceDim:'#E8D6D1', surfaceBright:'#FFF8F6',
    surfaceContainerLowest:'#FFFFFF', surfaceContainerLow:'#FFF0EC', surfaceContainer:'#FCEAE4',
    surfaceContainerHigh:'#F6E4DE', surfaceContainerHighest:'#F0DED8',
    onSurface:'#231916', onSurfaceVariant:'#53433F', outline:'#85736E', outlineVariant:'#D8C2BC',
    scrim:'rgba(30,18,15,0.42)', elevTint:'35,25,22',
    // accents (list/tag/person colors)
    accentA:'#E8886B', accentB:'#9DAE55', accentC:'#8C7BE0', accentD:'#E0A93A',
    accentE:'#4FA97D', accentF:'#DB6FA0', accentG:'#4A93C7', accentH:'#C77B4A',
    onAccent:'#FFFFFF',
  },
  dark: {
    primary:'#FFB4A2', onPrimary:'#561F11', primaryContainer:'#723524', onPrimaryContainer:'#FFDAD1',
    secondary:'#C6CA9C', onSecondary:'#2F3213', secondaryContainer:'#454929', onSecondaryContainer:'#E2E6BC',
    tertiary:'#C8BFFF', onTertiary:'#31285F', tertiaryContainer:'#484078', onTertiaryContainer:'#E5DEFF',
    error:'#FFB4AB', onError:'#690005', errorContainer:'#93000A', onErrorContainer:'#FFDAD6',
    success:'#9ED8B4', successContainer:'#24503A', onSuccessContainer:'#B9EBC9',
    surface:'#191110', surfaceDim:'#191110', surfaceBright:'#413734',
    surfaceContainerLowest:'#130B0A', surfaceContainerLow:'#221816', surfaceContainer:'#261C1A',
    surfaceContainerHigh:'#312724', surfaceContainerHighest:'#3D322F',
    onSurface:'#F0DED8', onSurfaceVariant:'#D8C2BC', outline:'#A08C87', outlineVariant:'#53433F',
    scrim:'rgba(0,0,0,0.55)', elevTint:'0,0,0',
    accentA:'#E8886B', accentB:'#9DAE55', accentC:'#A99BEE', accentD:'#E0A93A',
    accentE:'#5CBB8C', accentF:'#E080AC', accentG:'#5CA3D4', accentH:'#D48C5C',
    onAccent:'#1A1110',
  },
};

function applyTheme(name) {
  Object.keys(M3).forEach(k => { delete M3[k]; });
  Object.assign(M3, PALETTES[name] || PALETTES.light);
  M3.__theme = name;
}
applyTheme('light');

const SHAPE = { xs:8, sm:12, md:16, lg:20, xl:28, full:9999 };

const ELEV = {
  get l0(){ return 'none'; },
  get l1(){ return `0 1px 2px rgba(${M3.elevTint},0.10), 0 1px 3px 1px rgba(${M3.elevTint},0.06)`; },
  get l2(){ return `0 1px 2px rgba(${M3.elevTint},0.10), 0 2px 6px 2px rgba(${M3.elevTint},0.08)`; },
  get l3(){ return `0 4px 8px 3px rgba(${M3.elevTint},0.10), 0 1px 3px rgba(${M3.elevTint},0.14)`; },
};

const TYPE = {
  display:  { fontFamily:'"Inter Tight", Inter, system-ui, sans-serif', fontWeight:500, letterSpacing:'-0.02em' },
  headline: { fontFamily:'"Inter Tight", Inter, system-ui, sans-serif', fontWeight:500, letterSpacing:'-0.01em' },
  title:    { fontFamily:'"Inter Tight", Inter, system-ui, sans-serif', fontWeight:600, letterSpacing:'-0.005em' },
  body:     { fontFamily:'Inter, system-ui, sans-serif', fontWeight:400 },
  label:    { fontFamily:'Inter, system-ui, sans-serif', fontWeight:500, letterSpacing:'0.01em' },
  mono:     { fontFamily:'"JetBrains Mono", ui-monospace, monospace' },
};

// M3 Icon — minimal line icons (geometric primitives only)
function Icon({ name, size = 24, color = 'currentColor', filled = false }) {
  const sw = filled ? 0 : 1.8;
  const c = { width:size, height:size, viewBox:'0 0 24 24', fill:'none', stroke:color, strokeWidth:sw, strokeLinecap:'round', strokeLinejoin:'round' };
  switch (name) {
    case 'menu':     return <svg {...c}><path d="M3 7h18M3 12h18M3 17h18"/></svg>;
    case 'search':   return <svg {...c}><circle cx="10.5" cy="10.5" r="6.5"/><path d="M20 20l-4.8-4.8"/></svg>;
    case 'add':      return <svg {...c}><path d="M12 5v14M5 12h14"/></svg>;
    case 'check':    return <svg {...c}><path d="M5 12.5l4.5 4.5L19 7.5"/></svg>;
    case 'more':     return <svg {...c}><circle cx="5" cy="12" r="1.6" fill={color}/><circle cx="12" cy="12" r="1.6" fill={color}/><circle cx="19" cy="12" r="1.6" fill={color}/></svg>;
    case 'back':     return <svg {...c}><path d="M19 12H5M11 6l-6 6 6 6"/></svg>;
    case 'close':    return <svg {...c}><path d="M6 6l12 12M18 6L6 18"/></svg>;
    case 'filter':   return <svg {...c}><path d="M4 6h16M7 12h10M10 18h4"/></svg>;
    case 'sort':     return <svg {...c}><path d="M7 4v16M3 8l4-4 4 4M17 20V4M13 16l4 4 4-4"/></svg>;
    case 'star':     return <svg {...c} fill={filled ? color : 'none'}><path d="M12 3l2.7 6 6.3.6-4.8 4.3 1.5 6.4L12 17l-5.7 3.3 1.5-6.4L3 9.6 9.3 9z"/></svg>;
    case 'calendar': return <svg {...c}><rect x="3.5" y="5" width="17" height="15.5" rx="2"/><path d="M3.5 10h17M8 3v4M16 3v4"/></svg>;
    case 'clock':    return <svg {...c}><circle cx="12" cy="12" r="8.5"/><path d="M12 7v5l3 2"/></svg>;
    case 'list':     return <svg {...c}><path d="M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01"/></svg>;
    case 'grid':     return <svg {...c}><rect x="3.5" y="3.5" width="7" height="7" rx="1.5"/><rect x="13.5" y="3.5" width="7" height="7" rx="1.5"/><rect x="3.5" y="13.5" width="7" height="7" rx="1.5"/><rect x="13.5" y="13.5" width="7" height="7" rx="1.5"/></svg>;
    case 'home':     return <svg {...c}><path d="M4 11l8-7 8 7v9a1 1 0 01-1 1h-4v-6h-6v6H5a1 1 0 01-1-1z"/></svg>;
    case 'tag':      return <svg {...c}><path d="M12.5 3h7.5v7.5L10 20l-7.5-7.5z"/><circle cx="16" cy="8" r="1.3" fill={color}/></svg>;
    case 'tags':     return <svg {...c}><path d="M9 3h6v6l-6 6-6-6z"/><path d="M14 6l5 5-6 6"/><circle cx="11.5" cy="6.5" r="1" fill={color}/></svg>;
    case 'inbox':    return <svg {...c}><path d="M3.5 13h5l1.5 2.5h4L15.5 13h5M4 13l2.5-8h11l2.5 8v6a1 1 0 01-1 1h-14a1 1 0 01-1-1z"/></svg>;
    case 'today':    return <svg {...c}><rect x="3.5" y="5" width="17" height="15.5" rx="2"/><path d="M3.5 10h17"/><circle cx="12" cy="15" r="2.5" fill={color}/></svg>;
    case 'upcoming': return <svg {...c}><path d="M4 6l8 8 8-8M4 14l8 8 8-8"/></svg>;
    case 'flag':     return <svg {...c}><path d="M5 21V4M5 4h11l-2 4 2 4H5"/></svg>;
    case 'bell':     return <svg {...c}><path d="M6 16V11a6 6 0 0112 0v5l1.5 2.5h-15zM10 20.5a2 2 0 004 0"/></svg>;
    case 'folder':   return <svg {...c}><path d="M3.5 7.5v11a1 1 0 001 1h15a1 1 0 001-1v-9a1 1 0 00-1-1h-8l-2-2h-5a1 1 0 00-1 1z"/></svg>;
    case 'settings': return <svg {...c}><circle cx="12" cy="12" r="3"/><path d="M12 3v2.5M12 18.5V21M3 12h2.5M18.5 12H21M5.6 5.6l1.8 1.8M16.6 16.6l1.8 1.8M5.6 18.4l1.8-1.8M16.6 7.4l1.8-1.8"/></svg>;
    case 'edit':     return <svg {...c}><path d="M4 20h4l11-11-4-4L4 16z"/></svg>;
    case 'delete':   return <svg {...c}><path d="M5 7h14M10 7V4h4v3M7 7l1 13h8l1-13M10 11v6M14 11v6"/></svg>;
    case 'arrow':    return <svg {...c}><path d="M5 12h14M13 6l6 6-6 6"/></svg>;
    case 'repeat':   return <svg {...c}><path d="M4 8V7a2 2 0 012-2h11l-3-3M20 16v1a2 2 0 01-2 2H7l3 3"/><path d="M17 5l3 3M7 19l-3-3" opacity="0"/></svg>;
    case 'attach':   return <svg {...c}><path d="M14 7l-6.5 6.5a3 3 0 104.2 4.2l7-7a5 5 0 10-7-7L5 10.5"/></svg>;
    case 'subtask':  return <svg {...c}><path d="M5 5v8a2 2 0 002 2h12M14 10l5 5-5 5"/></svg>;
    case 'archive':  return <svg {...c}><rect x="3" y="4" width="18" height="4" rx="1"/><path d="M5 8v11a1 1 0 001 1h12a1 1 0 001-1V8M10 13h4"/></svg>;
    case 'mic':      return <svg {...c}><rect x="9" y="3" width="6" height="12" rx="3"/><path d="M5 11a7 7 0 0014 0M12 18v3"/></svg>;
    case 'user':     return <svg {...c}><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0116 0"/></svg>;
    case 'people':   return <svg {...c}><circle cx="9" cy="8" r="3.4"/><path d="M3 20a6 6 0 0112 0"/><path d="M16 5.2a3.4 3.4 0 010 5.6M17.5 20a6 6 0 00-3-5.2"/></svg>;
    case 'personAdd':return <svg {...c}><circle cx="10" cy="8" r="3.6"/><path d="M3.5 20a6.5 6.5 0 0113 0"/><path d="M18 8v6M15 11h6"/></svg>;
    case 'chevron':  return <svg {...c}><path d="M9 6l6 6-6 6"/></svg>;
    case 'chevronDown': return <svg {...c}><path d="M6 9l6 6 6-6"/></svg>;
    case 'sparkle':  return <svg {...c}><path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8zM19 3l.8 2.2L22 6l-2.2.8L19 9l-.8-2.2L16 6l2.2-.8z" fill={color} stroke="none"/></svg>;
    case 'layers':   return <svg {...c}><path d="M12 3l9 5-9 5-9-5z"/><path d="M3 12l9 5 9-5M3 16l9 5 9-5"/></svg>;
    case 'sun':      return <svg {...c}><circle cx="12" cy="12" r="4.5"/><path d="M12 2v2.5M12 19.5V22M2 12h2.5M19.5 12H22M4.9 4.9l1.8 1.8M17.3 17.3l1.8 1.8M4.9 19.1l1.8-1.8M17.3 6.7l1.8-1.8"/></svg>;
    case 'moon':     return <svg {...c}><path d="M20 14.5A8 8 0 019.5 4 7 7 0 1020 14.5z"/></svg>;
    case 'camera':   return <svg {...c}><path d="M4 8h3l1.5-2.5h7L17 8h3a1 1 0 011 1v9a1 1 0 01-1 1H4a1 1 0 01-1-1V9a1 1 0 011-1z"/><circle cx="12" cy="13" r="3.2"/></svg>;
    case 'check2':   return <svg {...c}><circle cx="12" cy="12" r="9"/><path d="M8 12.2l2.6 2.6L16 9"/></svg>;
    case 'chart':    return <svg {...c}><path d="M4 20V10M10 20V4M16 20v-6M4 20h16"/></svg>;
    default: return null;
  }
}

Object.assign(window, { M3, PALETTES, applyTheme, SHAPE, ELEV, TYPE, Icon });
