# CarDrop

A Pleos Connect app for groups of cars driving to the same place: form a crew, vote on a rest stop,
share a fuel warning, and send the agreed stop to every car's navigation (an in-app navigator on the emulator, Naver Map
from the Pleos app market on a car).

## Quick start

1. Install Android Studio and open this folder.
2. Copy your Android SDK path into `local.properties` (`sdk.dir=...`). The other keys are described in `CLAUDE.md`.
3. Start the `Pleos_Connect_v2.1.0` emulator, then press Run (or `./gradlew installDebug`).
4. In the app: 크루 → 다 모이면 출발하기 → 홈 → 휴게소 → 이 3곳으로 투표 보내기 → 응답하기.
   The **DEMO** button (bottom right) stages low fuel, P/D, and drops from the bot cars.

Everything runs on fakes by default, so no car or map app is needed. The in-app navigator is real (GPS +
live routes) and needs internet; on the emulator, run `python3 tools/drive_gps.py` to drive the car.
See `CLAUDE.md` for architecture, ownership and the navigation handoff notes.
