import { build } from "esbuild";
import { cp, mkdir, readFile, readdir, rm, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("../", import.meta.url));
const output = join(root, "dist");
const require = createRequire(import.meta.url);
await rm(output, { recursive: true, force: true });
await mkdir(join(output, "web/icons"), { recursive: true });
await cp(join(root, "src/pages"), join(output, "feiya"), { recursive: true });

const result = await build({
  absWorkingDir: root,
  entryPoints: {
    app: "src/app.js",
    worker: "src/worker.js",
    style: "src/style.css",
  },
  outdir: join(output, "web"),
  bundle: true,
  format: "esm",
  platform: "browser",
  target: ["es2022"],
  minify: true,
  // Icons keep their stable, same-origin URLs in CSS.
  external: ["/web/icons/*"],
  metafile: true,
  legalComments: "eof",
});

async function packageAt(path) {
  let directory = dirname(path);
  while (true) {
    try {
      const pkg = JSON.parse(
        await readFile(join(directory, "package.json"), "utf8"),
      );
      if (pkg.name) return { directory, ...pkg };
    } catch (error) {
      if (error.code !== "ENOENT") throw error;
    }
    const parent = dirname(directory);
    if (parent === directory)
      throw new Error(`No package manifest for ${path}`);
    directory = parent;
  }
}

const icons = await packageAt(require.resolve("@phosphor-icons/core"));
// Read the icon names from the production stylesheet instead of maintaining a second list.
const css = await readFile(join(root, "src/style.css"), "utf8");
for (const [, name] of css.matchAll(/\/web\/icons\/([a-z-]+)\.svg/g)) {
  const weight = name.endsWith("-fill") ? "fill" : "regular";
  await cp(
    join(icons.directory, "assets", weight, `${name}.svg`),
    join(output, "web/icons", `${name}.svg`),
  );
}

// Derive notices from actual bundled modules, so transitive dependency updates
// cannot silently leave a hand-maintained license list behind.
const packages = new Map([[icons.directory, icons]]);
for (const input of Object.keys(result.metafile.inputs)) {
  if (!input.includes("node_modules/")) continue;
  const pkg = await packageAt(resolve(root, input));
  packages.set(pkg.directory, pkg);
}
const notices = [];
for (const pkg of [...packages.values()].sort((a, b) =>
  a.name.localeCompare(b.name),
)) {
  const files = (await readdir(pkg.directory))
    .filter((name) => /^(licen[cs]e|copying)([.-]|$)/i.test(name))
    .sort();
  if (!files.length) throw new Error(`No license file found for ${pkg.name}`);
  const licenses = await Promise.all(
    files.map((name) => readFile(join(pkg.directory, name), "utf8")),
  );
  notices.push(`${pkg.name} ${pkg.version}\n${licenses.join("\n")}`);
}
await writeFile(
  join(output, "web/THIRD-PARTY-NOTICES.txt"),
  notices.join("\n\n"),
);
console.log(
  "Built web/dist (HTML, bundled JS/CSS, icons, and third-party notices).",
);
