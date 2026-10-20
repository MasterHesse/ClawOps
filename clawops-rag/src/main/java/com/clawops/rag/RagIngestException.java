package com.clawops.rag;

/** 知识库灌入异常（→ KB_INGEST_FAILED）。 */
public class RagIngestException extends RuntimeException {

  public RagIngestException(String message) {
    super(message);
  }

  public RagIngestException(String message, Throwable cause) {
    super(message, cause);
  }
}
