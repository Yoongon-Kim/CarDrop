# CarDrop

CarDrop lets a group of cars driving to the same place act "like one car": they form a crew,
send each other drops (a rest-stop vote, a fuel warning, a short message), and the agreed stop goes
into every car's navigation. It runs on Hyundai's Pleos Connect (Android Automotive).

- Build plan, scope and owners: https://claude.ai/artifact/JYJGVXDzvwKVNPHmTigVZP
- Design canvas: https://claude.ai/artifact/5g7v1Y5dL3wg6wk8H6sjMW (newer than our target; see "Design boards")
- Deadline: demo on 2026-11-17. Gates: flow clickable on fakes by Nov 12, real services by Nov 14,
  feature freeze Nov 16.

The team is new to Android and Kotlin. When you (Claude) write code here, explain what it does in plain
words, keep changes small, and run the build before saying something works.

## Who owns what

| Owner | Folders | Screens (design board) | Interface |
| --- | --- | --- | --- |
| Lead | `ai/`, `voice/`, `core/` | 09, 10, 15 (quiz) | `AiService`, `VoiceService` |
| Member A | `crew/`, `drop/` | 01, 02, 03, 07, 08, 11 | `CrewRepository` |
| Lead (was Member B) | `vehicle/`, `drive/`, `navi/` | 04, 05, 06, 14, in-app navigator (left third) | `VehicleRepository`, `RouteSimulator`, `NavigationHandoff` |

