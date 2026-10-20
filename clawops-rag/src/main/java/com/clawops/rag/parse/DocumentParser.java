package com.clawops.rag.parse;

/** 文档解析器：将原始文档（md/txt/pdf 字节）解析为纯文本。 */
public interface DocumentParser {

  /** 支持的源类型（md / txt / pdf）。 */
  String supportedType();

  /** 解析文档字节为纯文本。 */
  ParsedDocument parse(String fileName, byte[] data);
}
