import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { readFile, writeFile, mkdir } from "node:fs/promises";
const require = createRequire(import.meta.url);
const root = dirname(require.resolve("markdown-it/package.json"));
const destination = new URL(
  "../../app/src/main/resources/web/vendor/markdown-it/",
  import.meta.url,
);
await mkdir(destination, { recursive: true });
const bundle = await readFile(
  join(root, "dist/browser/markdown-it.esm.min.mjs"),
  "utf8",
);
// Source maps are not shipped with the app; preserve all executable upstream code.
await writeFile(
  new URL("markdown-it.esm.min.mjs", destination),
  bundle.split("//# sourceMappingURL=")[0].trimEnd() + "\n",
);
await writeFile(
  new URL("LICENSE", destination),
  await readFile(join(root, "LICENSE")),
);
const notices = [];
for (const [name, license] of [
  ["entities", "LICENSE"],
  ["linkify-it", "LICENSE"],
  ["mdurl", "LICENSE"],
  ["punycode.js", "LICENSE-MIT.txt"],
  ["uc.micro", "LICENSE.txt"],
]) {
  let path = dirname(require.resolve(name));
  while (true) {
    try {
      const pkg = JSON.parse(
        await readFile(join(path, "package.json"), "utf8"),
      );
      if (pkg.name === name) break;
    } catch {
      /* The entry point can be inside a dist directory. */
    }
    const parent = dirname(path);
    if (parent === path) throw new Error(`Cannot locate license for ${name}`);
    path = parent;
  }
  notices.push(
    `${name}\n${"=".repeat(name.length)}\n${await readFile(join(path, license), "utf8")}`,
  );
}
await writeFile(
  new URL("THIRD-PARTY-NOTICES.txt", destination),
  notices.join("\n\n"),
);