Member B finished on 2026-11-13 (PR #9) and does no further work. The lead now owns B's folders and
remaining tasks (use `lead/...` branches there); code comments that still say "Member B" mean the lead.

Rules:
- Edit only your own folders. `core/` (models, navigation, theme, `AppContainer`, `CarDropApp`) changes
  only through the lead. In `CarDropApp.kt` a member may edit only their own `Screen.X ->` branch.
- No new Gradle dependency without the lead.
- One branch per task (`a/crew-join`, `b/fuel-drop`, `lead/ai-recommend`), a PR into `main`, and
  `./gradlew assembleDebug testDebugUnitTest` must pass before merging.
- Never commit `local.properties` (API keys live there) or `app/google-services.json` (Firebase settings).

## Architecture

```
Screens (Compose)  ->  ViewModels  ->  interfaces  ->  fake now / real later
                                       CrewRepository      FakeCrewRepository (bots) -> FirebaseCrewRepository
                                       VehicleRepository   FakeVehicleRepository     -> PleosVehicleRepository
                                       RouteSimulator      GpsCrewPositions (GPS; tools/drive_gps.py on the emulator)
                                       NavigationHandoff   InAppNavigation (GPS + Kakao/OSRM)
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
- After a place or car name, pick the particle with `core/text/Korean.kt`: `name.particle("을", "를")`,
  `particle("이", "가")`, `name.withRo()` (로/으로). Never hard-code 를/는/가/로 after a name ("경포해변로").
- Design rule: DECISION drops are spoken aloud and need a vote; SOCIAL drops are on screen only,
  never spoken. No answer before the timer ends counts as candidate 1 (`drop/VoteRules.kt`).
- Place search (boards 02 and 10), like Kakao Map's: typing shows the places that fit (Kakao Local keyword
  search, `navi/KakaoPlaceSearch.kt`, key `cardrop.kakaoRestKey`, nearby places first, distance from the car's
  GPS via `AppContainer.myPosition()`). The shared parts: `navi/PlaceSuggester.kt` (2+ letters, 300 ms pause,
  half-typed Korean dropped, `best()` = top result for enter / speech) and `navi/PlaceSuggestionList.kt` (the
  list UI, `KeyboardRoom` so the last lines scroll above Pleos's keyboard). Without the key, or offline, only
  places the app knows match (`knownPlaceMatches` in `navi/PlaceSearch.kt`). Logs: `adb logcat -s CarDropPlaces`.
  - Board 02: the box starts focused; before typing it lists `DemoData.destinations`. Tapping a place (or enter
    for the top one) makes it the crew's destination with Kakao's address as its detail. 말로 검색 listens
    (`VoiceService.listen(placeName = true)`), puts what was heard into the box and searches it; the driver
    still taps the place, so a misheard name never becomes the destination by itself.
  - Board 10 "다른 곳을 직접 입력": the suggestions replace the list until the box is empty; tapping one (or 추가 =
    the top one) puts it in the list, ticked. 말해서 추가 takes the top result for what was heard. Known places
    here: rest stops, cafés, destinations. A searched place that is an AI pick under a longer name
    ("덕평자연휴게소 양방향") ticks the AI's row (`PlacePick.find`). 직접 추가 (`DropTopic.CUSTOM`) opens board 10
    with no AI picks.
- Board 08 "받는 차": a drop goes to the whole crew (`Drop.toCarIds` empty) or to chosen cars. Cars it
  wasn't sent to never see it (`Drop.isFor`), and only the sender and its receivers vote (`Drop.voterIds`).

## Design boards

- `design/FinalApp.dc.html` is the target: all final screens (1600x900; left 1/3 = navigation, right 2/3 =
  CarDrop). Each screen starts with a comment such as `<!-- 11 투표 (받는 차) + 돌림판 이스터에그 -->`;
  the board numbers match `Screen.kt`. It is the 2026-11-10 version of the 최종 UI, the same screens as the
  standalone export `CarDrop_최종UI.html` (chosen by the lead on 2026-11-13).
- The live canvas (link above) has moved on since: empty quick-drop slots with 편집, a quick-drop pop-up with a
  result notice, a feedback pill after sending, car nicknames, voice messages and shared `.cd-*` styles. We don't
  build those. Read the canvas only for reference; never copy it over `design/FinalApp.dc.html`.
- `design/FeatureMap.dc.html` = what is in scope (A = must, B = 1-2 extras, C = vision, D = cut).
- Week 1 cuts: song drop (12, 13), omok/chess (15), safety race (16, 17), ads and points (18, 19).

## Build and run

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home"   # or Android Studio's JBR
./gradlew assembleDebug testDebugUnitTest      # build + unit tests
./gradlew installDebug                         # install on the running emulator
~/Library/Android/sdk/emulator/emulator -avd Pleos_Connect_v2.1.0   # Pleos emulator
```

The **DEMO** button (bottom right of the app) changes fake fuel and gear, and makes bot cars send drops.
Use it to rehearse the 시연 on one emulator. With the real crew (`cardrop.useRealCrew=true`) its **내 차** picks
which car this device drives: the device remembers it and the app restarts as that car (`core/demo/DemoCar.kt`).
With the real crew, 아빠 차 starts on board 01 and creates the crew; the other cars start on board 03 to join,
because creating the crew replaces the one every device shares. The trip starts with the host's 출발, or by itself:
at once when every car has joined, else 3 minutes after the crew was made (board 01 counts down;
`JoinRules.JOIN_WINDOW_MILLIS`; both done by `TripCoordinator` on the host's device). Before that nobody drives off
or sends drops: a car that joined waits on board 04 ("아빠 차가
출발하면 같이 출발해요"), and drops, the inbox and the rail's 드랍 / 게임 / 편지함 are greyed out. Cars not connected at
the start are left out (`JoinRules.missingCars`; `out` in Firebase): they disappear from every screen and vote, and
their code then says "이미 출발한 크루예요" (a car that was in can join again, e.g. after an app restart). The start
belongs to the crew (`Crew.startedAtMillis`, `CrewRepository.startTrip`), for the bot crew too: it sets off by
itself once its 3 bots have joined (about 7 s after board 01 opens), a bot not in by an earlier 출발 stays behind,
and a bot crew joined with its code (board 03) is on the road already.

## Several Claude sessions at once

Sessions started in the same folder share its files, its one checked-out branch and the one emulator.
On 2026-11-13 a `git checkout` in one session moved another off its branch, and that session's commit
landed on `main`.

- Do branch work in your own git worktree, never with `git checkout` / `git switch` in a folder another
  session uses: `git fetch origin && git worktree add -b lead/<task> ../cardrop-<task> origin/main`.
  Copy `local.properties` into it (the build fails without one) and, for the real crew,
  `app/google-services.json`. Start Claude there. After the PR is merged: `git worktree remove ../cardrop-<task>`.
- One session at a time on the emulator. First check that no other session is using it: a test drive shows in
  `ps aux | grep drive_gps`, a fresh install in `adb shell dumpsys package com.cardrop | grep lastUpdateTime`.
  Installing over `com.cardrop` replaces the other session's build and wipes its trip. Before
  `adb shell input tap`, check which app is in front: `adb shell dumpsys activity activities | grep ResumedActivity`.
- To test while another session's CarDrop stays installed, build a side-by-side copy: add
  `debug { applicationIdSuffix = ".test.<task>" }` to `buildTypes` for that build only (don't commit it), with
  the fake crew and without `app/google-services.json` (Firebase and the Kakao key know only `com.cardrop`, so
  the copy shows the MapLibre map). Use your own `<task>` (letters, digits, `_`; no `-`), e.g. `.test.roulette`:
  another session's copy may be installed (`adb shell pm list packages --user 10 | grep cardrop`), and the same
  name would overwrite it. Start it with `adb shell am start --user 10 -n com.cardrop.test.<task>/com.cardrop.MainActivity`
  and uninstall it afterwards.

## Local settings (`local.properties`, not committed)

| Key | Values | Effect |
| --- | --- | --- |
| `cardrop.useRealCrew` | `true` / `false` | Firebase Realtime Database (project `cardrop-d47ca`, Singapore), so several devices share one crew (`crew/FirebaseCrewRepository.kt`). Needs `app/google-services.json` from Member A (not committed); without the file the app builds with the fake only. `false` = the fake crew with bot cars. Switching back needs a rebuild + install. Logs: `adb logcat -s CarDropCrew` |
| `cardrop.myCar` | `dad` / `uncle` / `minji` / `aunt` | The car this device drives in the real crew (empty = `dad`) until the DEMO panel's 내 차 picks another one on the device. Give each device a different car; any other value stops the build |
| `cardrop.useRealVehicle` | `true` / `false` | Pleos Vehicle SDK instead of the fake car |
| `cardrop.useRealVoice` | `true` / `false` | Pleos speech SDKs: spoken Korean and real listening (`voice/PleosVoiceService.kt`). `false` = the fake: captions only, and the mic always hears "배고파" (for rehearsing without speaking) |
| `cardrop.claudeApiKey` | key | Set = the real AI (`ai/ClaudeAiService.kt`, Anthropic Java SDK); empty = the fake. If a Claude call fails, the fake answers (`adb logcat -s CarDropAi`). The key is built into the APK, so don't share the APK |
| `cardrop.kakaoRestKey` | key | Kakao Developers REST API key for live routes and the place search on boards 02 and 10 (empty = OSRM only, and boards 02 / 10 offer only places the app knows) |
| `cardrop.kakaoNativeKey` | key | Kakao Developers native app key: the in-app navigator draws Kakao's map (`navi/KakaoNavMap.kt`); empty = MapLibre + OpenFreeMap. If Kakao refuses the key, the app falls back to MapLibre (`adb logcat -s CarDropKakaoMap`) |

## Vehicle SDK notes (2.3.2, checked against the SDK classes)

- Used: `Fuel.getFuelPercent` (permission `CAR_ENERGY`), `Odometer.getRangeRemainingDistance` and
  `getTripCurrentDrivingTime` (`CAR_DRIVE`), `Gear.getCurrentGearSelectionState` (`CAR_DRIVE`).
- Units, from the SDK's own docs (not yet measured on the emulator): range is in metres, driving time in
  minutes. `vehicle/VehicleValues.kt` converts range to km and drops impossible values (unit tested).
- Fuel and driving time are read only if their capability check says they are readable
  (`checkFuelPercentCapabilities`, `checkTripCurrentDrivingTimeCapabilities`). 2.3.2 has no such check for
  range or gear. A value that fails stays null (gear: `UNKNOWN`); `adb logcat -s CarDropVehicle` shows why.
- The gear and range APIs are deprecated in 2.3.2 ("use the Google GEAR_SELECTION / RANGE_REMAINING
  property", i.e. Android Automotive `CarPropertyManager`). They still compile; check them on the emulator.
  If gear stays `UNKNOWN`, the game (board 15) stays closed: it opens only in P (`ai/QuizScreen.kt`).
- The SDK has no vehicle speed or location API. Car positions come from GPS (`navi/GpsCrewPositions.kt`).
- The reference for SDK lifecycle and capability checks is `../pleos-sample-vehicle-app`.

## Voice notes (Pleos speech SDKs 3.3.1, checked on the emulator 2026-11-13)

- The image has no Google speech services. Its speech engine is Pleos's own (`ai.pleos.playground.caas`).
  Android's `android.speech.tts.TextToSpeech` can't use it: the engine's service isn't marked as a default
  one, so Android's lookup skips it (init fails with status -1). Use the Pleos SDKs instead:
  `ai.pleos.playground:TextToSpeech` and `SpeechToText` (`voice/PleosVoiceService.kt`).
- Docs: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro (and `SpeechToText/`).
  Permissions `pleos.car.permission.TTS_SERVICE` / `STT_SERVICE` (normal, no prompt). The Pleos service
  records the microphone itself, so CarDrop needs no `RECORD_AUDIO`.
- Call `initialize()` before any other SDK call: `addEventListener()` first crashes the app.
- `Mode.ON_DEVICE` needs no app registration. `HYBRID` (the SDK default) and `SERVER` need Pleos Playground
  app credentials through `registerApp(...)`.
- On the emulator, speaking works (you hear it on the computer). Listening needs, after every emulator
  start, Extended controls > Microphone > "Virtual microphone uses host audio input" (or
  `adb emu avd hostmicon`), and macOS must allow Android Studio to use the microphone. Without them it
  hears nothing and stops after 15 s. It hears everything near the computer, including other people.
- While listening, a "마이크 · 듣고 있어요…" caption shows at the bottom right, just above the DEMO button, until it
  stops (`Captions` in `core/CarDropApp.kt`).
- Pleos sends the final sentence a few ms after "recognition ended", so `PleosVoiceService` waits up to 1 s
  for it; otherwise it would keep only the partial text.
- Logs: `adb logcat -s CarDropVoice`.
- adb on this image: apps run as Android user 10, so use `--user 10`, e.g.
  `adb shell pm grant --user 10 com.cardrop <permission>` and `adb shell am start --user 10 -n com.cardrop/.MainActivity`.

## In-app navigator

The first demo round runs on the emulator, and the emulator's built-in Pleos navigation shows a blank
grey map (its developer map style `connect_empty` has no roads). So CarDrop has its own real navigator:
GPS position, live routes, rerouting, turn guidance. Only the GPS input is simulated on the emulator.

**Run the demo car:** start the emulator and the app, then
`python3 tools/drive_gps.py` (drives the 영동고속도로 from km 40 at 6x speed, 1 GPS fix per second via
`adb emu geo fix`; `--speed 1` for real time, `--start-km`, `--gpx` for Extended controls,
`--stop 여주휴게소` to pull into a stop and stand still 20 s, so the navigator says "도착"). The emulator's
own route player can't make Korean routes (Google has no driving directions in Korea). Grant location
when the app asks (or `adb shell pm grant --user 10 com.cardrop android.permission.ACCESS_FINE_LOCATION`).

**Several cars (real crew):** each device runs its own GPS script with its car, e.g.
`python3 tools/drive_gps.py --car uncle` (two emulators on one Mac: add `--serial emulator-5556`). `--car` starts
the car at its place in the convoy (아빠 km 40, 삼촌 39.2, 민지 38.2, 이모 37) and holds it still until the trip
starts (it reads `crews/<code>/info/startedAt` from Firebase); `--no-wait` drives right away. Every device
shares its position through Firebase about once a second, so each one draws the others where they really are.

- Layout: our window holds the whole 1600 x 900 design. The navigator is the left 533 px
  (`core/ui/Shell.kt`), CarDrop is the right 2/3. Everything is scaled down a little to fit.
- `navi/GpsLocation.kt` - Android GPS (1 fix/s). The emulator reports bearing 0, so direction comes from movement.
- `navi/InAppNavigation.kt` - the navigator (`NavigationHandoff`): asks for a route from the GPS position to
  the crew destination via the voted waypoint; asks again when the destination / waypoint changes, when
  the car leaves the route (60 m, 3 fixes) and once the car is done with the waypoint. Rules in
  `navi/GuidanceTracker.kt` (unit tested).
- At the waypoint (first GPS fix within 300 m): the map toast "…에 왔어요" and a card at the top right of
  the CarDrop side (`drive/StopNoticeCard.kt`, kept by `TripCoordinator.notice`); 도착 / 지나침 then
  replace the card for 10 s ("…에 도착했어요 · 크루에게 알렸어요").
- Waypoint done: **도착** = within 300 m and under 20 km/h for 10 s; **지나침** = came within 600 m, then
  more than 800 m away without stopping (the motorway passes rest stops 70-100 m from their map point, so
  distance alone can't tell them apart). Either one takes the stop off the route, shows the CarDrop toast,
  and `drive/TripCoordinator.kt` says it aloud and sends the crew a SOCIAL drop ("…에 도착했어요" /
  "…를 지나쳤어요"). Board 14 "도착 예정" (`drive/Arrivals.kt`) reads those crew messages back
  (`drive/StopNews.kt`) for 도착했어요 / 지나쳤어요; without one, a car within 0.3 km shows "곧 도착해요" and one
  more than 0.8 km past shows "지나쳤어요". The bot cars send no messages, so they never show 도착.
- After 도착 the car is parked there (`NavigatorState.parkedAt`) until it is more than 300 m away or faster than
  20 km/h: the ETA card shows "덕평자연휴게소 · 정차 중" with "P", and board 15's eyebrow names the stop and the cars
  that said 도착했어요 ("덕평자연휴게소 · 4대 모두 정차").
- Routing: `navi/KakaoDirections.kt` (Kakao Mobility, Korean data + traffic + Korean turn texts; key
  `cardrop.kakaoRestKey`, free for non-commercial use up to 10,000 requests/day), then
  `navi/OsrmDirections.kt` (public OSRM server, no key, no traffic, no uptime promise) as backup.
- `navi/GpsCrewPositions.kt` - crew positions: this car from GPS. Real crew: the other cars where their own
  devices say (`drive/FirebaseTrip.kt`, `trips/<code>/positions` in Firebase); a car without a connected device
  is not drawn. Bot crew: the bots follow this car's GPS trail 0.8 / 1.8 / 3 km behind.
- Board 01's destination card adds the road distance and drive time ("약 62km · 1시간 5분") from the live
  route (`navi/DestinationTrip.kt`, `AppContainer.destinationTrip`). Nothing shows until the route is there,
  or while the route still ends at the old destination after a change.
- `navi/InAppNavigatorPanel.kt` + `navi/NavPanelState.kt` - the design's overlays (turn banner, CarDrop
  toast, speed from GPS, ETA card), "전체 경로" and, after dragging the map, "현재 위치".
- `navi/NavMap.kt` - MapLibre (`org.maplibre.gl:android-sdk-opengl:13.0.2`) with free OpenFreeMap tiles
  (OpenStreetMap data, no key; keep the attribution text). Needs internet; without the style it falls back
  to a plain background. MapLibre 13.1+ needs Kotlin 2.2, so stay on 13.0.x unless Kotlin is upgraded.
- `navi/KakaoNavMap.kt` - the same map drawn by Kakao Maps (`com.kakao.maps.open:android:2.15.2`, Kakao's
  Maven repo), used when `cardrop.kakaoNativeKey` is set. Checked on the emulator 2026-11-13. Markers
  for both maps are in `navi/MapMarkers.kt`. Kakao setup (developers.kakao.com, app CarDrop):
  - 앱 → 플랫폼 키 → 네이티브 앱 키: Android package `com.cardrop` + the key hash of the keystore that
    signs the APK. Each Mac's debug keystore has its own hash (`adb logcat -s K3fAApi` prints it as
    `origin/...`), so each developer adds theirs.
  - The 카카오맵 service must be switched on, or Kakao answers 403 `disabled OPEN_MAP_AND_LOCAL service`.
  - Engine only for ARM (arm64-v8a, armeabi-v7a), which the Pleos emulator and cars are.
  - Kakao's angles: the camera turns counter-clockwise in radians (0..2π; a negative angle counts as 0),
    labels turn clockwise. Kakao's logo must stay visible; it is placed inside the camera padding.
- AI and DEMO-panel candidates come from `AppContainer.stopsAhead`: stops near the live route at least
  25 km ahead, so a 2-minute vote ends before the car passes the stop. Cafés (`DemoData.cafes`, real places
  from OpenStreetMap near 경포해변) and destinations are near the end of the trip and skip this filter.
- `drive/DemoRoute.kt` + `drive/DemoRouteData.kt` - the baked 244 km trip (OSRM, `tools/bake_route.py`).
  Used by `tools/drive_gps.py`.

## Other navigation apps (researched 2026-11-10, none built)

CarDrop uses only its in-app navigator. The Naver Map handoff (`cardrop.navApp=naver`) was removed on
2026-11-13. If another map app is ever added: Pleos allows only one navigation app to hold the active route,
and switching needs an explicit user choice. So `NavigationHandoff.setRoute()` only records the route, and
`launch()` would open the map app; call it only from a button the driver pressed.

Options, in order of fit:
1. **Kakao Navi** (in the Pleos app market) — not built. It has no URL scheme; use the Kakao Navi SDK:
   `com.kakao.sdk:v2-navi` from `https://devrepo.kakao.com/nexus/content/groups/public/`,
   `KakaoSdk.init(context, NATIVE_APP_KEY)`, then
   `NaviClient.instance.navigateIntent(destination, NaviOption(coordType = CoordType.WGS84), viaList)`.
   Needs a Kakao developer app (native key + key hash). Package `com.locnall.KimGiSa`.
   Source: https://developers.kakao.com/docs/ko/kakaonavi/android
2. **KakaoMap** — not found in the Pleos app market; no official deep-link spec. Skip.
3. **Pleos built-in navigation (NaviHelper SDK)** — `ai.pleos.playground:NaviHelper:3.0.5` on the same
   Pleos Maven repo. It drives the built-in nav (`requestRoute`, `addWaypoint`, max 2 waypoints) with no
   app switch. Permissions `pleos.car.permission.NAVI_ROUTE_SEARCH` and `NAVI_ROUTE`. Not built.
   Source: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro

Unverified until tested on Pleos: that the app-market build of Kakao Navi keeps the same package name and
honours this intent, and whether `market://` links work (do not rely on them).
Window behaviour (https://document.pleos.ai/en/docs/connect/guide/docs-design/getting-started/window):
on Connect-S two apps split the screen 1:1; on Connect-W a deep-linked app opens over ours.
