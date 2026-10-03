import test from "node:test";
import assert from "node:assert/strict";
import { readFile, readdir } from "node:fs/promises";
const dist = new URL("../dist/", import.meta.url);

test("the distribution includes every same-origin page and CSS asset reference", async () => {
  const pages = await readdir(new URL("feiya/", dist));
  assert.deepEqual(pages.sort(), ["chat.html", "index.html", "login.html"]);
  for (const page of pages) {
    const html = await readFile(new URL(`feiya/${page}`, dist), "utf8");
    for (const [, path] of html.matchAll(/(?:src|href)="(\/web\/[^"#]+)"/g)) {
      assert.ok(
        (await readFile(new URL(path.slice(1), dist))).length > 0,
        path,
      );
    }
  }
  const css = await readFile(new URL("web/style.css", dist), "utf8");
  for (const [, path] of css.matchAll(/url\(["']?(\/web\/[^"')]+)["']?\)/g)) {
    assert.ok((await readFile(new URL(path.slice(1), dist))).length > 0, path);
  }
  assert.ok((await readFile(new URL("web/worker.js", dist))).length > 0);
});

test("the runtime has no copied source modules, vendored tree or node_modules", async () => {
  assert.deepEqual((await readdir(dist)).sort(), ["feiya", "web"]);
  assert.deepEqual((await readdir(new URL("web/", dist))).sort(), [
    "THIRD-PARTY-NOTICES.txt",
    "app.js",
    "icons",
    "style.css",
    "worker.js",
  ]);
  const pkg = JSON.parse(
    await readFile(new URL("../package.json", import.meta.url), "utf8"),
  );
  const notices = await readFile(
    new URL("web/THIRD-PARTY-NOTICES.txt", dist),
    "utf8",
  );
  for (const name of Object.keys(pkg.dependencies))
    assert.ok(notices.includes(name), name);
  assert.ok(notices.includes("MIT License"));
});
