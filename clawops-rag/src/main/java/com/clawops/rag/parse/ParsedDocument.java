package com.clawops.rag.parse;

/** 文档解析结果（标题 + 纯文本）。 */
public record ParsedDocument(String title, String text) {}
