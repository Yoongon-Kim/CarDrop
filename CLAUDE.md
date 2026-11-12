# CarDrop

CarDrop lets a group of cars driving to the same place act "like one car": they form a crew,
send each other drops (a rest-stop vote, a fuel warning, a short message), and the agreed stop goes
into every car's navigation. It runs on Hyundai's Pleos Connect (Android Automotive).

- Build plan, scope and owners: https://claude.ai/artifact/JYJGVXDzvwKVNPHmTigVZP
- Design canvas: https://claude.ai/artifact/5g7v1Y5dL3wg6wk8H6sjMW
- Deadline: demo on 2026-11-17. Gates: flow clickable on fakes by Nov 12, real services by Nov 14,
  feature freeze Nov 16.

The team is new to Android and Kotlin. When you (Claude) write code here, explain what it does in plain
words, keep changes small, and run the build before saying something works.

## Who owns what

| Owner | Folders | Screens (design board) | Interface |
| --- | --- | --- | --- |
| Lead | `ai/`, `voice/`, `core/` | 09, 10, 15 (quiz) | `AiService`, `VoiceService` |
| Member A | `crew/`, `drop/` | 01, 02, 03, 07, 08, 11 | `CrewRepository` |
| Member B | `vehicle/`, `drive/`, `navi/` | 04, 05, 06, 14, in-app navigator (left third) | `VehicleRepository`, `RouteSimulator`, `NavigationHandoff` |

Rules:
- Edit only your own folders. `core/` (models, navigation, theme, `AppContainer`, `CarDropApp`) changes
  only through the lead. In `CarDropApp.kt` a member may edit only their own `Screen.X ->` branch.
- No new Gradle dependency without the lead.
- One branch per task (`a/crew-join`, `b/fuel-drop`, `lead/ai-recommend`), a PR into `main`, and
  `./gradlew assembleDebug testDebugUnitTest` must pass before merging.
- Never commit `local.properties` (API keys live there).

## Architecture

```
Screens (Compose)  ->  ViewModels  ->  interfaces  ->  fake now / real later
                                       CrewRepository      FakeCrewRepository (bots) -> realtime DB
                                       VehicleRepository   FakeVehicleRepository     -> PleosVehicleRepository
                                       RouteSimulator      GpsCrewPositions (inapp) / ScriptedRouteSimulator
                                       NavigationHandoff   InAppNavigation (GPS + Kakao/OSRM) -> NaverMapHandoff
                                       AiService           FakeAiService             -> Claude API
                                       VoiceService        FakeVoiceService          -> Android TTS/STT
```

- `core/AppContainer.kt` is the only place that picks fake or real. Screens never create services.
- `core/CarDropApp.kt` maps each `Screen` to its composable. `core/navigation/Screen.kt` lists every
  screen with its board number and owner.
