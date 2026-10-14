package com.clawops.harness.spi;

import java.time.Duration;
import java.util.Map;

/**
 * 工具定义。
 *
 * @param name 工具名（全局唯一）
 * @param description 工具描述，用于 Prompt
 * @param permission 权限等级
 * @param parameterSchema JSON Schema（OpenAPI 风格子集）
 * @param executor 执行器
 * @param timeout 单次调用超时
 */
public record ToolDefinition(
    String name,
    String description,
    Permission permission,
    Map<String, Object> parameterSchema,
    ToolExecutor executor,
    Duration timeout) {

  public ToolDefinition {
    parameterSchema = Map.copyOf(parameterSchema == null ? Map.of() : parameterSchema);
    if (timeout == null) {
      timeout = Duration.ofSeconds(5);
    }
  }
}
