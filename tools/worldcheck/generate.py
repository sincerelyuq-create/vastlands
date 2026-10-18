"""Generates a test world on a dev dedicated server (./gradlew runServer) and probes its build limits.

Writes run/server.properties, starts the server, force-loads a square of chunks around 0,0, waits until
every chunk in it is fully generated and saved, runs setblock probes at the build limits through RCON,
then stops the server. Results go to run/<level-name>/worldcheck.json; analyze.py reads the region files.

Running a Minecraft server requires accepting the Minecraft EULA (https://aka.ms/MinecraftEULA);
pass --accept-eula to write run/eula.txt.
"""
import argparse
import json
import os
import shutil
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from anvil import read_chunk  # noqa: E402
from rcon import Rcon  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RUN = os.path.join(ROOT, "run")
RCON_PORT = 25575
RCON_PASSWORD = "worldcheck"


def write_properties(args):
    props = {
        "level-name": args.level_name,
        "level-type": args.level_type.replace(":", "\\:"),
        "level-seed": str(args.seed),
        "online-mode": "false",
        "enable-rcon": "true",
        "rcon.port": str(RCON_PORT),
        "rcon.password": RCON_PASSWORD,
        "spawn-protection": "0",
        "view-distance": "4",
        "simulation-distance": "4",
        "max-tick-time": "-1",
        "server-port": "25565",
        "spawn-monsters": "false",
        "spawn-animals": "false",
        "generate-structures": "true",
    }
    with open(os.path.join(RUN, "server.properties"), "w") as f:
        f.write("".join("%s=%s\n" % kv for kv in props.items()))


def wait_for(log_path, needle, proc, timeout):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if proc.poll() is not None:
            raise RuntimeError("server exited early; see " + log_path)
        with open(log_path, errors="replace") as f:
            if needle in f.read():
                return
        time.sleep(2)
    raise TimeoutError("'%s' not seen in %s" % (needle, log_path))


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--level-name", required=True)
    p.add_argument("--level-type", default="vastlands:vastlands")
    p.add_argument("--seed", type=int, default=20261018)
    p.add_argument("--radius", type=int, default=8, help="half-size of the chunk square around 0,0 (max 8)")
    p.add_argument("--accept-eula", action="store_true")
    p.add_argument("--fresh", action="store_true", help="delete an existing world with this name first")
    args = p.parse_args()
    if not 1 <= args.radius <= 8:
        p.error("--radius must be 1..8 (forceload is limited to 256 chunks)")

    os.makedirs(RUN, exist_ok=True)
    if args.accept_eula:
        with open(os.path.join(RUN, "eula.txt"), "w") as f:
            f.write("eula=true\n")
    world = os.path.join(RUN, args.level_name)
    if args.fresh and os.path.isdir(world):
        shutil.rmtree(world)
    write_properties(args)

    log_path = os.path.join(RUN, "worldcheck-%s.log" % args.level_name)
    log = open(log_path, "w")
    proc = subprocess.Popen(["./gradlew", "runServer", "--console=plain"], cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    report = {"level_name": args.level_name, "level_type": args.level_type, "seed": args.seed, "radius": args.radius}
    try:
        wait_for(log_path, "RCON running on", proc, 1800)
        rcon = Rcon("127.0.0.1", RCON_PORT, RCON_PASSWORD)
        lo, hi = -args.radius, args.radius - 1
        report["forceload"] = rcon.command("forceload add %d %d %d %d" % (lo * 16, lo * 16, hi * 16 + 15, hi * 16 + 15))

        region = os.path.join(world, "region")
        start = time.time()
        while True:
            rcon.command("save-all flush")
            pending = [(x, z) for x in range(lo, hi + 1) for z in range(lo, hi + 1)
                       if (read_chunk(region, x, z) or {}).get("Status") != "minecraft:full"]
            if not pending:
                break
            if time.time() - start > 1800:
                raise TimeoutError("%d chunks never reached full status" % len(pending))
            time.sleep(5)
        report["generation_seconds"] = round(time.time() - start, 1)

        # Build limit probes in the corner column of the square (analyze.py skips it).
        x, z = lo * 16, lo * 16
        probes = {}
        for y in (511, 512, 319, 320, -64, -65, -256, -257):
            probes[str(y)] = rcon.command("setblock %d %d %d minecraft:glass" % (x, y, z))
        report["setblock_probes"] = probes
        report["probe_column"] = [x, z]
        rcon.command("save-all flush")
        rcon.command("stop")
        rcon.close()
        proc.wait(timeout=300)
    finally:
        if proc.poll() is None:
            proc.terminate()
            proc.wait(timeout=60)
        log.close()

    with open(os.path.join(world, "worldcheck.json"), "w") as f:
        json.dump(report, f, indent=2)
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
