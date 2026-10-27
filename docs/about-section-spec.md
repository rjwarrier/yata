# App and Developer About Section

This specification reproduces YATA's app/developer About panel in another app. It describes the About panel under Settings → Help & About, rather than the editable user Profile card.

## Structure

Arrange the content vertically in this order:

1. Centered app identity card.
2. Two equal-width buttons: source code and share.
3. Full-width website button.
4. Full-width Support Dev button.
5. Other apps card containing three equal-width app tiles.

Use a vertically scrollable parent, theme-aware colors, and a bounded content width on larger screens. Preserve the host app's normal back navigation.

```text
┌──────────────────────────────────────┐
│              [RJ logo]               │
│                YATA                  │
│        yet another todo app          │
│     [vVERSION · Build BUILD_ID]       │
│ ──────────────────────────────────── │
│ From the Labs of RJ | Vibe Coded      │
│             Made in 🇮🇳              │
└──────────────────────────────────────┘

[View source on GitHub]  [Share APP]
[              APP website           ]
[              ☕ Support Dev        ]

┌──────────────────────────────────────┐
│ ✨ Other apps by developers          │
│ [ yaja ]   [ Assetrack ]   [ Ultra ] │
│ Journaling  Track your     Smart     │
│ app         assets         reminders│
└──────────────────────────────────────┘
```

## App Identity Card

### Container

- Full available content width.
- Corner radius: **16 dp**.
- Background: Material 3 `surfaceContainerLow`.
- Inner padding: **24 dp**.
- Center all children horizontally.
- Default vertical spacing between children: **4 dp**.

### Logo

- Circular background: **64 × 64 dp**, using `primaryContainer`.
- Center the RJ logo mark at **44 × 29 dp**.
- Tint the mark with `onPrimaryContainer`.
- For another brand, substitute its logo while preserving the circular treatment.

### App Name and Tagline

- App name: **YATA**; use the other app's own name when adapting.
- Name typography: `headlineSmall`, bold, **Bodoni Moda** serif.
- Name color: `onSurface`.
- Tagline: **yet another todo app**.
- Tagline typography: `bodyMedium`.
- Tagline color: `onSurfaceVariant`.

### Version Badge

- Add an extra **8 dp** spacer before the badge.
- Capsule shape with `surfaceContainerHighest` background.
- Text padding: **14 dp horizontal, 6 dp vertical**.
- Typography: `labelMedium`, bold; color: `onSurface`.
- Format: `v{versionName}  ·  Build {versionCode}.{buildTimestamp}`.
- YATA's build timestamp uses `ddMMyyyyHHmm`, generated for each build.
- Read the version and build identifier from build metadata rather than hardcoding them.

### Developer Credit

- Add an extra **12 dp** spacer before a horizontal divider.
- Divider color: `outlineVariant` at **40% opacity**.
- Add an extra **8 dp** spacer after the divider.
- Credit: **From the Labs of RJ | Vibe Coded**.
- Credit typography: `labelMedium`, bold.
- Country line: **Made in 🇮🇳**.
- Country typography: `labelMedium`, normal weight.
- Both lines use `onSurfaceVariant`.

## Action Buttons

Use **8 dp** spacing between rows and between the two buttons in the first row.

| Action | Appearance | Icon | Behavior |
| --- | --- | --- | --- |
| View source on GitHub | Outlined; half width | Open in new | Open the app's repository URL |
| Share APP | Outlined; half width | Share | Navigate to the app's share screen |
| APP website | Filled tonal; full width | Open in new | Open the app's website URL |
| Support Dev | Filled; full width | Coffee cup | Open the developer's Buy Me a Coffee page |

- Source and share buttons have equal height, accommodating wrapped labels.
- Icons: **16 dp**; spacing between icon and text: **6 dp**.
- Button text: `labelLarge`, centered.
- Support Dev text is bold.
- Support Dev uses fixed brand colors: **#FFDD00 background, black text and icons, 1 dp black border**. Keep these colors in light and dark themes.

YATA's configured destinations:

| Destination | URL |
| --- | --- |
| Source | https://github.com/rjwarrier/yata |
| Website | https://ranjithj.in/yata/ |
| Support | https://www.buymeacoffee.com/ranjithj |

## Other Apps Card

### Container and Heading

- Full width; **16 dp** corner radius and **16 dp** padding.
- Background: `surfaceContainer`.
- Border: **1 dp**, `outlineVariant` at **40% opacity**.
- Vertical spacing: **10 dp**.
- Heading: **Other apps by developers**, `labelMedium`, bold, `onSurface`.
- Precede the heading with a **16 dp** sparkle icon in `primary`, separated by **6 dp**.

### App Tiles

- Three equal-width tiles in one row, with **8 dp** gaps and matching heights.
- Tile corner radius: **14 dp**.
- Assign a different theme accent color to each tile.
- Tile background: accent at **12% opacity**.
- Tile border: **1 dp**, accent at **30% opacity**.
- Tile padding: **8 dp horizontal, 12 dp vertical**.
- Center content horizontally, with **6 dp** vertical spacing.
- Icon circle: **36 dp**, accent background at **22% opacity**.
- Icon: **18 dp**, tinted with the accent.
- App name: `bodyMedium`, bold, centered, `onSurface`.
- Tagline: `labelSmall`, centered, `onSurfaceVariant`.
- Make the whole tile clickable and open its Play Store URL.

| App | Tagline | Icon | Play Store package |
| --- | --- | --- | --- |
| yaja | Journaling app | Book | `com.mj.yaja` |
| Assetrack | Track your assets | Inventory | `com.mj.assetrack` |
| Ultra | Smart reminders | Alarm | `com.ultra.reminders` |

Build each store URL as `https://play.google.com/store/apps/details?id={packageName}`.

## Animation

- Reveal the action-button block with a fade-in and upward slide on first composition.
- Reveal the Other apps card with the same animation after **60 ms**.
- Start the slide at one-quarter of the content's height below its final position.
- Use the host app's navigation animation duration and emphasized-deceleration easing.

## Optional YATA-Specific Behavior

The circular RJ logo is also a developer demo-mode toggle. Tapping it calls the demo-mode action; an active-state label and transient feedback can appear beneath it. This is optional when adapting the design and requires the destination app's own demo-data implementation.

## Reuse Checklist

- Replace the app name, tagline, logo, repository, website, and share destination.
- Keep the RJ credit, country line, support URL, and other-app entries only if they apply to the destination app.
- Localize labels and use placeholders for version/build values.
- Preserve meaningful accessible button names; decorative icons should not duplicate spoken labels.
- Allow text wrapping and check the three-tile layout at large font sizes. A stacked layout on narrow screens is a recommended adaptation, rather than current YATA behavior.
- Keep colors tied to theme roles except the branded Support Dev button.

## Source Reference

The implementation is in `app/src/main/java/com/mj/yata/ui/screen/settings/SettingsScreen.kt`, primarily `AboutYataCard`, `GitHubAndShareRow`, `OtherAppsCard`, and `AboutEntrance`. Labels are defined in `app/src/main/res/values/strings.xml`.
