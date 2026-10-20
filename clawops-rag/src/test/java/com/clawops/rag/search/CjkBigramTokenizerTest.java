package com.clawops.rag.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CJK bigram 分词器（conventions/testing.md §3 TDD）。 */
@DisplayName("CjkBigramTokenizer CJK 二元分词")
class CjkBigramTokenizerTest {

  private final CjkBigramTokenizer tokenizer = new CjkBigramTokenizer();

  @Test
  @DisplayName("纯中文：相邻二字滑动组成 bigram，空格连接")
  void shouldTokenizePureChinese() {
    // "连接池耗尽" → 连接 接池 池耗 耗尽
    assertThat(tokenizer.tokenize("连接池耗尽")).isEqualTo("连接 接池 池耗 耗尽");
  }

  @Test
  @DisplayName("中英混合：中文 bigram 与拉丁词原样保留，小写")
  void shouldTokenizeMixed() {
    String result = tokenizer.tokenize("订单 API 超时");
    assertThat(result).contains("订单", "api", "超时");
  }

  @Test
  @DisplayName("纯英文：按空格切分并小写")
  void shouldTokenizeLatin() {
    assertThat(tokenizer.tokenize("Connection Pool Exhausted"))
        .isEqualTo("connection pool exhausted");
  }

  @Test
  @DisplayName("空串返回空")
  void shouldReturnEmptyForBlank() {
    assertThat(tokenizer.tokenize("")).isEmpty();
    assertThat(tokenizer.tokenize("   ")).isEmpty();
  }
}
