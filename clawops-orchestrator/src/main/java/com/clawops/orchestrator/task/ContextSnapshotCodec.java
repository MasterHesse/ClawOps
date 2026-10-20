package com.clawops.orchestrator.task;

import com.clawops.harness.spi.AgentMessage;
import com.clawops.harness.spi.TokenUsage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/**
 * 上下文快照与 token 用量的 JSON 编解码（落 task 表 jsonb 列）。
 *
 * <p>AgentMessage / TokenUsage 均为 record，Jackson 走全参构造器反序列化。
 */
public class ContextSnapshotCodec {

  private final ObjectMapper mapper;

  public ContextSnapshotCodec(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public String serialize(List<AgentMessage> context) {
    try {
      return mapper.writeValueAsString(context == null ? List.of() : context);
    } catch (Exception e) {
      throw new IllegalStateException("序列化上下文快照失败", e);
    }
  }

  public List<AgentMessage> deserializeContext(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return mapper.readValue(json, new TypeReference<List<AgentMessage>>() {});
    } catch (Exception e) {
      throw new IllegalStateException("反序列化上下文快照失败: " + json, e);
    }
  }

  public String serialize(TokenUsage usage) {
    try {
      return usage == null ? null : mapper.writeValueAsString(usage);
    } catch (Exception e) {
      throw new IllegalStateException("序列化 token 用量失败", e);
    }
  }

  public TokenUsage deserializeToken(String json) {
    if (json == null || json.isBlank()) {
      return TokenUsage.zero();
    }
    try {
      return mapper.readValue(json, TokenUsage.class);
    } catch (Exception e) {
      throw new IllegalStateException("反序列化 token 用量失败: " + json, e);
    }
  }
}
