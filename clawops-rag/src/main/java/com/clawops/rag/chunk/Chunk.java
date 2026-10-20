package com.clawops.rag.chunk;

/** 文本切分单元。 */
public record Chunk(int seq, String content) {

  public Chunk {
    if (seq < 1) {
      throw new IllegalArgumentException("seq must be >= 1");
    }
    if (content == null) {
      throw new NullPointerException("content must not be null");
    }
  }
}
