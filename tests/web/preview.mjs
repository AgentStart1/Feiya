import http from "node:http";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { WebSocketServer } from "ws";
const root = new URL("../../app/src/main/resources/", import.meta.url);
export async function createPreview(port = 0) {
  const state = {
    files: [
      "项目说明.pdf",
      "周末照片.jpg",
      "演示文稿.pptx",
      "资料打包.zip",
    ].map((name) => ({ name })),
    failure: false,
    events: new Set(),
  };
  const server = http.createServer(async (req, res) => {
    const pathname = new URL(req.url, "http://localhost").pathname;
    if (pathname === "/shares") {
      res.writeHead(state.failure ? 503 : 200, {
        "Content-Type": "application/json",
      });
      res.end(JSON.stringify(state.files));
      return;
    }
    if (pathname.startsWith("/shares/")) {
      res.writeHead(200, {
        "Content-Disposition": 'attachment; filename="sample.txt"',
      });
      res.end("Preview download fixture");
      return;
    }
    if (pathname === "/sse") {
      res.writeHead(200, {
        "Content-Type": "text/event-stream",
        "Cache-Control": "no-cache",
      });
      res.write(": ready\n\n");
      state.events.add(res);
      req.on("close", () => state.events.delete(res));
      return;
    }
    if (pathname === "/login" && req.method === "POST") {
      let body = "";
      for await (const chunk of req) body += chunk;
      if (new URLSearchParams(body).get("password") === "wrong") {
        res.writeHead(401);
        res.end("invalid");
      } else {
        res.writeHead(302, { Location: "/" });
        res.end();
      }
      return;
    }
    const path =
      { "/": "feiya/index.html", "/login": "feiya/login.html", "/messages": "feiya/chat.html" }[
        pathname
      ] || pathname.slice(1);
    if (
      !/^(web\/[a-z0-9/.-]+|feiya\/(index.html|login.html|chat.html))$/.test(path) ||
      path.includes("..")
    ) {
      res.writeHead(404);
      res.end();
      return;
    }
    try {
      const bytes = await readFile(new URL(path, root));
      const type = path.endsWith(".css")
        ? "text/css"
        : path.endsWith(".js")
          ? "text/javascript"
          : path.endsWith(".svg")
            ? "image/svg+xml"
            : "text/html";
      res.writeHead(200, { "Content-Type": `${type}; charset=utf-8` });
      res.end(bytes);
    } catch {
      res.writeHead(404);
      res.end();
    }
  });
  const wss = new WebSocketServer({ server, path: "/chat" });
  wss.on("connection", (socket) =>
    socket.on("message", (data) => {
      for (const peer of wss.clients)
        if (peer.readyState === 1)
          peer.send(JSON.stringify({ from: "user1", data: data.toString() }));
    }),
  );
  await new Promise((resolve) => server.listen(port, "127.0.0.1", resolve));
  return {
    state,
    wss,
    url: `http://127.0.0.1:${server.address().port}`,
    refresh() {
      for (const stream of state.events) stream.write("data: refresh\n\n");
    },
    async close() {
      for (const peer of wss.clients) peer.terminate();
      wss.close();
      server.closeAllConnections();
      await new Promise((resolve) => server.close(resolve));
    },
  };
}
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const preview = await createPreview(Number(process.env.PORT || 4173));
  console.log(`Feiya preview (mock API): ${preview.url}`);
}
