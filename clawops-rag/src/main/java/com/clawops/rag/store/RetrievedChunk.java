package com.clawops.rag.store;

/** 检索召回的切分（含来源标记与分数）。 */
public record RetrievedChunk(
    String chunkId,
    String docId,
    String docTitle,
    int seq,
    String content,
    double score,
    String source) {}
