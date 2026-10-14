package com.clawops.gateway.chat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.io.IOException;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@DisplayName("ChatController SSE 对话接口")
class ChatControllerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    ChatService chatService =
        new ChatService(null) {
          @Override
          public SseEmitter stream(String message) {
            return fakeEmitter();
          }
        };
    ChatController controller = new ChatController(chatService);
    mockMvc = standaloneSetup(controller).build();
  }

  @Test
  @DisplayName("POST /api/v1/chat/stream 返回 SSE 事件流")
  void shouldStreamSseEvents() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/chat/stream")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"message\":\"hi\"}"))
            .andExpect(request().asyncStarted())
            .andReturn();

    mockMvc
        .perform(asyncDispatch(result))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("delta")));
  }

  private SseEmitter fakeEmitter() {
    SseEmitter emitter = new SseEmitter();
    Executors.newSingleThreadExecutor()
        .submit(
            () -> {
              try {
                emitter.send(SseEventBuilder.delta("hello"));
                emitter.send(SseEventBuilder.done("task-1", 1));
                emitter.complete();
              } catch (IOException e) {
                emitter.completeWithError(e);
              }
            });
    return emitter;
  }
}
