package com.clawops.rag.parse;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/** PDF 解析器（Apache PDFBox）：提取文本，标题取文件名。 */
public class PdfParser implements DocumentParser {

  @Override
  public String supportedType() {
    return "pdf";
  }

  @Override
  public ParsedDocument parse(String fileName, byte[] data) {
    if (data == null || data.length == 0) {
      throw new DocumentParseException("PDF 内容为空: " + fileName);
    }
    try (PDDocument doc = Loader.loadPDF(data)) {
      String text = new PDFTextStripper().getText(doc).trim();
      return new ParsedDocument(stripExtension(fileName), text);
    } catch (DocumentParseException e) {
      throw e;
    } catch (Exception e) {
      throw new DocumentParseException("解析 PDF 失败: " + fileName, e);
    }
  }

  private String stripExtension(String fileName) {
    if (fileName == null) {
      return "untitled";
    }
    int dot = fileName.lastIndexOf('.');
    return dot > 0 ? fileName.substring(0, dot) : fileName;
  }
}
