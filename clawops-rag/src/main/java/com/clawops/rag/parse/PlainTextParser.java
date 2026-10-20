package com.clawops.rag.parse;

import java.nio.charset.StandardCharsets;

/** 纯文本解析器：原样保留，标题取首行或文件名。 */
public class PlainTextParser implements DocumentParser {

  @Override
  public String supportedType() {
    return "txt";
  }

  @Override
  public ParsedDocument parse(String fileName, byte[] data) {
    String content = data == null ? "" : new String(data, StandardCharsets.UTF_8);
    return new ParsedDocument(extractTitle(content, fileName), content.trim());
  }

  private String extractTitle(String content, String fileName) {
    if (!content.isBlank()) {
      String firstLine = content.split("\\R", 2)[0].trim();
      if (!firstLine.isEmpty()) {
        return firstLine.length() > 100 ? firstLine.substring(0, 100) : firstLine;
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
