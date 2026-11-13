#!/usr/bin/env python3
"""Drives the emulator's GPS along the demo trip, so CarDrop's in-app navigator has a moving car.

The app reads GPS like a real navigator; this script plays the part of the car. It moves along the
route baked in app/src/main/java/com/cardrop/drive/DemoRouteData.kt (신촌 -> 경부 -> 영동고속도로 ->
강릉 경포해변, real roads from OpenStreetMap) and sends one GPS fix per second with `adb emu geo fix`.

Usage (emulator running):
    python3 tools/drive_gps.py                 # from km 40 (just after 신갈JC), 6x speed
    python3 tools/drive_gps.py --speed 1       # real time, 100 km/h
    python3 tools/drive_gps.py --start-km 60 --speed 10
    python3 tools/drive_gps.py --gpx /tmp/trip.gpx   # write a GPX for Extended controls > Location > Routes
    python3 tools/drive_gps.py --stop 여주휴게소     # pull into that stop, stand still 20 s, drive on
    python3 tools/drive_gps.py --car uncle        # 삼촌 차 with the real crew: waits for the host's 출발
    python3 tools/drive_gps.py --serial emulator-5556 --car minji   # the second emulator on this Mac
Ctrl+C stops; the car stays where it is. The reported speed is always --kmh (not sped up), like a real car.

--car picks where the car starts: 아빠 km 40, 삼촌 39.2, 민지 38.2, 이모 37 (the bot cars' gaps). It also
holds the car still at its start until the crew's trip starts (the host's 출발 on board 01, or 3 minutes after
the crew was made), read from the real crew's Firebase (crews/<code>/info/startedAt, see
crew/FirebaseCrewRepository.kt), so every car sets off together. --no-wait drives right away (the bot crew has
no host to wait for).

--stop shows the navigator's "도착" (arrived): it needs about 10 s standing still within 300 m of the
voted stop. Without --stop the car drives past every stop, which the navigator reports as "지나침".
"""
import argparse
import json
import math
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ROUTE_FILE = ROOT / "app/src/main/java/com/cardrop/drive/DemoRouteData.kt"
PLACES_FILE = ROOT / "app/src/main/java/com/cardrop/core/demo/DemoData.kt"
CREW_FILE = ROOT / "app/src/main/java/com/cardrop/crew/FirebaseCrewRepository.kt"

# Where each car starts (km on the route): the same gaps as the bot cars (BOT_GAPS_KM in GpsCrewPositions.kt).
CAR_START_KM = {"dad": 40.0, "uncle": 39.2, "minji": 38.2, "aunt": 37.0}


