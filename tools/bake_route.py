"""Bakes an OSRM route (JSON) into app/src/main/java/com/cardrop/drive/DemoRouteData.kt.

The in-app navigator and ScriptedRouteSimulator drive this route. Re-run only to change the demo trip.

1. Get the route from the public OSRM demo server (OpenStreetMap data). The two middle points are on the
   영동고속도로 (eastbound) so the route passes the rest stops in DemoData; bearings keep them on the
   eastbound carriageway:
   curl -o /tmp/osrm.json "https://router.project-osrm.org/route/v1/driving/126.9368,37.5552;127.360332,37.243306;128.134374,37.464029;128.9081,37.8055?overview=full&geometries=polyline&steps=true&bearings=90,90;90,45;90,60;&continue_straight=true"
2. python3 tools/bake_route.py /tmp/osrm.json app/src/main/java/com/cardrop/drive/DemoRouteData.kt
"""
import json, math, sys

src, out = sys.argv[1], sys.argv[2]
r = json.load(open(src))
rt = r['routes'][0]

def decode(s, precision=5):
    coords, index, lat, lng, factor = [], 0, 0, 0, 10 ** precision
    while index < len(s):
        for which in (0, 1):
            shift = result = 0
            while True:
                b = ord(s[index]) - 63; index += 1
                result |= (b & 0x1f) << shift; shift += 5
                if b < 0x20: break
            d = ~(result >> 1) if result & 1 else result >> 1
            if which == 0: lat += d
            else: lng += d
        coords.append((lat / factor, lng / factor))
    return coords

def encode(coords, precision=5):
    factor, out_s, plat, plng = 10 ** precision, [], 0, 0
    for lat, lng in coords:
        ilat, ilng = round(lat * factor), round(lng * factor)
        for d in (ilat - plat, ilng - plng):
            v = ~(d << 1) if d < 0 else d << 1
            while v >= 0x20:
                out_s.append(chr((0x20 | (v & 0x1f)) + 63)); v >>= 5
            out_s.append(chr(v + 63))
        plat, plng = ilat, ilng
    return ''.join(out_s)

def hav(a, b):
    R = 6371.0
    dlat, dlng = math.radians(b[0] - a[0]), math.radians(b[1] - a[1])
    h = math.sin(dlat / 2) ** 2 + math.cos(math.radians(a[0])) * math.cos(math.radians(b[0])) * math.sin(dlng / 2) ** 2
    return 2 * R * math.asin(math.sqrt(h))

def simplify(pts, tol_m):
    # Douglas-Peucker on a local equirectangular projection (metres).
    lat0 = math.radians(pts[0][0])
    xy = [(p[1] * 111320 * math.cos(lat0), p[0] * 110540) for p in pts]
    keep = [False] * len(pts); keep[0] = keep[-1] = True
    stack = [(0, len(pts) - 1)]
    while stack:
        i, j = stack.pop()
        ax, ay = xy[i]; bx, by = xy[j]
        dx, dy = bx - ax, by - ay; L2 = dx * dx + dy * dy
        best, bi = -1, -1
        for k in range(i + 1, j):
            px, py = xy[k]
            t = 0 if L2 == 0 else max(0, min(1, ((px - ax) * dx + (py - ay) * dy) / L2))
            d = math.hypot(px - (ax + t * dx), py - (ay + t * dy))
            if d > best: best, bi = d, k
        if best > tol_m:
            keep[bi] = True; stack += [(i, bi), (bi, j)]
    return [p for p, k in zip(pts, keep) if k]

full = decode(rt['geometry'])
pts = simplify(full, 10.0)
cum = [0.0]
for a, b in zip(pts, pts[1:]): cum.append(cum[-1] + hav(a, b))

def km_at(loc):
    # route km of the vertex nearest to loc (lng, lat)
    p = (loc[1], loc[0])
    i = min(range(len(pts)), key=lambda k: hav(pts[k], p))
    return cum[i]

def korean(step):
    m = step['maneuver']; typ, mod = m['type'], m.get('modifier', 'straight')
    name = (step.get('name') or '').split(';')[0].strip()
    dests = (step.get('destinations') or '')
    if ':' in dests: dests = dests.split(':', 1)[1]
    dests = [d.strip() for d in dests.split(',') if d.strip()]
    dest = next((d for key in ('강릉', '원주', '영동', '동해') for d in dests if key in d), dests[0] if dests else '')
    left = 'left' in mod; right = 'right' in mod
    side = '왼쪽' if left else '오른쪽' if right else ''
    if typ == 'arrive': return 'ARRIVE', '목적지 도착'
    if typ == 'on ramp': return ('LEFT' if left else 'RIGHT'), f'{dest or name} 방면 진입' if (dest or name) else f'{side} 진입로'
    if typ == 'off ramp': return ('LEFT' if left else 'RIGHT'), f'{dest or name} 방면 출구' if (dest or name) else f'{side} 출구'
    if typ == 'merge': return 'STRAIGHT', f'{name or dest} 합류' if (name or dest) else '본선 합류'
    if typ == 'fork': return ('LEFT' if left else 'RIGHT'), f'{dest or name} 방면 {side}' if (dest or name) else f'{side} 방향'
    if 'uturn' in mod: return 'UTURN', f'{name} 유턴'.strip()
    if left: return 'LEFT', f'{name} 좌회전'.strip()
    if right: return 'RIGHT', f'{name} 우회전'.strip()
    return 'STRAIGHT', f'{name} 방면 직진' if name else '직진'

steps, last_km = [], -1
for li, leg in enumerate(rt['legs']):
    for si, s in enumerate(leg['steps']):
        typ = s['maneuver']['type']
        if typ == 'depart': continue
        if typ == 'arrive' and li < len(rt['legs']) - 1: continue   # via points
        if typ in ('new name',) or (typ == 'continue' and s['maneuver'].get('modifier') == 'straight'): continue
        arrow, text = korean(s)
        if not text: continue
        km = km_at(s['maneuver']['location'])
        if km - last_km < 0.05 and steps: continue
        road = (s.get('name') or '').split(';')[0].strip()
        steps.append((round(km, 2), arrow, text, road)); last_km = km

poly = encode(pts)
with open(out, 'w') as f:
    f.write('package com.cardrop.drive\n\n')
    f.write('// GENERATED by bake_route.py from an OSRM route (OpenStreetMap data, ODbL). Do not edit by hand.\n')
    f.write(f'// {rt["distance"]/1000:.1f} km, {len(full)} points simplified to {len(pts)}.\n\n')
    f.write('internal object DemoRouteData {\n')
    f.write('    /** Google encoded polyline (precision 5) of 신촌 -> 경부 -> 영동고속도로 -> 강릉 경포해변. */\n')
    f.write('    const val POLYLINE: String =\n')
    chunk = 100
    parts = [poly[i:i + chunk] for i in range(0, len(poly), chunk)]
    def kq(s): return '"' + s.replace('\\', '\\\\').replace('"', '\\"').replace('$', '\\$') + '"'
    f.write('        ' + ' +\n        '.join(kq(p) for p in parts) + '\n\n')
    f.write('    /** Turn-by-turn steps: km from the start, arrow, Korean instruction, road name after the turn. */\n')
    f.write('    val STEPS: List<NavStep> = listOf(\n')
    for km, arrow, text, road in steps:
        f.write(f'        NavStep({km}, NavArrow.{arrow}, {kq(text)}, {kq(road)}),\n')
    f.write('    )\n}\n')
print('points', len(full), '->', len(pts), 'polyline chars', len(poly), 'steps', len(steps), 'km', round(cum[-1], 1))
for s in steps: print(s)
