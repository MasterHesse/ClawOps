package com.clawops.rag.embedding;

import java.util.List;

/**
 * Embedding 提供者抽象（gateway 注入：真实 LangChain4j OpenAiEmbeddingModel 或 Fake）。
 *
 * <p>rag 主代码仅依赖本接口，不直接依赖 LangChain4j（ADR-009）。
 */
public interface EmbeddingProvider {

  float[] embed(String text);

  List<float[]> embedAll(List<String> texts);

  int dimension();
}
