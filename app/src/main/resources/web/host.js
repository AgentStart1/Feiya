// This module runs only in a dedicated Worker. It owns network tasks and state;
// the DOM layer receives structured-cloned snapshots and one-time effects.
export class WebHost {
  constructor(io, publish, effect) {
    this.io = io;
    this.publish = publish;
    this.effect = effect;
    this.state = {
      connection: "connecting",
      files: [],
      loading: true,
      error: "",
      messages: [],
      busy: false,
    };
    this.closed = false;
  }
  update(patch) {
    if (this.closed) return;
    this.state = Object.freeze({ ...this.state, ...patch });
    this.publish(this.state);
  }
  start(page) {
    this.page = page;
    if (page === "files") {
      this.source = this.io.events("/sse");
      this.source.onopen = () => {
        this.update({ connection: "connected" });
        this.refresh();
      };
      this.source.onerror = () => this.update({ connection: "disconnected" });
      this.source.onmessage = (e) => {
        if (e.data === "refresh") this.refresh();
      };
      this.refresh();
    } else if (page === "chat") this.connect();
    else this.update({ connection: "waiting", loading: false });
  }
  async refresh() {
    this.request?.abort();
    const request = (this.request = new AbortController());
    this.update({ loading: true, error: "" });
    try {
      const response = await this.io.fetch("/shares", {
        signal: request.signal,
      });
      if (request.signal.aborted || this.closed) return;
      if (response.redirected && new URL(response.url).pathname === "/login") {
        this.effect({ type: "navigate", url: "/login" });
        return;
      }
      if (!response.ok) throw new Error("load");
      const files = await response.json();
      if (
        !Array.isArray(files) ||
        files.some((f) => typeof f.name !== "string")
      )
        throw new Error("data");
      if (request.signal.aborted || this.closed) return;
      this.update({
        files: Object.freeze(
          files.map((f, index) => Object.freeze({ name: f.name, index })),
        ),
        loading: false,
      });
    } catch (e) {
      if (!request.signal.aborted)
        this.update({
          loading: false,
          error: "无法获取文件。请检查手机服务后重试。",
        });
    }
  }
  connect() {
    if (this.closed) return;
    if (this.socket) {
      this.socket.onclose = null;
      this.socket.close();
    }
    this.update({ connection: "connecting", error: "" });
    const socket = (this.socket = this.io.socket());
    const current = () => !this.closed && this.socket === socket;
    socket.onopen = () => {
      if (current()) this.update({ connection: "connected" });
    };
    socket.onerror = socket.onclose = () => {
      if (current())
        this.update({
          connection: "disconnected",
          error: "连接已断开。重新连接后可继续发送。",
        });
    };
    socket.onmessage = (event) => {
      if (!current()) return;
      try {
        const message = JSON.parse(event.data);
        if (
          typeof message.from !== "string" ||
          typeof message.data !== "string"
        )
          throw new Error("data");
        this.update({
          messages: Object.freeze([
            ...this.state.messages,
            Object.freeze(message),
          ]),
        });
      } catch {
        this.update({ error: "收到无法识别的消息。" });
      }
    };
  }
  send(text) {
    if (!text.trim() || this.closed) return;
    if (this.socket?.readyState !== 1) {
      this.update({ error: "尚未连接，消息未发送。" });
      return;
    }
    try {
      this.socket.send(text);
      this.update({ error: "" });
      this.effect({ type: "sent", text });
    } catch {
      this.update({ error: "发送失败，文字已保留，请重试。" });
    }
  }
  async login(password) {
    if (this.state.busy || this.closed) return;
    const request = (this.request = new AbortController());
    const timer = this.io.setTimeout(() => request.abort(), 15000);
    this.update({ busy: true, error: "" });
    try {
      const response = await this.io.fetch("/login", {
        method: "POST",
        body: new URLSearchParams({ user: "hidden", password }),
        signal: request.signal,
      });
      if (this.closed) return;
      if (response.ok) this.effect({ type: "navigate", url: "/" });
      else
        this.update({
          error:
            response.status === 401
              ? "密码不正确，请检查手机上的访问密码。"
              : "暂时无法连接，请稍后重试。",
        });
    } catch {
      this.update({ error: "连接失败，请确认手机服务仍在运行。" });
    } finally {
      this.io.clearTimeout(timer);
      this.update({ busy: false });
    }
  }
  close() {
    this.closed = true;
    this.request?.abort();
    this.source?.close();
    this.socket?.close();
  }
}
