package com.clawops.rag.embedding;

import java.util.List;

/**
 * 确定性 hash 向量（无 LLM key / CI 环境使用）：同文本同向量，dimension=1536 与 kb_chunk 列对齐。
 *
 * <p>非语义向量，仅用于管线验证；真实语义检索需注入 LangChain4j 实现。
 */
public class FakeEmbeddingProvider implements EmbeddingProvider {

  private static final int DIMENSION = 1536;

  @Override
  public float[] embed(String text) {
    float[] v = new float[DIMENSION];
    int h = text == null ? 0 : text.hashCode();
    for (int i = 0; i < DIMENSION; i++) {
      v[i] = ((h >>> (i % 32)) & 1) == 1 ? 1f : 0f;
    }
    return v;
  }

  @Override
  public List<float[]> embedAll(List<String> texts) {
    return texts.stream().map(this::embed).toList();
  }

  @Override
  public int dimension() {
    return DIMENSION;
  }
}
