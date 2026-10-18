worldcheck

Generates test worlds on the development dedicated server and analyses their region files.
Python 3 standard library only; run from the repository root.

```
# Vastlands world, seed 20261018, 16x16 chunks around 0,0 (writes run/eula.txt: you accept the Minecraft EULA)
python3 tools/worldcheck/generate.py --level-name vl_a --fresh --accept-eula

# Same seed again, and a vanilla baseline
python3 tools/worldcheck/generate.py --level-name vl_b --fresh
python3 tools/worldcheck/generate.py --level-name vanilla_a --level-type minecraft:normal --fresh

# One world, or a comparison of two
python3 tools/worldcheck/analyze.py run/vl_a
python3 tools/worldcheck/analyze.py run/vl_a run/vl_b
```

- `generate.py` overwrites `run/server.properties`, starts `./gradlew runServer`, force-loads the
  chunk square, waits until every chunk is fully generated and saved, probes the build limits with
  `setblock` over RCON (port 25575), stops the server, and writes `run/<level-name>/worldcheck.json`.
  Server output goes to `run/worldcheck-<level-name>.log`.
- `analyze.py` reports the section range, bedrock floor, block composition per height band and
  surface heights; with two worlds, the share of identical blocks, biomes and surface heights.
  The probe column is excluded.
- Vanilla decoration is not bit-for-bit reproducible between runs (features crossing chunk borders
  depend on generation order). Judge determinism against a vanilla-vs-vanilla baseline.
