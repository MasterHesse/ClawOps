package com.clawops.harness.fake;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 可编程假模型（conventions/testing.md §2）：按轮次弹出预设回复，并记录全部入参供断言。
 *
 * <p>单测与集测一律使用本类，禁止真实 LLM API 调用。本类位于 test 源码集（fake 包，被 ArchUnit 排除），随 test-jar 分发给兄弟模块复用。
 *
 * <pre>
 * fake.enqueueText("已定位到 inventory-service 连接池耗尽");
 * fake.enqueueToolCall("metrics.query", Map.of("query", "db_pool_active"));
 * assertThat(fake.requests()).hasSize(2);
 * </pre>
 */
public final class FakeChatModel implements ChatModel {

  /** 单次请求的记录视图：消息列表与请求携带的工具名。 */
  public record RecordedRequest(List<ChatMessage> messages, List<String> toolNames) {

    static RecordedRequest of(ChatRequest request) {
      List<String> tools =
          request.toolSpecifications() == null
              ? List.of()
              : request.toolSpecifications().stream().map(ToolSpecification::name).toList();
      return new RecordedRequest(List.copyOf(request.messages()), tools);
    }
  }

  private sealed interface Scripted permits TextResponse, ToolCallResponse {

    AiMessage toAiMessage();
  }

  private record TextResponse(String text) implements Scripted {

    @Override
    public AiMessage toAiMessage() {
      return new AiMessage(text);
    }
  }

  private record ToolCallResponse(String toolName, Map<String, Object> arguments)
      implements Scripted {

    @Override
    public AiMessage toAiMessage() {
      ToolExecutionRequest executionRequest =
          ToolExecutionRequest.builder()
              .id("fake-tool-call-" + SEQ.incrementAndGet())
              .name(toolName)
              .arguments(JsonSupport.toJson(arguments))
              .build();
      return new AiMessage(List.of(executionRequest));
    }
  }

  private static final AtomicLong SEQ = new AtomicLong();

  private final Deque<Scripted> queue = new ArrayDeque<>();
  private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
  private TokenUsage tokenUsage = new TokenUsage(1, 1, 2);

  /** 下一轮返回纯文本回复。 */
  public void enqueueText(String text) {
    queue.add(new TextResponse(text));
  }

  /** 下一轮返回工具调用请求（参数序列化为 JSON 字符串）。 */
  public void enqueueToolCall(String toolName, Map<String, Object> arguments) {
    queue.add(new ToolCallResponse(toolName, arguments));
  }

  /** 断言用：按调用顺序返回全部入参记录。 */
  public List<RecordedRequest> requests() {
    return List.copyOf(requests);
  }

  /** 注入 usage 值，供 token 计数 / 压缩触发等逻辑测试（规则三）。 */
  public void setTokenUsage(TokenUsage usage) {
    this.tokenUsage = usage;
  }

  @Override
  public ChatResponse doChat(ChatRequest request) {
    requests.add(RecordedRequest.of(request));
    Scripted scripted = queue.poll();
    if (scripted == null) {
      throw new IllegalStateException("FakeChatModel 无剩余预设回复，请先 enqueue");
    }
    ChatResponseMetadata metadata =
        ChatResponseMetadata.builder().modelName("fake-chat-model").tokenUsage(tokenUsage).build();
    return ChatResponse.builder().aiMessage(scripted.toAiMessage()).metadata(metadata).build();
  }
}
