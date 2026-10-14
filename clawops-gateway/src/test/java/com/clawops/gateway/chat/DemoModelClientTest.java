package com.clawops.gateway.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.clawops.harness.spi.AgentMessage;
import com.clawops.harness.spi.ModelResponse;
import com.clawops.harness.spi.ToolCall;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DemoModelClient 关键字路由")
class DemoModelClientTest {

  private final DemoModelClient client = new DemoModelClient();

  @Test
  @DisplayName("提到指标时返回 metrics.query 工具调用")
  void shouldReturnMetricsQueryForCpu() {
    ModelResponse response = client.chat(List.of(AgentMessage.user("查询 CPU 指标")), List.of());

    assertThat(response.hasToolCalls()).isTrue();
    ToolCall call = response.toolCalls().get(0);
    assertThat(call.name()).isEqualTo("metrics.query");
    assertThat(call.arguments()).containsKey("promql");
  }

  @Test
  @DisplayName("提到日志错误时返回 log.search 工具调用")
  void shouldReturnLogSearchForError() {
    ModelResponse response = client.chat(List.of(AgentMessage.user("查看 ERROR 日志")), List.of());

    assertThat(response.hasToolCalls()).isTrue();
    ToolCall call = response.toolCalls().get(0);
    assertThat(call.name()).isEqualTo("log.search");
  }

  @Test
  @DisplayName("无匹配关键词时返回引导文本")
  void shouldReturnGuidanceForUnknownInput() {
    ModelResponse response = client.chat(List.of(AgentMessage.user("你好")), List.of());

    assertThat(response.hasToolCalls()).isFalse();
    assertThat(response.text()).contains("指标");
  }
}
