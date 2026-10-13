package com.clawops.harness.fake;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FakeChatModel 行为")
class FakeChatModelTest {

  @Test
  @DisplayName("应返回预设文本回复并附带默认 usage")
  void shouldReturnTextWhenEnqueued() {
    FakeChatModel fake = new FakeChatModel();
    fake.enqueueText("已定位到 inventory-service 连接池耗尽");

    ChatResponse response =
        fake.chat(ChatRequest.builder().messages(UserMessage.from("诊断告警")).build());

    assertThat(response.aiMessage().text()).isEqualTo("已定位到 inventory-service 连接池耗尽");
    assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(2);
    assertThat(response.aiMessage().hasToolExecutionRequests()).isFalse();
  }

  @Test
  @DisplayName("应按顺序弹出多条预设回复并记录全部入参")
  void shouldRecordRequestsInOrder() {
    FakeChatModel fake = new FakeChatModel();
    fake.enqueueText("第一轮");
    fake.enqueueText("第二轮");

    fake.chat(ChatRequest.builder().messages(UserMessage.from("问题一")).build());
    fake.chat(ChatRequest.builder().messages(UserMessage.from("问题二")).build());

    assertThat(fake.requests()).hasSize(2);
    assertThat(fake.requests().get(0).messages()).hasSize(1);
    assertThat(fake.requests().get(1).messages()).hasSize(1);
  }

  @Test
  @DisplayName("应发出工具调用请求且参数为 JSON 字符串")
  void shouldEmitToolCallWithJsonArguments() {
    FakeChatModel fake = new FakeChatModel();
    fake.enqueueToolCall("metrics.query", Map.of("query", "db_pool_active"));

    ChatResponse response =
        fake.chat(ChatRequest.builder().messages(UserMessage.from("查连接池")).build());

    assertThat(response.aiMessage().hasToolExecutionRequests()).isTrue();
    ToolExecutionRequest request = response.aiMessage().toolExecutionRequests().get(0);
    assertThat(request.name()).isEqualTo("metrics.query");
    assertThat(request.arguments()).isEqualTo("{\"query\":\"db_pool_active\"}");
  }

  @Test
  @DisplayName("应支持注入 usage 值（token 计数 / 压缩触发测试）")
  void shouldSupportUsageInjection() {
    FakeChatModel fake = new FakeChatModel();
    fake.setTokenUsage(new TokenUsage(100, 50, 150));
    fake.enqueueText("x");

    ChatResponse response =
        fake.chat(ChatRequest.builder().messages(UserMessage.from("q")).build());

    assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(100);
    assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(50);
    assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(150);
  }

  @Test
  @DisplayName("预设耗尽时应快速失败而非返回空")
  void shouldFailFastWhenQueueEmpty() {
    FakeChatModel fake = new FakeChatModel();
    ChatRequest request = ChatRequest.builder().messages(UserMessage.from("q")).build();

    assertThat(fake.requests()).isEmpty();
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalStateException.class, () -> fake.chat(request));
  }
}
