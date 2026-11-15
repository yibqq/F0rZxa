# Device classes and platform notes

One APK serves phones, foldables, tablets and Android TV. Touch and the TV feature are both
declared optional in the manifest.

## Phones, foldables, tablets (touch)

Layout is chosen from the **current window width**, using the official window size classes
(compact < 600 dp, medium 600–839 dp, expanded ≥ 840 dp). Resource qualifiers (`layout-w600dp`,
`layout-w840dp`, `values-w600dp`, `values-w840dp`) follow split-screen, freeform windows and
fold/unfold changes automatically.

| | Compact | Medium | Expanded |
|---|---|---|---|
| Navigation | Bottom bar | Side rail | Side rail |
| Grids | Column count computed from the real width and a minimum card width (`GridAutoFit`) | ← | ← |
| Details | Single column | Single column | Two columns (identity/actions + story/facts), episodes in two columns |
| Search / My List | Full-screen details | Full-screen details | **Master–detail**: list + details pane side by side |

State survives resizing and rotation: tabs are kept alive with show/hide, view models hold
lists, filters and paging, and every RecyclerView restores its scroll position. Insets
(status/navigation bars, display cutouts, gesture areas) are applied for edge-to-edge drawing.

**iPad:** the expanded layouts are designed for iPad-class screens (e.g. 1024–1366 dp wide), but
an Android APK cannot run on iPadOS. A real iPad app needs a separate iOS project or a
cross-platform rewrite, which is outside this Java/XML Android project.

## Android TV / Google TV

A separate experience (`ui/tv`), launched from `LEANBACK_LAUNCHER`, with a 320×180 dp banner:

- D-pad-only navigation: side menu (Home, Search, My List, Settings) + content.
- Home: a hero that follows the focused title (backdrop, title, metadata) above horizontal rows;
  a "Browse by genre" row opens full grids.
- Rows remember the last focused item (`FocusMemoryRecyclerView`), so moving up/down returns to
  where you were. Back from content returns to the menu; back from the menu exits.
- Clear focus states: a white ring plus a gentle scale-up on cards; buttons fill white when focused.
- 10-foot sizing in `values-television` / `values-w840dp-television`.
- Title page: backdrop, focusable actions (Watch, list status, favourite), then episode, character
  and related rows.
- Initial focus is placed after layout (`TvFocus`), so it isn't lost while data loads.
- Play Store TV requirements covered: leanback launcher intent, banner, no required touchscreen,
  no portrait-only screens. Before distributing, also review Google's TV app quality checklist on
  real hardware.

## Cars (Android Auto / Android Automotive OS) — not supported, by policy

Checked against Google's [Android for Cars overview](https://developer.android.com/training/cars)
and [car app quality guidelines](https://developer.android.com/docs/quality-guidelines/car-app-quality)
(November 2026):

- Car apps are limited to specific categories: media (audio), messaging, calling, navigation,
  points of interest, IoT, weather, plus the parked categories video, games and browsers.
- **An anime catalog/tracking app is in none of these categories.**
- Even with playback added later, the **Video** category is allowed only on **Android Automotive
  OS**, **primarily while parked**, and must not be usable while driving (Android Auto video is
  limited to an early-access partner programme).

So the manifest makes **no** car declarations (no `automotive_app_desc`, no car services, no
`android.hardware.type.automotive` feature). Nothing should be described as car-compatible.
If you add playback later and want an AAOS parked video build, follow
[Build video apps for Android Automotive OS](https://developer.android.com/training/cars/parked/video):
a separate AAOS build with video only while parked, media-session integration, and the
distraction-optimization rules.

## Testing done

- Robolectric renders (native graphics) of real screens with recorded API data: phone (EN + AR/RTL),
  tablet 1280×800 (rail, master–detail, wide details) and TV 960×540 dp (focus placement asserted).
- Not done: tests on physical devices or an emulator (no hardware virtualisation was available
  in the build environment). Run the app on a real phone, tablet and TV before release.
