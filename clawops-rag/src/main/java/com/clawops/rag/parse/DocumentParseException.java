package com.clawops.rag.parse;

/** 文档解析异常（→ KB_INGEST_FAILED / KB_PARSE_FAILED）。 */
public class DocumentParseException extends RuntimeException {

  public DocumentParseException(String message, Throwable cause) {
    super(message, cause);
  }

  public DocumentParseException(String message) {
    super(message);
  }
}
