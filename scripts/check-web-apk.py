#!/usr/bin/env python3
"""Check that APK classpath resources exactly match the npm web distribution."""
import argparse
from pathlib import Path
from zipfile import ZipFile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("distribution", type=Path)
    parser.add_argument("apk", type=Path, nargs="+")
    args = parser.parse_args()
    expected = {
        path.relative_to(args.distribution).as_posix(): path.read_bytes()
        for path in args.distribution.rglob("*") if path.is_file()
    }
    required = {"feiya/index.html", "feiya/login.html", "feiya/chat.html",
                "web/app.js", "web/worker.js", "web/style.css",
                "web/THIRD-PARTY-NOTICES.txt"}
    if not required <= expected.keys():
        parser.error("Web distribution is missing required entries; run the npm build first.")
    for apk in args.apk:
        with ZipFile(apk) as archive:
            entries = [entry.filename for entry in archive.infolist()
                       if not entry.is_dir() and entry.filename.startswith(("web/", "feiya/"))]
            if len(entries) != len(set(entries)):
                raise SystemExit(f"{apk}: duplicate web resources")
            missing = expected.keys() - set(entries)
            extra = set(entries) - expected.keys()
            if missing or extra:
                raise SystemExit(f"{apk}: missing {sorted(missing)}, unexpected {sorted(extra)}")
            for name, data in expected.items():
                if archive.read(name) != data:
                    raise SystemExit(f"{apk}: stale or changed resource {name}")
            if any("node_modules/" in entry.filename for entry in archive.infolist()):
                raise SystemExit(f"{apk}: contains node_modules")
        print(f"PASS {apk}: {len(expected)} web resources match the npm distribution")


if __name__ == "__main__":
    main()
