package com.clawops.harness.spi;

import java.util.Map;

/**
 * 模型返回的一次工具调用请求。
 *
 * @param id 调用 ID，用于与 ToolResult 关联
 * @param name 工具名
 * @param arguments 参数映射（已解析为 Java 对象）
 */
public record ToolCall(String id, String name, Map<String, Object> arguments) {

  public ToolCall {
    arguments = Map.copyOf(arguments == null ? Map.of() : arguments);
  }
}
