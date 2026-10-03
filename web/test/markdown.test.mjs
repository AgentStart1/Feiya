import test from "node:test";
import assert from "node:assert/strict";
import { renderMarkdown } from "../src/markdown.mjs";

test("renders common message formatting and multiline text", () => {
  const html = renderMarkdown(
    '# Heading\n\n**bold** *emphasis* ~~removed~~ `code`\nnext line\n\n- first\n- second\n\n> quote\n\n```js\nconst x = "<tag>";\n```\n\n| A | B |\n| - | - |\n| 1 | 2 |',
  );
  for (const tag of [
    "h1",
    "strong",
    "em",
    "s",
    "code",
    "br",
    "ul",
    "li",
    "blockquote",
    "pre",
    "table",
  ])
    assert.match(html, new RegExp(`<${tag}[ >]`));
  assert.ok(html.includes("&lt;tag&gt;"));
});
test("raw HTML and dangerous link schemes cannot become executable markup", () => {
  for (const text of [
    "<script>alert(1)</script>",
    "<img src=x onerror=alert(1)>",
    "<svg onload=alert(1)>",
  ]) {
    const html = renderMarkdown(text);
    assert.ok(html.includes("&lt;"));
    assert.doesNotMatch(html, /<(script|img|svg)[ >]/);
  }
  for (const url of [
    "javascript:alert%281%29",
    "jav&#x61;script:alert%281%29",
    "vbscript:msgbox%281%29",
    "data:text/html;base64,PHNjcmlwdD4=",
    "file:///etc/passwd",
  ]) {
    assert.doesNotMatch(renderMarkdown(`[unsafe](${url})`), /<a /);
  }
});
test("links open separately with isolation and images preserve alt text", () => {
  const html = renderMarkdown(
    '[Docs](https://example.com "title")\n\nhttps://example.org\n\n![sample](https://example.com/image.png)',
  );
  assert.match(html, /target="_blank"/);
  assert.match(html, /rel="noopener noreferrer"/);
  assert.match(html, /href="https:\/\/example.org"/);
  assert.match(html, /alt="sample"/);
  assert.match(html, /loading="lazy"/);
  assert.match(html, /referrerpolicy="no-referrer"/);
});