- `core/navigation/DropRouting.kt` opens the vote / fuel / decision screens when a drop arrives.
- `drive/TripCoordinator.kt` runs all the time: crew destination or a finished vote -> route.
- `core/model/Models.kt` holds the shared data types. Ask the lead before changing them.
- Shared UI: `core/ui/Components.kt` (`ScreenHeader`, `CdCard`, `PrimaryButton`, `Pill`, `BackButton`,
  `CdChip`, `CdTextField`, `StatusText`, `ErrorText`, ...), `core/ui/Icons.kt` (`CdIcons`, the design's line
  icons) and `core/ui/theme/Theme.kt` (`CdColors` = the design's colours). Use these instead of raw Material
  styling. `core/ui/ComponentsPreview.kt` shows the controls in Android Studio's preview.
- `TodoNote("...")` marks unbuilt parts of a screen. Remove it when the part is done.
- Design rule: DECISION drops are spoken aloud and need a vote; SOCIAL drops are on screen only,
  never spoken. No answer before the timer ends counts as candidate 1 (`drop/VoteRules.kt`).

## Design boards

- `design/FinalApp.dc.html` holds all final screens (1600x900; left 1/3 = navigation, right 2/3 = CarDrop).
  Each screen starts with a comment such as `<!-- 11 투표 (받는 차) + 돌림판 이스터에그 -->`;
  the board numbers match `Screen.kt`.
- `design/FeatureMap.dc.html` = what is in scope (A = must, B = 1-2 extras, C = vision, D = cut).
- The live canvas (link above) can be read with the Artifact tool if the local copy is out of date.
- Week 1 cuts: song drop (12, 13), omok/chess (15), safety race (16, 17), ads and points (18, 19).

## Build and run

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home"   # or Android Studio's JBR
./gradlew assembleDebug testDebugUnitTest      # build + unit tests
./gradlew installDebug                         # install on the running emulator
~/Library/Android/sdk/emulator/emulator -avd Pleos_Connect_v2.1.0   # Pleos emulator
```

The **DEMO** button (bottom right of the app) changes fake fuel and gear, and makes bot cars send drops.
Use it to rehearse the 시연 on one emulator.

## Local settings (`local.properties`, not committed)

| Key | Values | Effect |
| --- | --- | --- |
| `cardrop.useRealVehicle` | `true` / `false` | Pleos Vehicle SDK instead of the fake car |
| `cardrop.navApp` | `inapp` / `naver` / `kakao` | `inapp` (default) draws the navigator inside CarDrop; `naver` hands the route to Naver Map (`kakao` is not built yet) |
| `cardrop.claudeApiKey` | key | Exposed as `BuildConfig.CLAUDE_API_KEY` for the real `AiService` |
| `cardrop.kakaoRestKey` | key | Kakao Developers REST API key for live routes (empty = OSRM only) |

## Vehicle SDK notes (2.3.2, checked against the SDK classes)

- Used: `Fuel.getFuelPercent` (permission `CAR_ENERGY`), `Odometer.getRangeRemainingDistance` and
  `getTripCurrentDrivingTime` (`CAR_DRIVE`), `Gear.getCurrentGearSelectionState` (`CAR_DRIVE`).
- The gear and range APIs are deprecated in 2.3.2 ("use the Google GEAR_SELECTION / RANGE_REMAINING
  property", i.e. Android Automotive `CarPropertyManager`). They still compile; check them on the emulator.
- The SDK has no vehicle speed or location API. Car positions come from `ScriptedRouteSimulator`.
- Driving-time units are unverified (seconds assumed). See the TODO in `PleosVehicleRepository`.
- The reference for SDK lifecycle and capability checks is `../pleos-sample-vehicle-app`.

## In-app navigator (`cardrop.navApp=inapp`, the default)

The first demo round runs on the emulator, and the emulator's built-in Pleos navigation shows a blank
grey map (its developer map style `connect_empty` has no roads). So CarDrop has its own real navigator:
GPS position, live routes, rerouting, turn guidance. Only the GPS input is simulated on the emulator.

**Run the demo car:** start the emulator and the app, then
`python3 tools/drive_gps.py` (drives the 영동고속도로 from km 40 at 6x speed, 1 GPS fix per second via
`adb emu geo fix`; `--speed 1` for real time, `--start-km`, `--gpx` for Extended controls). The emulator's
own route player can't make Korean routes (Google has no driving directions in Korea). Grant location
when the app asks (or `adb shell pm grant com.cardrop android.permission.ACCESS_FINE_LOCATION`).

- Layout: with `inapp`, our window holds the whole 1600 x 900 design. The navigator is the left 533 px
  (`core/ui/Shell.kt`), CarDrop is the right 2/3. Everything is scaled down a little to fit.
- `navi/GpsLocation.kt` - Android GPS (1 fix/s). The emulator reports bearing 0, so direction comes from movement.
- `navi/InAppNavigation.kt` - the navigator (`NavigationHandoff`): asks for a route from the GPS position to
  the crew destination via the voted waypoint; asks again when the destination / waypoint changes, when
  the car leaves the route (60 m, 3 fixes) and after the waypoint is reached (600 m). Rules in
  `navi/GuidanceTracker.kt` (unit tested).
- Routing: `navi/KakaoDirections.kt` (Kakao Mobility, Korean data + traffic + Korean turn texts; key
  `cardrop.kakaoRestKey`, free for non-commercial use up to 10,000 requests/day), then
  `navi/OsrmDirections.kt` (public OSRM server, no key, no traffic, no uptime promise) as backup.
- `navi/GpsCrewPositions.kt` - crew positions: this car from GPS; the bot cars follow its GPS trail
  0.8 / 1.8 / 3 km behind (until Member A's real crew sync).
- `navi/InAppNavigatorPanel.kt` + `navi/NavPanelState.kt` - the design's overlays (turn banner, CarDrop
  toast, speed from GPS, ETA card), "전체 경로" and, after dragging the map, "현재 위치".
- `navi/NavMap.kt` - MapLibre (`org.maplibre.gl:android-sdk-opengl:13.0.2`) with free OpenFreeMap tiles
  (OpenStreetMap data, no key; keep the attribution text). Needs internet; without the style it falls back
  to a plain background. MapLibre 13.1+ needs Kotlin 2.2, so stay on 13.0.x unless Kotlin is upgraded.
- AI and DEMO-panel candidates come from `AppContainer.stopsAhead`: stops near the live route at least
  25 km ahead, so a 2-minute vote ends before the car passes the stop.
- `drive/DemoRoute.kt` + `drive/DemoRouteData.kt` - the baked 244 km trip (OSRM, `tools/bake_route.py`).
  Used by `tools/drive_gps.py` and, with `cardrop.navApp=naver`, by `ScriptedRouteSimulator`.

## Navigation handoff (researched 2026-11-10)

Pleos allows only one navigation app to hold the active route, and switching needs an explicit user
choice. So `NavigationHandoff.setRoute()` only records the route, and `launch()` opens the map app.
Call `launch()` only from a button the driver pressed.

Options, in order of fit:
1. **Naver Map** (in the Pleos app market) — built: `navi/NaverMapHandoff.kt`.
   `nmap://navigation?dlat&dlng&dname&v1lat&v1lng&v1name&appname=<applicationId>`, package
   `com.nhn.android.nmap`, up to 5 waypoints (`v1`..`v5`), no API key.
   Source: https://guide.ncloud-docs.com/docs/application-maps-url-scheme-vpc.md
2. **Kakao Navi** (in the Pleos app market) — not built. It has no URL scheme; use the Kakao Navi SDK:
   `com.kakao.sdk:v2-navi` from `https://devrepo.kakao.com/nexus/content/groups/public/`,
   `KakaoSdk.init(context, NATIVE_APP_KEY)`, then
   `NaviClient.instance.navigateIntent(destination, NaviOption(coordType = CoordType.WGS84), viaList)`.
   Needs a Kakao developer app (native key + key hash). Package `com.locnall.KimGiSa`.
   Source: https://developers.kakao.com/docs/ko/kakaonavi/android
3. **KakaoMap** — not found in the Pleos app market; no official deep-link spec. Skip.
4. **Pleos built-in navigation (NaviHelper SDK)** — `ai.pleos.playground:NaviHelper:3.0.5` on the same
   Pleos Maven repo. It drives the built-in nav (`requestRoute`, `addWaypoint`, max 2 waypoints) with no
   app switch. Permissions `pleos.car.permission.NAVI_ROUTE_SEARCH` and `NAVI_ROUTE`. Not built.
   Source: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro

Unverified until tested on Pleos: that the app-market builds of Naver Map and Kakao Navi keep the same
package names and honour these intents, and whether `market://` links work (do not rely on them).
Window behaviour (https://document.pleos.ai/en/docs/connect/guide/docs-design/getting-started/window):
on Connect-S two apps split the screen 1:1; on Connect-W a deep-linked app opens over ours.
