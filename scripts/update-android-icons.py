#!/usr/bin/env python3
"""Convert the npm Phosphor SVG paths into Android VectorDrawable resources."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
PACKAGE = ROOT / "web/node_modules/@phosphor-icons/core"
RESOURCES = ROOT / "app/src/main/res"
ICONS = {
    "file-pdf": "file_pdf", "file-image": "file_image", "file-zip": "file_zip",
    "file": "file_generic", "keyboard": "nav_keyboard", "folder": "nav_folder",
}
ANDROID = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", ANDROID)

for source, name in ICONS.items():
    svg = ET.parse(PACKAGE / f"assets/regular/{source}.svg").getroot()
    assert svg.attrib["viewBox"] == "0 0 256 256"
    vector = ET.Element("vector", {f"{{{ANDROID}}}{key}": value for key, value in {
        "width": "24dp", "height": "24dp", "viewportWidth": "256", "viewportHeight": "256",
    }.items()})
    for path in svg:
        assert path.tag == "{http://www.w3.org/2000/svg}path", "New SVG structure needs review"
        ET.SubElement(vector, "path", {f"{{{ANDROID}}}fillColor": "#FF000000", f"{{{ANDROID}}}pathData": path.attrib["d"]})
    ET.indent(vector, space="    ")
    (RESOURCES / f"drawable/{name}.xml").write_text(ET.tostring(vector, encoding="unicode") + "\n")
(RESOURCES / "raw/phosphor_license.txt").write_text((PACKAGE / "LICENSE").read_text())
