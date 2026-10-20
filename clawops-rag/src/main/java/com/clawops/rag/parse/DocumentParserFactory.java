package com.clawops.rag.parse;

import java.util.Locale;

/** 按文件扩展名分发到对应解析器；未知类型抛 {@link DocumentParseException}。 */
public class DocumentParserFactory {

  private final MarkdownParser markdown = new MarkdownParser();
  private final PlainTextParser plainText = new PlainTextParser();
  private final PdfParser pdf = new PdfParser();

  public DocumentParser forFileName(String fileName) {
    String ext = extensionOf(fileName);
    return switch (ext) {
      case "md" -> markdown;
      case "txt" -> plainText;
      case "pdf" -> pdf;
      default -> throw new DocumentParseException("不支持的文档类型: " + ext + "（" + fileName + "）");
    };
  }

  public ParsedDocument parse(String fileName, byte[] data) {
    return forFileName(fileName).parse(fileName, data);
  }

  private String extensionOf(String fileName) {
    if (fileName == null) {
      return "";
    }
    int dot = fileName.lastIndexOf('.');
    return dot > 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
  }
}
