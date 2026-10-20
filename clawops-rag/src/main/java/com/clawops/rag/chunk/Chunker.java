package com.clawops.rag.chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文本切分器：可调 chunkSize / overlap。
 *
 * <p>策略：按段落（双换行）切；段落 ≤ chunkSize 时整段为一个 chunk（不跨段合并）； 段落超长时按句子边界（。！？.!?）拆分并打包到 chunkSize，超长句子按字符硬切。
 * 相邻 chunk 按 overlap 在边界重叠（下一个 chunk 开头 = 上一个 chunk 结尾的 overlap 字符）。
 *
 * @param chunkSize 单个 chunk 的最大字符数（≥50）
 * @param overlap 相邻 chunk 重叠字符数（0 ≤ overlap < chunkSize）
 */
public class Chunker {

  private static final Pattern SENTENCE = Pattern.compile("[^。！？.!?]+[。！？.!?]*");

  public List<Chunk> split(String text, int chunkSize, int overlap) {
    if (chunkSize < 50) {
      throw new IllegalArgumentException("chunkSize must be >= 50");
    }
    if (overlap < 0 || overlap >= chunkSize) {
      throw new IllegalArgumentException("overlap must be in [0, chunkSize)");
    }
    if (text == null || text.isBlank()) {
      return List.of();
    }

    List<Chunk> chunks = new ArrayList<>();
    String prevTail = "";
    int seq = 0;
    for (String paragraph : text.trim().split("\\n\\s*\\n")) {
      if (paragraph.isBlank()) {
        continue;
      }
      List<String> pieces =
          paragraph.length() <= chunkSize
              ? List.of(paragraph)
              : packSentences(paragraph, chunkSize);
      for (String piece : pieces) {
        String content = (overlap > 0 && !prevTail.isEmpty()) ? prevTail + piece : piece;
        chunks.add(new Chunk(++seq, content));
        prevTail = overlap > 0 ? content.substring(Math.max(0, content.length() - overlap)) : "";
      }
    }
    return chunks;
  }

  private List<String> packSentences(String paragraph, int chunkSize) {
    List<String> sentences = splitIntoSentences(paragraph, chunkSize);
    List<String> packed = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    for (String s : sentences) {
      if (cur.length() + s.length() > chunkSize && cur.length() > 0) {
        packed.add(cur.toString());
        cur.setLength(0);
      }
      cur.append(s);
    }
    if (cur.length() > 0) {
      packed.add(cur.toString());
    }
    return packed;
  }

  private List<String> splitIntoSentences(String paragraph, int chunkSize) {
    List<String> sentences = new ArrayList<>();
    Matcher m = SENTENCE.matcher(paragraph);
    int lastEnd = 0;
    while (m.find()) {
      String s = m.group();
      if (s.length() > chunkSize) {
        sentences.addAll(hardSplit(s, chunkSize));
      } else {
        sentences.add(s);
      }
      lastEnd = m.end();
    }
    if (lastEnd < paragraph.length()) {
      String tail = paragraph.substring(lastEnd);
      if (!tail.isBlank()) {
        sentences.addAll(hardSplit(tail, chunkSize));
      }
    }
    return sentences;
  }

  private List<String> hardSplit(String text, int chunkSize) {
    List<String> parts = new ArrayList<>();
    for (int i = 0; i < text.length(); i += chunkSize) {
      parts.add(text.substring(i, Math.min(i + chunkSize, text.length())));
    }
    return parts;
  }
}
