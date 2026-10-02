import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
// Import the production Worker module without imposing a package type on Android resources.
const source = await readFile(
  new URL("../../app/src/main/resources/web/host.js", import.meta.url),
  "utf8",
);
const { WebHost } = await import(
  `data:text/javascript;base64,${Buffer.from(source).toString("base64")}`
);
function setup(overrides = {}) {
  const states = [],
    effects = [],
    sockets = [];
  const io = {
    fetch: async () => ({ ok: true, json: async () => [] }),
    events: () => ({ close() {} }),
    socket: () => {
      const socket = {
        readyState: 1,
        sent: [],
        send(text) {
          this.sent.push(text);
        },
        close() {},
      };
      sockets.push(socket);
      return socket;
    },
    setTimeout: () => 1,
    clearTimeout() {},
    ...overrides,
  };
  return {
    host: new WebHost(
      io,
      (s) => states.push(s),
      (e) => effects.push(e),
    ),
    states,
    effects,
    sockets,
  };
}
test("newer refresh wins even when an aborted response resolves late", async () => {
  const pending = [];
  const { host } = setup({
    fetch: () => new Promise((resolve) => pending.push(resolve)),
  });
  const first = host.refresh(),
    second = host.refresh();
  pending[1]({ ok: true, json: async () => [{ name: "new.pdf" }] });
  await second;
  pending[0]({ ok: true, json: async () => [{ name: "old.pdf" }] });
  await first;
  assert.equal(host.state.files[0].name, "new.pdf");
  assert.ok(Object.isFrozen(host.state.files));
});
test("failed refresh reports failure; close cancels late updates", async () => {
  let resolve;
  const { host, states } = setup({
    fetch: () =>
      new Promise((r) => {
        resolve = r;
      }),
  });
  const task = host.refresh();
  resolve({ ok: false });
  await task;
  assert.match(host.state.error, /无法获取/);
  const later = host.refresh();
  host.close();
  const count = states.length;
  resolve({ ok: true, json: async () => [{ name: "late" }] });
  await later;
  assert.equal(states.length, count);
});
test("redirected file requests navigate to login", async () => {
  const { host, effects } = setup({
    fetch: async () => ({ redirected: true, url: "http://example.test/login" }),
  });
  await host.refresh();
  assert.deepEqual(effects, [{ type: "navigate", url: "/login" }]);
});
test("socket reconnect isolates old callbacks and never replays text", () => {
  const { host, sockets, effects } = setup();
  host.connect();
  const old = sockets[0];
  old.onopen();
  host.send("hello");
  assert.deepEqual(old.sent, ["hello"]);
  assert.equal(effects[0].text, "hello");
  const oldMessage = old.onmessage;
  host.connect();
  oldMessage({ data: JSON.stringify({ from: "old", data: "stale" }) });
  assert.equal(host.state.messages.length, 0);
  assert.deepEqual(sockets[1].sent, []);
  sockets[1].readyState = 3;
  host.send("draft");
  assert.match(host.state.error, /未发送/);
});
test("socket payloads are validated and resources are closed", () => {
  const { host, sockets } = setup();
  host.connect();
  sockets[0].onmessage({ data: "{" });
  assert.match(host.state.error, /无法识别/);
  sockets[0].onmessage({
    data: JSON.stringify({ from: "user", data: "<script>" }),
  });
  assert.equal(host.state.messages[0].data, "<script>");
  let closed = false;
  sockets[0].close = () => {
    closed = true;
  };
  host.close();
  assert.ok(closed);
});
test("login distinguishes invalid password and successful navigation", async () => {
  let ok = false;
  let body;
  const { host, effects } = setup({
    fetch: async (url, options) => {
      body = options.body;
      return { ok, status: 401 };
    },
  });
  await host.login("wrong");
  assert.match(host.state.error, /密码不正确/);
  assert.equal(host.state.busy, false);
  ok = true;
  await host.login("");
  assert.equal(body.get("password"), "");
  assert.equal(body.get("user"), "hidden");
  assert.deepEqual(effects, [{ type: "navigate", url: "/" }]);
});
