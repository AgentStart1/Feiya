import { chromium } from "playwright";
import assert from "node:assert/strict";
import { mkdir } from "node:fs/promises";
import { createPreview } from "./preview.mjs";
const output = new URL("./artifacts/", import.meta.url);
await mkdir(output, { recursive: true });
const preview = await createPreview();
const browser = await chromium.launch({ headless: true });
const errors = [],
  external = [];
try {
  const page = await browser.newPage({
    viewport: { width: 1487, height: 1058 },
  });
  await page.context().grantPermissions(["clipboard-read", "clipboard-write"]);
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("request", (request) => {
    if (!request.url().startsWith(preview.url)) external.push(request.url());
  });
  await page.goto(preview.url);
  await page
    .getByRole("link", { name: "下载 项目说明.pdf", exact: true })
    .waitFor();
  await page.getByText("已连接", { exact: true }).waitFor();
  await page.screenshot({
    path: new URL("files-desktop.png", output).pathname,
    fullPage: true,
  });
  const download = page.waitForEvent("download");
  await page
    .getByRole("link", { name: "下载 项目说明.pdf", exact: true })
    .click();
  assert.equal((await download).suggestedFilename(), "sample.txt");
  preview.state.files = [];
  preview.refresh();
  await page.getByText("还没有共享文件").waitFor();
  preview.state.failure = true;
  preview.refresh();
  await page.getByText("暂时无法加载").waitFor();
  assert.equal(await page.locator("#file-table").isVisible(), false);
  preview.state.failure = false;
  preview.state.files = [
    { name: "<script>alert(1)</script>.pdf" },
    { name: "非常长的文件名称".repeat(15) + ".zip" },
  ];
  await page.getByRole("button", { name: "重新加载" }).click();
  await page
    .getByText("<script>alert(1)</script>.pdf", { exact: true })
    .waitFor();
  await page.setViewportSize({ width: 390, height: 844 });
  assert.ok(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  );
  await page.screenshot({
    path: new URL("files-mobile-long.png", output).pathname,
    fullPage: true,
  });
  preview.state.files = [
    "项目说明.pdf",
    "周末照片.jpg",
    "演示文稿.pptx",
    "资料打包.zip",
  ].map((name) => ({ name }));
  preview.refresh();
  await page
    .getByRole("link", { name: "下载 项目说明.pdf", exact: true })
    .waitFor();
  await page.screenshot({
    path: new URL("files-mobile.png", output).pathname,
    fullPage: true,
  });
  await page.getByRole("link", { name: "消息", exact: true }).click();
  await page.getByText("已连接", { exact: true }).waitFor();
  assert.equal(
    await page.getByRole("button", { name: "发送", exact: true }).isEnabled(),
    false,
  );
  await page.getByLabel("消息内容").fill("Hello <script> & 中文");
  await page.getByLabel("消息内容").press("Enter");
  await page
    .locator(".message p")
    .filter({ hasText: "Hello <script> & 中文" })
    .waitFor();
  await page.waitForFunction(
    () => document.getElementById("input").value === "",
  );
  await page.getByRole("button", { name: "复制", exact: true }).click();
  await page.getByText("已复制消息", { exact: true }).waitFor();
  assert.equal(
    await page.evaluate(() => navigator.clipboard.readText()),
    "Hello <script> & 中文",
  );
  await page.evaluate(() => {
    Object.defineProperty(window, "isSecureContext", { value: false });
    document.getElementById("feedback").textContent = "";
  });
  await page.getByRole("button", { name: "复制", exact: true }).click();
  await page.getByText("已复制消息", { exact: true }).waitFor();
  await page.getByLabel("消息内容").fill("保留草稿");
  await page.getByLabel("消息内容").press("Shift+Enter");
  assert.equal(await page.getByLabel("消息内容").inputValue(), "保留草稿\n");
  await page
    .getByLabel("消息内容")
    .dispatchEvent("keydown", { key: "Enter", isComposing: true });
  assert.equal(await page.getByLabel("消息内容").inputValue(), "保留草稿\n");
  for (const socket of preview.wss.clients) socket.close();
  await page.getByText("连接已断开", { exact: true }).waitFor();
  assert.equal(
    await page.getByRole("button", { name: "发送", exact: true }).isEnabled(),
    false,
  );
  assert.equal(await page.getByLabel("消息内容").inputValue(), "保留草稿\n");
  await page.getByRole("button", { name: "重新连接" }).click();
  await page.getByText("已连接", { exact: true }).waitFor();
  assert.equal(await page.locator(".message").count(), 1);
  await page.screenshot({
    path: new URL("chat-mobile.png", output).pathname,
    fullPage: true,
  });
  await page.setViewportSize({ width: 1487, height: 1058 });
  await page.screenshot({
    path: new URL("chat-desktop.png", output).pathname,
    fullPage: true,
  });
  await page.goto(preview.url + "/login");
  assert.equal(
    await page.getByLabel("访问密码", { exact: true }).getAttribute("type"),
    "password",
  );
  await page.getByLabel("访问密码", { exact: true }).fill("wrong");
  await page.getByRole("button", { name: "连接", exact: true }).click();
  await page.getByText("密码不正确，请检查手机上的访问密码。").waitFor();
  await page.screenshot({
    path: new URL("login-desktop.png", output).pathname,
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({
    path: new URL("login-mobile.png", output).pathname,
    fullPage: true,
  });
  await page.getByRole("button", { name: "未设置密码，直接进入" }).click();
  await page.waitForURL(preview.url + "/");
  assert.deepEqual(errors, []);
  assert.deepEqual(external, []);
  console.log(
    "PASS: desktop/mobile layout, download, SSE refresh, empty/error/retry, escaped text, chat send/copy/IME/reconnect, login error/passwordless, no page errors or external requests.",
  );
} finally {
  await browser.close();
  await preview.close();
}
