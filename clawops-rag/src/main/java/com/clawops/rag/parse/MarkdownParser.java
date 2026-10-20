package com.clawops.rag.parse;

import java.nio.charset.StandardCharsets;

/** Markdown 解析器：去标记保留文本，标题取首个 # 行。 */
public class MarkdownParser implements DocumentParser {

  @Override
  public String supportedType() {
    return "md";
  }

  @Override
  public ParsedDocument parse(String fileName, byte[] data) {
    String content = data == null ? "" : new String(data, StandardCharsets.UTF_8);
    String text = stripMarkdown(content);
    String title = extractTitle(content, fileName);
    return new ParsedDocument(title, text);
  }

  private String stripMarkdown(String md) {
    StringBuilder out = new StringBuilder();
    for (String line : md.split("\\R")) {
      String trimmed = line.trim();
      trimmed = trimmed.replaceAll("^#{1,6}\\s*", "");
      trimmed = trimmed.replaceAll("^[*>-]+\\s*", "");
      trimmed = trimmed.replaceAll("`([^`]*)`", "$1");
      trimmed = trimmed.replaceAll("\\*\\*([^*]*)\\*\\*", "$1");
      trimmed = trimmed.replaceAll("\\*([^*]*)\\*", "$1");
      if (!trimmed.isEmpty()) {
        out.append(trimmed).append('\n');
      }
    }
    return out.toString().trim();
  }

  private String extractTitle(String content, String fileName) {
    for (String line : content.split("\\R", 2)) {
      String trimmed = line.trim();
      if (trimmed.startsWith("#")) {
        return trimmed.replaceAll("^#{1,6}\\s*", "").trim();
      }
    }
    return stripExtension(fileName);
  }

  private String stripExtension(String fileName) {
    if (fileName == null) {
      return "untitled";
    }
    int dot = fileName.lastIndexOf('.');
    return dot > 0 ? fileName.substring(0, dot) : fileName;
  }
}
