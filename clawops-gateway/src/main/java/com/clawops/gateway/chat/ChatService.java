package com.clawops.gateway.chat;

import com.clawops.harness.loop.AgentLoop;
import com.clawops.harness.loop.LoopResult;
import com.clawops.harness.spi.AgentEventListener;
import com.clawops.harness.spi.TokenUsage;
import com.clawops.harness.spi.ToolCall;
import com.clawops.harness.spi.ToolResult;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 对话服务：在独立线程中运行 AgentLoop，并通过 SSE 推送事件。 */
@Service
public class ChatService {

  private static final Logger LOG = LoggerFactory.getLogger(ChatService.class);
  private static final long SSE_TIMEOUT_MS = 120_000;

  private final AgentLoop agentLoop;
  private final ExecutorService executor;

  public ChatService(AgentLoop agentLoop) {
    this.agentLoop = agentLoop;
    this.executor = Executors.newVirtualThreadPerTaskExecutor();
  }

  public SseEmitter stream(String message) {
    String taskId = UUID.randomUUID().toString();
    SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
    emitter.onCompletion(() -> LOG.debug("SSE completed: {}", taskId));
    emitter.onTimeout(() -> LOG.warn("SSE timeout: {}", taskId));
    emitter.onError(e -> LOG.warn("SSE error: {}", taskId, e));

    executor.submit(
        () -> {
          try {
            AgentEventListener listener = createListener(emitter);
            AgentLoop loop = agentLoop.withListener(listener);
            LoopResult result = loop.run(message);
            emitter.send(SseEventBuilder.done(taskId, result.steps()));
            emitter.complete();
          } catch (Exception e) {
            LOG.error("AgentLoop execution failed: {}", taskId, e);
            safeSend(emitter, SseEventBuilder.error(e.getMessage()));
            emitter.completeWithError(e);
          }
        });

    return emitter;
  }

  private AgentEventListener createListener(SseEmitter emitter) {
    return new AgentEventListener() {
      @Override
      public void onDelta(String text) {
        safeSend(emitter, SseEventBuilder.delta(text));
      }

      @Override
      public void onToolCall(ToolCall call) {
        safeSend(emitter, SseEventBuilder.toolCall(call));
      }

      @Override
      public void onToolResult(String toolCallId, ToolResult result) {
        safeSend(emitter, SseEventBuilder.toolResult(toolCallId, result));
      }

      @Override
      public void onDone(String answer, int steps, TokenUsage usage) {
        // done event is sent by ChatService to include taskId
      }

      @Override
      public void onError(Throwable error) {
        safeSend(emitter, SseEventBuilder.error(error.getMessage()));
      }
    };
  }

  private void safeSend(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
    try {
      emitter.send(event);
    } catch (IOException e) {
      LOG.warn("Failed to send SSE event", e);
    }
  }
}
