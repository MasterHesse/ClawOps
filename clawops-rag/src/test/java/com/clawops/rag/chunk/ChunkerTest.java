package com.clawops.rag.chunk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Chunker 文本切分（conventions/testing.md §3 TDD 强制范围）。 */
@DisplayName("Chunker 文本切分")
class ChunkerTest {

  private final Chunker chunker = new Chunker();

  @Test
  @DisplayName("空文本返回空列表")
  void shouldReturnEmptyForBlankText() {
    assertThat(chunker.split("", 500, 80)).isEmpty();
    assertThat(chunker.split("   ", 500, 80)).isEmpty();
  }

  @Test
  @DisplayName("短文本形成单个 chunk，保留原文")
  void shouldFormSingleChunkForShortText() {
    String text = "连接池耗尽导致订单服务 502。";
    List<Chunk> chunks = chunker.split(text, 500, 80);
    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0).seq()).isEqualTo(1);
    assertThat(chunks.get(0).content()).isEqualTo(text);
  }

  @Test
  @DisplayName("多段落按段落边界聚合，不跨段硬切")
  void shouldSplitByParagraph() {
    String text = "第一段：连接池配置。maxActive=50。\n\n第二段：监控指标。up=0。\n\n第三段：根因。";
    List<Chunk> chunks = chunker.split(text, 60, 0);
    assertThat(chunks).hasSize(3);
    assertThat(chunks.get(0).content()).contains("第一段");
    assertThat(chunks.get(1).content()).contains("第二段");
    assertThat(chunks).extracting(Chunk::seq).containsExactly(1, 2, 3);
  }

  @Test
  @DisplayName("超长段落按句子边界切分")
  void shouldSplitLongParagraphBySentence() {
    StringBuilder sb = new StringBuilder();
    for (int i = 1; i <= 20; i++) {
      sb.append("句").append(i).append("。");
    }
    String text = sb.toString(); // ~60 字符
    List<Chunk> chunks = chunker.split(text, 50, 0);
    assertThat(chunks.size()).isGreaterThanOrEqualTo(2);
    assertThat(String.join("", chunks.stream().map(Chunk::content).toList())).contains("句1", "句20");
  }

  @Test
  @DisplayName("overlap 使相邻 chunk 在边界重叠")
  void shouldOverlapAdjacentChunks() {
    String text = "0123456789".repeat(20); // 200 字符
    List<Chunk> chunks = chunker.split(text, 50, 10);
    assertThat(chunks.size()).isGreaterThanOrEqualTo(2);
    // 第二个 chunk 的开头应出现在第一个 chunk 的结尾（10 字符重叠）
    assertThat(chunks.get(0).content()).endsWith(chunks.get(1).content().substring(0, 10));
  }

  @Test
  @DisplayName("参数非法抛 IllegalArgumentException")
  void shouldRejectInvalidParameters() {
    assertThatThrownBy(() -> chunker.split("x", 10, 0)) // chunkSize < 50
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> chunker.split("x", 500, 500)) // overlap >= chunkSize
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> chunker.split("x", 500, -1)) // overlap < 0
        .isInstanceOf(IllegalArgumentException.class);
  }
}
