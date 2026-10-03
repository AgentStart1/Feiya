import MarkdownIt from "markdown-it";

// Keep raw HTML disabled: only parser-generated markup reaches the DOM.
const markdown = new MarkdownIt({ html: false, breaks: true, linkify: true });
const defaultLink = markdown.renderer.rules.link_open;
markdown.renderer.rules.link_open = (tokens, index, options, env, renderer) => {
  tokens[index].attrSet("target", "_blank");
  tokens[index].attrSet("rel", "noopener noreferrer");
  return defaultLink
    ? defaultLink(tokens, index, options, env, renderer)
    : renderer.renderToken(tokens, index, options);
};
const defaultImage = markdown.renderer.rules.image;
markdown.renderer.rules.image = (tokens, index, options, env, renderer) => {
  tokens[index].attrSet("loading", "lazy");
  tokens[index].attrSet("referrerpolicy", "no-referrer");
  return defaultImage(tokens, index, options, env, renderer);
};

export const renderMarkdown = (text) => markdown.render(text);
