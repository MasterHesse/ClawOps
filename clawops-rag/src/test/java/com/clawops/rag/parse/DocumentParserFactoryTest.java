package com.clawops.rag.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 文档解析器（md/txt/pdf）测试（conventions/testing.md §3 TDD）。 */
@DisplayName("文档解析器")
class DocumentParserFactoryTest {

  private final DocumentParserFactory factory = new DocumentParserFactory();

  @Test
  @DisplayName("Markdown：去标记保留文本，标题取首个 # 行")
  void shouldParseMarkdown() {
    String md = "# 连接池诊断\n\n正文 **加粗** 与 `代码`。\n- 列表项";
    ParsedDocument pd = factory.parse("diag.md", md.getBytes(StandardCharsets.UTF_8));
    assertThat(pd.title()).isEqualTo("连接池诊断");
    assertThat(pd.text()).contains("连接池诊断", "正文", "加粗", "代码", "列表项");
    assertThat(pd.text()).doesNotContain("#", "**", "`");
  }

  @Test
  @DisplayName("纯文本：原样保留，标题取首行")
  void shouldParsePlainText() {
    String txt = "首行标题\n第二行内容";
    ParsedDocument pd = factory.parse("notes.txt", txt.getBytes(StandardCharsets.UTF_8));
    assertThat(pd.title()).isEqualTo("首行标题");
    assertThat(pd.text()).isEqualTo(txt);
  }

  @Test
  @DisplayName("PDF：空内容抛 DocumentParseException")
  void shouldRejectEmptyPdf() {
    assertThatThrownBy(() -> factory.parse("a.pdf", new byte[0]))
        .isInstanceOf(DocumentParseException.class);
  }

  @Test
  @DisplayName("factory 按扩展名分发到正确解析器")
  void shouldDispatchByExtension() {
    assertThat(factory.forFileName("a.md")).isInstanceOf(MarkdownParser.class);
    assertThat(factory.forFileName("a.TXT")).isInstanceOf(PlainTextParser.class);
    assertThat(factory.forFileName("a.pdf")).isInstanceOf(PdfParser.class);
  }

  @Test
  @DisplayName("未知类型抛 DocumentParseException")
  void shouldRejectUnknownType() {
    assertThatThrownBy(() -> factory.parse("a.docx", new byte[] {1, 2}))
        .isInstanceOf(DocumentParseException.class);
  }
}
