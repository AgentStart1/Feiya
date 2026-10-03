import { WebHost } from "./host.js";
import { renderMarkdown } from "./markdown.mjs";
const host = new WebHost(
  {
    renderMarkdown,
    fetch: (...args) => fetch(...args),
    events: (url) => new EventSource(url),
    socket: () =>
      new WebSocket(
        `${location.protocol === "https:" ? "wss:" : "ws:"}//${location.host}/chat`,
      ),
    setTimeout: (callback, delay) => setTimeout(callback, delay),
    clearTimeout: (timer) => clearTimeout(timer),
  },
  (state) => postMessage({ type: "state", state }),
  (effect) => postMessage(effect),
);
onmessage = ({ data }) => {
  if (data.type === "start") host.start(data.page);
  if (data.type === "refresh") host.refresh();
  if (data.type === "connect") host.connect();
  if (data.type === "send") host.send(data.text);
  if (data.type === "login") host.login(data.password);
  if (data.type === "close") host.close();
};