def find_adb() -> str:
    """adb from PATH, else from the Android SDK (Android Studio doesn't put it on PATH)."""
    found = shutil.which("adb")
    if found:
        return found
    for sdk in (os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT"), str(Path.home() / "Library/Android/sdk")):
        if sdk and (Path(sdk) / "platform-tools/adb").exists():
            return str(Path(sdk) / "platform-tools/adb")
    sys.exit("adb not found: add Android SDK platform-tools to PATH or set ANDROID_HOME")


def kotlin_string(path: Path, name: str) -> str:
    """The value of `const val <name> = \"...\"` in a Kotlin file."""
    found = re.search(rf'const val {name}\s*=\s*"([^"]+)"', path.read_text(encoding="utf-8"))
    if not found:
        sys.exit(f"{name} not found in {path}")
    return found.group(1)


def trip_started(url: str):
    """True / False from the crew's startedAt in Firebase (REST, no login); None when it can't be read."""
    try:
        with urllib.request.urlopen(url, timeout=5) as response:
            return json.load(response) is not None
    except (OSError, ValueError):  # no network, refused (HTTPError is an OSError), or not JSON
        return None


def wait_for_host(stand_still, url: str, poll_s: float = 1.0):
    """Calls stand_still() every poll_s seconds until the crew's trip has started."""
    print("Waiting for the host's 출발, or 3 minutes after the crew was made (the car stands at its start). Ctrl+C to stop.")
    misses = 0
    while True:
        stand_still()
        started = trip_started(url)
        if started:
            print("The trip started. Driving.")
            return
        if started is None:
            misses += 1
            if misses % 10 == 1:
                print(f"  Can't read the trip start ({url}); still waiting.")
        time.sleep(poll_s)


def load_places() -> dict:
    """Every Place(...) in DemoData.kt (rest stops, cafés, destinations) by name: (lat, lng)."""
    text = PLACES_FILE.read_text(encoding="utf-8")
    found = re.findall(r'Place\(\s*"[^"]*",\s*"([^"]+)",\s*(-?[0-9.]+),\s*(-?[0-9.]+)', text)
    return {name: (float(lat), float(lng)) for name, lat, lng in found}


def nearest_km(points, cumulative, place):
    """km along the route where it passes closest to [place], checked every ~20 m."""
    best_km, best_d, km = 0.0, float("inf"), 0.0
    while km <= cumulative[-1]:
        d = haversine_km(point_at(points, cumulative, km), place)
        if d < best_d:
            best_km, best_d = km, d
        km += 0.02
    return best_km, best_d


def load_polyline() -> str:
    text = ROUTE_FILE.read_text(encoding="utf-8")
    block = text.split("const val POLYLINE: String =", 1)[1].split("\n\n", 1)[0]
    parts = re.findall(r'"((?:[^"\\]|\\.)*)"', block)
    return "".join(parts).replace("\\$", "$").replace('\\"', '"').replace("\\\\", "\\")


def decode(encoded: str, precision: int = 5):
    coords, index, lat, lng, factor = [], 0, 0, 0, 10 ** precision
    while index < len(encoded):
        for which in (0, 1):
            shift = result = 0
            while True:
                b = ord(encoded[index]) - 63
                index += 1
                result |= (b & 0x1F) << shift
                shift += 5
                if b < 0x20:
                    break
            delta = ~(result >> 1) if result & 1 else result >> 1
            if which == 0:
                lat += delta
            else:
                lng += delta
        coords.append((lat / factor, lng / factor))
    return coords


def haversine_km(a, b):
    dlat, dlng = math.radians(b[0] - a[0]), math.radians(b[1] - a[1])
    h = math.sin(dlat / 2) ** 2 + math.cos(math.radians(a[0])) * math.cos(math.radians(b[0])) * math.sin(dlng / 2) ** 2
    return 2 * 6371.0 * math.asin(math.sqrt(h))


def point_at(points, cumulative, km):
    km = max(0.0, min(km, cumulative[-1]))
    i = max(0, min(next((k for k in range(1, len(cumulative)) if cumulative[k] >= km), len(cumulative) - 1) - 1, len(points) - 2))
    span = cumulative[i + 1] - cumulative[i]
    t = 0.0 if span == 0 else (km - cumulative[i]) / span
    a, b = points[i], points[i + 1]
    return a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t


def write_gpx(path, points, cumulative, start_km, kmh):
    lines = ['<?xml version="1.0" encoding="UTF-8"?>',
             '<gpx version="1.1" creator="CarDrop drive_gps.py" xmlns="http://www.topografix.com/GPX/1/1"><trk><trkseg>']
    start = time.time()
    for (lat, lng), km in zip(points, cumulative):
        if km < start_km:
            continue
        stamp = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(start + (km - start_km) / kmh * 3600))
        lines.append(f'<trkpt lat="{lat:.6f}" lon="{lng:.6f}"><time>{stamp}</time></trkpt>')
    lines.append("</trkseg></trk></gpx>")
    Path(path).write_text("\n".join(lines), encoding="utf-8")
    print(f"Wrote {path}: import it in the emulator's Extended controls > Location > Routes.")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--car", choices=sorted(CAR_START_KM), default=None,
                        help="this emulator's car: its start km, and wait for the host's 출발 (real crew)")
    parser.add_argument("--start-km", type=float, default=None,
                        help="where to start on the route (default: the --car's start, else 40 = after 신갈JC)")
    parser.add_argument("--wait", action=argparse.BooleanOptionalAction, default=None,
                        help="stand still until the host's 출발 (default: on with --car)")
    parser.add_argument("--code", default=None, help="crew code (default: DEMO_CREW_CODE in DemoData.kt)")
    parser.add_argument("--speed", type=float, default=6.0, help="time speed-up (default 6: 10 km per minute)")
    parser.add_argument("--kmh", type=float, default=100.0, help="driving speed the GPS reports (default 100)")
    parser.add_argument("--serial", default=None, help="adb device, e.g. emulator-5554")
    parser.add_argument("--gpx", default=None, help="write a GPX file instead of driving")
    parser.add_argument("--stop", default=None, help="pull into this place on the way, e.g. 여주휴게소 (a name from DemoData.kt)")
    parser.add_argument("--stop-seconds", type=float, default=20.0, help="how long to stand still there (default 20)")
    args = parser.parse_args()
    start_km = args.start_km if args.start_km is not None else CAR_START_KM.get(args.car, 40.0)
    wait = args.wait if args.wait is not None else args.car is not None

    points = decode(load_polyline())
    cumulative = [0.0]
    for a, b in zip(points, points[1:]):
        cumulative.append(cumulative[-1] + haversine_km(a, b))
    total = cumulative[-1]

    if args.gpx:
        write_gpx(args.gpx, points, cumulative, start_km, args.kmh)
        return

    stop = None
    if args.stop:
        places = load_places()
        if args.stop not in places:
            sys.exit(f"Unknown place {args.stop!r}. Known: {', '.join(sorted(places))}")
        stop_km, off_km = nearest_km(points, cumulative, places[args.stop])
        if off_km > 1.5 or stop_km < start_km:
            sys.exit(f"{args.stop} is not ahead on the route (closest at km {stop_km:.1f}, {off_km * 1000:.0f} m away)")
        stop = (args.stop, places[args.stop], stop_km)
        print(f"Will stop at {args.stop} (km {stop_km:.1f}) for {args.stop_seconds:g} s.")

    adb = [find_adb()] + (["-s", args.serial] if args.serial else [])
    knots = args.kmh / 1.852

    def send(lat, lng, knots_now):
        # geo fix <longitude> <latitude> [<altitude> [<satellites> [<velocity in knots>]]]
        result = subprocess.run(adb + ["emu", "geo", "fix", f"{lng:.6f}", f"{lat:.6f}", "50", "8", f"{knots_now:.1f}"],
                                capture_output=True, text=True)
        if result.returncode != 0:
            sys.exit(f"adb failed: {result.stderr.strip() or result.stdout.strip()} (is the emulator running?)")

    if wait:
        code = args.code or kotlin_string(PLACES_FILE, "DEMO_CREW_CODE")
        url = f"{kotlin_string(CREW_FILE, 'DATABASE_URL')}/crews/{code}/info/startedAt.json"
        start_lat, start_lng = point_at(points, cumulative, start_km)
        try:
            wait_for_host(lambda: send(start_lat, start_lng, 0.0), url)
        except KeyboardInterrupt:
            print("\nStopped before the start.")
            return

    km = start_km
    step = args.kmh / 3600.0 * args.speed
    print(f"Driving {total - km:.0f} km at {args.kmh:.0f} km/h x{args.speed:g}. Ctrl+C to stop.")
    tick = 0
    try:
        while km <= total:
            if stop and km >= stop[2]:
                # Pull into the stop (its map point, beside the motorway) and stand still, 1 fix per second.
                name, (lat, lng), stop_km = stop
                print(f"  Stopped at {name} for {args.stop_seconds:g} s.")
                for _ in range(int(args.stop_seconds)):
                    send(lat, lng, 0.0)
                    time.sleep(1.0)
                print(f"  Leaving {name}.")
                km, stop = stop_km, None
            lat, lng = point_at(points, cumulative, km)
            send(lat, lng, knots)
            if tick % 10 == 0:
                print(f"  km {km:6.1f} / {total:.0f}   {lat:.5f}, {lng:.5f}")
            tick += 1
            km += step
            time.sleep(1.0)
        print("Arrived at 경포해변.")
    except KeyboardInterrupt:
        print(f"\nStopped at km {km:.1f}.")


if __name__ == "__main__":
    main()
